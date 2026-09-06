import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptPath = fileURLToPath(import.meta.url)
const repositoryRoot = path.resolve(path.dirname(scriptPath), '..')
const datasetDirectory = path.join(
  repositoryRoot,
  'src',
  'test',
  'resources',
  'search-evaluation',
  'prizm-v1',
)
const factMatrixPath = path.join(datasetDirectory, 'fact-matrix.json')

const DATASET_ID = 'prizm-career-evidence-synthetic-v1.0'
const EXPECTED_DOCUMENTS = 114
const EXPECTED_QUESTIONS = 300
const COHORTS = ['A', 'B', 'C']
const SPLITS = ['TUNING', 'TEST']
const SHARED_NEGATIVE_SCENARIO_COMPONENTS = new Set([
  'archive',
  'course',
  'dormant',
  'foreign',
  'mock',
  'plan',
  'role',
  'sketch',
])
const MODE = process.argv[2]

if (!['--write', '--check'].includes(MODE) || process.argv.length !== 3) {
  throw new Error('Usage: node scripts/generate-prizm-search-evaluation-dataset.mjs --write|--check')
}

const matrixBytes = await readFile(factMatrixPath)
const matrix = JSON.parse(matrixBytes.toString('utf8'))
validateMatrix(matrix)

const documents = renderDocuments(matrix)
const questions = renderQuestions(matrix)
validateRenderedDataset(documents, questions)

const corpusBytes = utf8(`${JSON.stringify({
  datasetId: matrix.datasetId,
  schemaVersion: matrix.schemaVersion,
  documents,
}, null, 2)}\n`)
const questionsBytes = utf8(`${questions.map((question) => JSON.stringify(question)).join('\n')}\n`)
const readmeBytes = utf8(renderDatasetCard(documents, questions))
const scriptBytes = await readFile(scriptPath)
const manifestBytes = utf8(`${JSON.stringify(renderFreezeManifest({
  matrix,
  documents,
  questions,
  files: {
    'README.md': readmeBytes,
    'fact-matrix.json': matrixBytes,
    'corpus.json': corpusBytes,
    'questions.jsonl': questionsBytes,
    '../../../../../scripts/generate-prizm-search-evaluation-dataset.mjs': scriptBytes,
  },
}), null, 2)}\n`)

const outputs = new Map([
  [path.join(datasetDirectory, 'README.md'), readmeBytes],
  [path.join(datasetDirectory, 'corpus.json'), corpusBytes],
  [path.join(datasetDirectory, 'questions.jsonl'), questionsBytes],
  [path.join(datasetDirectory, 'freeze-manifest.json'), manifestBytes],
])

if (MODE === '--write') {
  for (const [outputPath, bytes] of outputs) {
    await writeFile(outputPath, bytes)
  }
  process.stdout.write(`Generated ${documents.length} documents and ${questions.length} questions.\n`)
} else {
  for (const [outputPath, expected] of outputs) {
    let actual
    try {
      actual = await readFile(outputPath)
    } catch (error) {
      throw new Error(`Missing generated dataset file: ${path.relative(repositoryRoot, outputPath)}`, {
        cause: error,
      })
    }
    if (!actual.equals(expected)) {
      throw new Error(`Generated dataset drift: ${path.relative(repositoryRoot, outputPath)}`)
    }
  }
  process.stdout.write(`Dataset check passed for ${documents.length} documents and ${questions.length} questions.\n`)
}

function validateMatrix(value) {
  assert(value.datasetId === DATASET_ID, 'Unexpected datasetId.')
  assert(value.schemaVersion === 2, 'The PRIZM dataset must use schema version 2.')
  assert(Array.isArray(value.positiveCases), 'positiveCases must be an array.')
  assert(Array.isArray(value.negativeFixtures), 'negativeFixtures must be an array.')
  assert(countReviewedTextItems(value) === 576,
    `Expected 576 authored text items, found ${countReviewedTextItems(value)}.`)

  const seenFacts = new Set()
  const seenProjects = new Set()
  const seenIdentifiers = new Set()
  for (const fact of value.positiveCases) {
    const cohort = cohortOf(fact)
    assert(COHORTS.includes(cohort), `Unknown cohort for ${fact.factId}.`)
    assert(SPLITS.includes(fact.split), `Unknown split for ${fact.factId}.`)
    assert(!seenFacts.has(fact.factId), `Duplicate factId: ${fact.factId}`)
    assert(!seenProjects.has(normalize(fact.project)), `Duplicate project: ${fact.project}`)
    assert(!seenIdentifiers.has(normalize(fact.identifier)), `Duplicate identifier: ${fact.identifier}`)
    seenFacts.add(fact.factId)
    seenProjects.add(normalize(fact.project))
    seenIdentifiers.add(normalize(fact.identifier))
    for (const field of [
      'project',
      'identifier',
      'title',
      'documentType',
      'fileType',
      'boundary',
      'directCategory',
      'anchor',
      'summaryAnchor',
      'directQuery',
      'paraphraseQuery',
    ]) {
      assert(typeof fact[field] === 'string' && fact[field].trim(), `${fact.factId} is missing ${field}.`)
    }
    assert(typeof fact.includeExact === 'boolean', `${fact.factId} is missing includeExact.`)
    assert(
      fact.includeExact === (typeof fact.exactQuery === 'string' && fact.exactQuery.trim().length > 0),
      `${fact.factId} exact query contract is inconsistent.`,
    )
    assert(!normalize(fact.paraphraseQuery).includes(normalize(fact.project)),
      `${fact.factId} paraphrase query must not reveal the project name.`)
    assert(!normalize(fact.paraphraseQuery).includes(normalize(fact.identifier)),
      `${fact.factId} paraphrase query must not reveal the identifier.`)
    assert(!/[A-Za-z0-9]/u.test(fact.paraphraseQuery),
      `${fact.factId} paraphrase query must not contain ASCII technology names or exact numbers.`)
    if (fact.boundary === 'OVERLAP') {
      assert(fact.anchor.length <= 110, `${fact.factId} overlap anchor is longer than 110 characters.`)
    }
  }

  const seenFixtures = new Set()
  for (const fixture of value.negativeFixtures) {
    const cohort = cohortOf(fixture)
    assert(COHORTS.includes(cohort), `Unknown cohort for ${fixture.fixtureId}.`)
    assert(SPLITS.includes(fixture.split), `Unknown split for ${fixture.fixtureId}.`)
    assert(!seenFixtures.has(fixture.fixtureId), `Duplicate negative fixture: ${fixture.fixtureId}`)
    seenFixtures.add(fixture.fixtureId)
    assert(Array.isArray(fixture.queries) && fixture.queries.length > 0, `${fixture.fixtureId} needs queries.`)
    for (const query of fixture.queries) {
      assert(typeof query.query === 'string' && query.query.trim(), `${fixture.fixtureId} has an empty query.`)
      assert(typeof query.category === 'string' && query.category.trim(), `${fixture.fixtureId} has no category.`)
    }
  }

  const authoredValues = {
    project: [
      ...value.positiveCases.map((fact) => ({ cohort: cohortOf(fact), value: fact.project })),
      ...value.negativeFixtures.map((fixture) => ({
        cohort: cohortOf(fixture),
        value: fixture.title.replace(/^합성\s+/u, '').split(' ')[0],
      })),
    ],
    identifier: value.positiveCases.map((fact) => ({ cohort: cohortOf(fact), value: fact.identifier })),
    sourceFact: [
      ...value.positiveCases.map((fact) => ({ cohort: cohortOf(fact), value: fact.factId })),
      ...value.negativeFixtures.map((fixture) => ({
        cohort: cohortOf(fixture),
        value: `${fixture.fixtureId}-source-fact`,
      })),
    ],
    anchor: [
      ...value.positiveCases.flatMap((fact) => [fact.anchor, fact.summaryAnchor]
        .map((anchor) => ({ cohort: cohortOf(fact), value: anchor }))),
      ...value.negativeFixtures.map((fixture) => ({
        cohort: cohortOf(fixture),
        value: fixture.anchor,
      })),
    ],
    query: [
      ...value.positiveCases.flatMap((fact) => [
        fact.directQuery,
        fact.paraphraseQuery,
        fact.exactQuery,
      ].filter(Boolean).map((query) => ({ cohort: cohortOf(fact), value: query }))),
      ...value.negativeFixtures.flatMap((fixture) => fixture.queries.map((query) => ({
        cohort: cohortOf(fixture),
        value: query.query,
      }))),
    ],
  }
  for (const [label, records] of Object.entries(authoredValues)) {
    assertNoCrossCohortDuplicate(records, label)
  }
  assertNoCrossCohortNameOnlySentenceCopies([
    ...value.positiveCases.flatMap((fact) => [
      fact.anchor,
      fact.summaryAnchor,
      fact.directQuery,
      fact.paraphraseQuery,
      fact.exactQuery,
    ].filter(Boolean).map((sentence) => ({
      cohort: cohortOf(fact),
      project: fact.project,
      identifier: fact.identifier,
      sentence,
    }))),
    ...value.negativeFixtures.flatMap((fixture) => {
      const project = fixture.title.replace(/^합성\s+/u, '').split(' ')[0]
      return [fixture.anchor, ...fixture.queries.map((query) => query.query)].map((sentence) => ({
        cohort: cohortOf(fixture),
        project,
        identifier: null,
        sentence,
      }))
    }),
  ])
  assertNoCrossCohortProjectComponentReuse([
    ...value.positiveCases.map((fact) => ({ cohort: cohortOf(fact), project: fact.project })),
    ...value.negativeFixtures.map((fixture) => ({
      cohort: cohortOf(fixture),
      project: fixture.title.replace(/^합성\s+/u, '').split(' ')[0],
    })),
  ])

  for (const cohort of COHORTS) {
    assertCount(value.positiveCases, { cohort, split: 'TUNING' }, 12)
    assertCount(value.positiveCases, { cohort, split: 'TEST' }, 8)
    assertCount(value.negativeFixtures, { cohort, split: 'TUNING' }, 8)
    assertCount(value.negativeFixtures, { cohort, split: 'TEST' }, 8)
    assertPositiveShape(value.positiveCases, cohort, 'TUNING', 6)
    assertPositiveShape(value.positiveCases, cohort, 'TEST', 4)
    assertNegativeQueryCount(value.negativeFixtures, cohort, 'TUNING', 30)
    assertNegativeQueryCount(value.negativeFixtures, cohort, 'TEST', 20)
  }
}

function assertNoCrossCohortNameOnlySentenceCopies(records) {
  const seen = new Map()
  for (const record of records) {
    let skeleton = normalize(record.sentence).replaceAll(normalize(record.project), '<project>')
    if (record.identifier) {
      skeleton = skeleton.replaceAll(normalize(record.identifier), '<identifier>')
    }
    skeleton = skeleton
      .replace(/\b[a-z]{2,}(?:-[a-z0-9]+)+\b/gu, '<identifier>')
      .replace(/\s+/gu, ' ')
      .trim()
    const previous = seen.get(skeleton)
    assert(!previous || previous.cohort === record.cohort,
      `Cross-cohort name-only sentence copy: ${previous?.sentence} / ${record.sentence}`)
    seen.set(skeleton, record)
  }
}

function assertNoCrossCohortDuplicate(records, label) {
  const seen = new Map()
  for (const record of records) {
    const key = normalize(record.value)
    const previous = seen.get(key)
    assert(!previous || previous.cohort === record.cohort,
      `Cross-cohort ${label} duplicate: ${previous?.value} / ${record.value}`)
    seen.set(key, record)
  }
}

function assertNoCrossCohortProjectComponentReuse(projects) {
  const seen = new Map()
  for (const record of projects) {
    const projectWithoutCohortPrefix = record.project.replace(/^[BC][XY]-/u, '')
    const components = projectWithoutCohortPrefix
      .match(/[A-Z]+(?=[A-Z][a-z]|$)|[A-Z]?[a-z]+|\d+/gu)
      ?.map((component) => component.toLocaleLowerCase('en-US')) ?? []
    assert(components.length >= 2, `${record.project} needs at least two name components.`)
    for (const component of components) {
      if (SHARED_NEGATIVE_SCENARIO_COMPONENTS.has(component)) {
        continue
      }
      const previous = seen.get(component)
      assert(!previous || previous.cohort === record.cohort,
        `Cross-cohort project-name component duplicate: ${previous?.project} / ${record.project}`)
      seen.set(component, record)
    }
  }
}

function assertPositiveShape(facts, cohort, split, expectedExact) {
  const selected = facts.filter((fact) => cohortOf(fact) === cohort && fact.split === split)
  assert(selected.filter((fact) => fact.fileType === 'TXT').length === selected.length / 2,
    `${cohort}/${split} positive TXT/PDF balance is wrong.`)
  assert(selected.filter((fact) => fact.fileType === 'PDF').length === selected.length / 2,
    `${cohort}/${split} positive TXT/PDF balance is wrong.`)
  assert(selected.filter((fact) => fact.includeExact).length === expectedExact,
    `${cohort}/${split} exact-query count is wrong.`)
  assert(selected.filter((fact) => fact.includeExact && fact.fileType === 'TXT').length === expectedExact / 2,
    `${cohort}/${split} exact TXT/PDF balance is wrong.`)
  assert(selected.filter((fact) => fact.includeExact && fact.fileType === 'PDF').length === expectedExact / 2,
    `${cohort}/${split} exact TXT/PDF balance is wrong.`)
}

function assertNegativeQueryCount(fixtures, cohort, split, expected) {
  const count = fixtures
    .filter((fixture) => cohortOf(fixture) === cohort && fixture.split === split)
    .reduce((total, fixture) => total + fixture.queries.length, 0)
  assert(count === expected, `${cohort}/${split} negative query count must be ${expected}, found ${count}.`)
}

function assertCount(records, expected, count) {
  const actual = records.filter((record) =>
    cohortOf(record) === expected.cohort && record.split === expected.split).length
  assert(actual === count,
    `${expected.cohort}/${expected.split} count must be ${count}, found ${actual}.`)
}

function renderDocuments(value) {
  const detailDocuments = value.positiveCases.map((fact, index) => renderDetailDocument(fact, index))
  const summaryDocuments = COHORTS.flatMap((cohort) => SPLITS.map((split) =>
    renderSummaryDocument(
      cohort,
      split,
      value.positiveCases.filter((fact) => cohortOf(fact) === cohort && fact.split === split),
    )))
  const negativeDocuments = value.negativeFixtures.map((fixture, index) =>
    renderNegativeDocument(fixture, index))
  return [...detailDocuments, ...summaryDocuments, ...negativeDocuments]
}

function renderDetailDocument(fact, index) {
  const fixtureId = detailFixtureId(fact)
  const pageCount = fact.fileType === 'PDF' ? 3 : 1
  const goldPage = fact.fileType === 'PDF' ? 2 + (index % 2) : null
  const pages = []
  for (let pageNumber = 1; pageNumber <= pageCount; pageNumber += 1) {
    const hasAnchor = fact.fileType === 'TXT' || pageNumber === goldPage
    pages.push({
      pageNumber,
      text: renderLongPage(fact, hasAnchor ? fact.anchor : null, pageNumber),
    })
  }
  return {
    fixtureId,
    title: fact.title,
    documentType: fact.documentType,
    fileType: fact.fileType,
    pages,
    evidenceAnchors: [{
      fixtureEvidenceId: detailEvidenceId(fact),
      anchorText: fact.anchor,
      sourceFactId: fact.factId,
    }],
    split: fact.split,
  }
}

function renderSummaryDocument(cohort, split, facts) {
  const fixtureId = summaryFixtureId(cohort, split)
  const records = facts.map((fact, index) => [
    `${fact.project} 요약 ${index + 1}`,
    fact.summaryAnchor,
    `${fact.project} 요약에는 ${fact.identifier} 세부 수치를 넣지 않고 수행 범위만 짧게 남겼다.`,
  ].join('\n')).join('\n\n')
  const seed = {
    cohort,
    project: `${cohort}-${split}-CareerSummary`,
    identifier: `${cohort}-${split}-SUMMARY`,
    title: `${cohort} ${split} 합성 경력 요약`,
    documentType: 'RESUME',
    split,
    boundary: 'SECTION',
  }
  const text = `${records}\n\n${buildContext(seed, 2400, '요약')}`
  return {
    fixtureId,
    title: seed.title,
    documentType: 'RESUME',
    fileType: 'TXT',
    pages: [{ pageNumber: 1, text }],
    evidenceAnchors: facts.map((fact) => ({
      fixtureEvidenceId: summaryEvidenceId(fact),
      anchorText: fact.summaryAnchor,
      sourceFactId: fact.factId,
    })),
    split,
  }
}

function renderNegativeDocument(fixture, index) {
  const record = {
    ...fixture,
    project: fixture.title.replace(/^합성\s+/, '').split(' ')[0],
    identifier: fixture.fixtureId.toUpperCase(),
    boundary: 'STANDARD',
  }
  const pageCount = fixture.fileType === 'PDF' ? 3 : 1
  const anchorPage = fixture.fileType === 'PDF' ? 2 + (index % 2) : 1
  const pages = []
  for (let pageNumber = 1; pageNumber <= pageCount; pageNumber += 1) {
    pages.push({
      pageNumber,
      text: renderLongPage(record, pageNumber === anchorPage ? fixture.anchor : null, pageNumber),
    })
  }
  return {
    fixtureId: fixture.fixtureId,
    title: fixture.title,
    documentType: fixture.documentType,
    fileType: fixture.fileType,
    pages,
    evidenceAnchors: [{
      fixtureEvidenceId: negativeEvidenceId(fixture),
      anchorText: fixture.anchor,
      sourceFactId: `${fixture.fixtureId}-source-fact`,
    }],
    split: fixture.split,
  }
}

function renderLongPage(record, anchor, pageNumber) {
  const heading = `${record.project} ${pageNumber}쪽 합성 기록\n${record.title}\n`
  if (!anchor) {
    return `${heading}${buildContext(record, 1120, `${pageNumber}쪽 배경`)}`
  }

  const overlap = record.boundary === 'OVERLAP'
  const desiredAnchorStart = overlap ? 690 : 900
  const prefixTarget = desiredAnchorStart - heading.length - 1
  assert(prefixTarget > 0, `${record.identifier} heading leaves no room for the anchor prefix.`)
  const prefix = buildContext(record, prefixTarget, `${pageNumber}쪽 앞부분`, prefixTarget)
  const suffix = buildContext(record, 1180, `${pageNumber}쪽 뒷부분`)
  const text = `${heading}${prefix}\n${anchor}\n${suffix}`
  if (overlap) {
    const anchorStart = text.indexOf(anchor)
    assert(anchorStart === desiredAnchorStart,
      `${record.identifier} overlap anchor must start at ${desiredAnchorStart}, found ${anchorStart}.`)
    const offsetInFirstChunk = anchorStart % 680
    assert(anchorStart >= 680, `${record.identifier} overlap anchor starts too early.`)
    assert(offsetInFirstChunk + anchor.length <= 800,
      `${record.identifier} overlap anchor is not fully shared by two 800/120 chunks.`)
  }
  return text
}

function buildContext(record, minimumLength, salt, exactLength = null) {
  const templates = contextTemplates(record)
  let text = ''
  let index = 0
  while (text.length < minimumLength) {
    const sentence = templates[index % templates.length](index + 1, salt)
    if (exactLength !== null && text.length + sentence.length + 1 > exactLength) {
      break
    }
    text += `${sentence} `
    index += 1
  }
  if (exactLength !== null && text.length < exactLength) {
    const pad = `${record.project} ${salt} 경계 점검 `
    while (text.length + pad.length <= exactLength) {
      text += pad
    }
    text += ' '.repeat(exactLength - text.length)
  }
  if (exactLength !== null) {
    assert(text.length === exactLength,
      `${record.identifier} context length must be ${exactLength}, found ${text.length}.`)
    return text
  }
  return text.trimEnd()
}

function contextTemplates(record) {
  const cohort = cohortOf(record)
  const splitLabel = record.split === 'TUNING' ? '조정용' : '최종 확인용'
  const styles = {
    A: [
      (n, salt) => `${record.project} ${salt} 기록 ${n}에는 ${splitLabel} 합성 사례의 작업 조건과 중단 조건을 나눠 적었다.`,
      (n) => `${record.project} 검토 기록 ${n}에는 확인된 결과와 다음에 확인할 항목을 구분해 정리했다.`,
      (n) => `${record.identifier} 보조 기록 ${n}에는 처리 순서와 예외 경로를 이해하는 데 필요한 배경만 담았다.`,
      (n) => `${record.project} 회고 ${n}에 적힌 사례는 평가를 위해 만든 허구이며 실제 회사나 사용자의 경력이 아니다.`,
      (n) => `${record.project} 점검 ${n}에서는 맡은 범위와 검토한 범위를 따로 기록했다.`,
      (n) => `${record.identifier} 관련 기술의 일반 설명 ${n}에는 확인된 성과 근거와 구분해 배경만 남겼다.`,
    ],
    B: [
      (n, salt) => `${record.project}의 ${salt} 메모 ${n}에는 합성 작업의 확인 순서와 중단 기준이 적혀 있다.`,
      (n) => `${record.project} 검토 항목 ${n}에는 확인한 사실, 관찰한 내용, 보류한 판단으로 나눠 작성했다.`,
      (n) => `${record.identifier} 주변 설명 ${n}에는 결과 수치를 새로 보태지 않고 판단 배경만 남겼다.`,
      (n) => `${record.project} 기록 ${n}에는 평가를 위해 만든 허구이며 실재 인물이나 조직의 이력을 재현하지 않는다고 밝혔다.`,
      (n) => `${record.project} 작업 기록 ${n}에서는 정상 흐름과 실패 뒤 처리 순서를 따로 확인했다.`,
      (n) => `${record.identifier} 관련 참고 사항 ${n}에는 일반적인 설명을 직접 확인한 근거와 구분해 적었다.`,
    ],
    C: [
      (n, salt) => `${record.project} ${salt} 항목 ${n}에는 허구 작업의 입력, 처리, 확인 단계를 차례로 적었다.`,
      (n) => `${record.project} 검토표 ${n}에는 성공 조건과 되돌림 조건을 서로 다른 칸에 정리했다.`,
      (n) => `${record.identifier} 배경 기록 ${n}에는 원문 근거의 뜻을 바꾸지 않는 범위에서 주변 상황을 설명했다.`,
      (n) => `${record.project} 사례 ${n}에는 공개된 제3자 문서나 실제 경력 자료를 바탕으로 쓰지 않았다고 밝혔다.`,
      (n) => `${record.project} 확인 기록 ${n}에는 재현 절차와 판정 기준을 먼저 맞춘 뒤 작성한 내용을 담았다.`,
      (n) => `${record.identifier} 관련 참고 내용 ${n}에는 직접 수행한 근거와 일반적인 기술 설명을 구분해 적었다.`,
    ],
  }
  return styles[cohort]
}

function renderQuestions(value) {
  const positiveQuestions = value.positiveCases.flatMap((fact, index) => {
    const detailId = detailFixtureId(fact)
    const summaryId = summaryFixtureId(cohortOf(fact), fact.split)
    const evidenceGroupId = `${fact.factId}-evidence-group`
    const goldPage = fact.fileType === 'PDF' ? 2 + (index % 2) : null
    const common = {
      noEvidence: false,
      split: fact.split,
      fixtureIds: [detailId, summaryId],
      questionGroupId: `${fact.factId}-question-group`,
      ownerScenario: 'PRIMARY_OWNER',
      versionScenario: 'ACTIVE',
      goldPage,
    }
    const gradedEvidence = [
      { fixtureEvidenceId: detailEvidenceId(fact), relevance: 2, evidenceGroupId },
      { fixtureEvidenceId: summaryEvidenceId(fact), relevance: 1, evidenceGroupId },
    ]
    const result = [
      {
        questionId: `${fact.factId}-direct`,
        query: fact.directQuery,
        expectedEvidence: gradedEvidence,
        ...common,
        category: fact.directCategory,
      },
      {
        questionId: `${fact.factId}-paraphrase`,
        query: fact.paraphraseQuery,
        expectedEvidence: gradedEvidence,
        ...common,
        category: 'PARAPHRASE',
      },
    ]
    if (fact.includeExact) {
      result.push({
        questionId: `${fact.factId}-exact`,
        query: fact.exactQuery,
        expectedEvidence: [
          { fixtureEvidenceId: detailEvidenceId(fact), relevance: 2, evidenceGroupId },
          { fixtureEvidenceId: summaryEvidenceId(fact), relevance: 0, evidenceGroupId },
        ],
        ...common,
        category: fact.fileType === 'PDF' ? 'PDF_EVIDENCE' : 'EXACT_VALUE',
      })
    }
    return result
  })

  const negativeQuestions = value.negativeFixtures.flatMap((fixture) => fixture.queries.map((query, index) => ({
    questionId: `${fixture.fixtureId}-negative-${index + 1}`,
    query: query.query,
    expectedEvidence: [{
      fixtureEvidenceId: negativeEvidenceId(fixture),
      relevance: 0,
      evidenceGroupId: `${fixture.fixtureId}-negative-group`,
    }],
    noEvidence: true,
    split: fixture.split,
    category: query.category,
    fixtureIds: [fixture.fixtureId],
    questionGroupId: `${fixture.fixtureId}-question-group`,
    ownerScenario: fixture.ownerScenario,
    versionScenario: fixture.versionScenario,
    goldPage: null,
  })))
  return [...positiveQuestions, ...negativeQuestions]
}

function validateRenderedDataset(documents, questions) {
  assert(documents.length === EXPECTED_DOCUMENTS,
    `Expected ${EXPECTED_DOCUMENTS} documents, found ${documents.length}.`)
  assert(questions.length === EXPECTED_QUESTIONS,
    `Expected ${EXPECTED_QUESTIONS} questions, found ${questions.length}.`)
  assertUnique(documents.map((document) => document.fixtureId), 'fixtureId')
  assertUnique(questions.map((question) => question.questionId), 'questionId')
  assertUnique(questions.map((question) => normalize(question.query)), 'normalized query')

  const expected = {
    TUNING: { total: 180, positive: 90, negative: 90 },
    TEST: { total: 120, positive: 60, negative: 60 },
  }
  for (const split of SPLITS) {
    const selected = questions.filter((question) => question.split === split)
    assert(selected.length === expected[split].total, `${split} total question count is wrong.`)
    assert(selected.filter((question) => !question.noEvidence).length === expected[split].positive,
      `${split} positive question count is wrong.`)
    assert(selected.filter((question) => question.noEvidence).length === expected[split].negative,
      `${split} no-evidence question count is wrong.`)
  }

  const corpusText = documents.flatMap((document) => document.pages.map((page) => normalize(page.text)))
  const rawPages = documents.flatMap((document) => document.pages.map((page) => page.text))
  assert(rawPages.every((text) => !/(?:before|after|page)-\d+|·{3,}/u.test(text)),
    'Generated corpus must not expose context-control salts or repeated padding marks.')
  assert(rawPages.every((text) => !/일반 설명 \d+은/u.test(text)),
    'Generated corpus must not attach a fixed subject particle to numbered context labels.')
  const summaryStyleMarkers = {
    A: '작업 조건과 중단 조건을 나눠 적었다',
    B: '합성 작업의 확인 순서와 중단 기준이 적혀 있다',
    C: '허구 작업의 입력, 처리, 확인 단계를 차례로 적었다',
  }
  for (const cohort of COHORTS) {
    for (const split of SPLITS) {
      const summary = documents.find((document) =>
        document.fixtureId === summaryFixtureId(cohort, split))
      assert(summary?.pages[0]?.text.includes(summaryStyleMarkers[cohort]),
        `${cohort}/${split} summary must use its own cohort context style.`)
    }
  }
  for (const question of questions) {
    const normalizedQuery = normalize(question.query)
    assert(!corpusText.some((text) => text.includes(normalizedQuery)),
      `A full query appears verbatim in the corpus: ${question.questionId}`)
  }
  const paraphrases = questions.filter((question) => question.category === 'PARAPHRASE')
  assert(paraphrases.length === 60, `Expected 60 paraphrase questions, found ${paraphrases.length}.`)
  assert(paraphrases.every((question) => !/[A-Za-z0-9]/u.test(question.query)),
    'Paraphrase questions must remain free of ASCII technology names and exact numbers.')
}

function renderDatasetCard(documents, questions) {
  const categoryCounts = countBy(questions, (question) => question.category)
  return `# PRIZM Career Evidence Synthetic Dataset v1.0

이 디렉터리는 PRIZM 검색 방식을 비교하려고 만든 완전 합성 페이지 텍스트 벤치마크다.

## 구성

- 엔터티·식별자·source fact가 겹치지 않는 A/B/C 코호트
- 합성 문서 ${documents.length}개
- 질문 ${questions.length}개: TUNING 180, 동결 TEST 120
- 근거 있음 150개, 근거 없음 150개
- 프로젝트명·식별자·ASCII 기술명·수치를 뺀 한국어 PARAPHRASE 질문 60개
- 스키마 버전 2
- 운영 청킹 기준: 800자, 겹침 120자
- 질문의 정답 표시는 \`questions.jsonl\` 한 곳에만 둔다.

평가 범주별 수량은 \`freeze-manifest.json\`에 기록한다. 현재 범주는 ${Object.entries(categoryCounts)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([category, count]) => `${category} ${count}`)
    .join(', ')}다.

## 합성 벤치마크의 출처와 작성 범위

모든 인물, 조직, 고용주, 프로젝트, 사건, 날짜, 성과, 수치, 식별자와 경력 주장은 허구다.
PRIZM 사용자 업로드, 실제 이력서·포트폴리오·채용공고, 데이터베이스 덤프, 개인 통신은
사용하지 않았다. ESCO, O*NET, SkillSpan을 비롯한 제3자 데이터셋 문구를 복사·번역·각색하지
않았다. 실제 기술명은 명목상 참조일 뿐 제휴, 후원, 보증을 뜻하지 않는다.

Codex는 허구 사실 행렬과 결정적 템플릿 작성을 도왔다. 실제 사용자 문서나 이름이 있는
외부 데이터셋을 입력으로 주지 않았다. 모델 학습 데이터의 출처는 확인할 수 없으므로 잠재적
학습 데이터 중복이 전혀 없다고 보증하지 않는다.

사실 행렬의 사용자 대면 한국어 문장은 \`humanize-korean\` 기본 강도로 교정·윤문했다.
고유명사, 수치, 날짜, 단위, 식별자, 전문 용어, 부정 범위와 qrel은 보호 요소로 고정했다.
결정적으로 생성되는 문맥 문장은 별도의 정적 검사를 거쳤다. 문장을 자연스럽게 다듬으려고
새 경력 사실이나 성과를 추가하지 않았다.

## 파일

- \`fact-matrix.json\`: 허구 사실과 질문의 작성 원본
- \`corpus.json\`: 기존 PRIZM 평가 로더가 읽는 문서와 페이지 텍스트
- \`questions.jsonl\`: 질문, 관련도, 분할, 소유자·버전 시나리오와 PDF 정답 페이지
- \`freeze-manifest.json\`: 출처, 수량과 SHA-256

\`fact-matrix.json\`, README와 manifest는 검색 말뭉치에 넣지 않는다. 평가 실행기는
\`corpus.json\`과 \`questions.jsonl\`만 읽는다.

## 사용 범위와 한계

이 자료는 검색 후보 회수, 순위, 근거 위치, 소유자·버전 경계와 근거 없음 응답을 평가하는 용도다.
채용, 사람 순위화 또는 고용 의사결정에 사용하지 않는다.

PDF fixture는 실제 PDF 파일이 아니라 추출 결과를 흉내 낸 페이지별 텍스트다. PDF 생성,
파서, 글꼴, 메타데이터, 업로드 제한과 실제 v0→v1 활성 전환은 검증하지 않는다. 스키마 v2는
여러 절을 모두 만족해야 하는 AND 정답, 정확한 문자 범위와 여러 정답 페이지를 표현하지 못한다.
운영 청킹 경계 재현을 위해 일부 생성 문맥 끝의 남는 길이는 공백으로 채운다.

현재 평가용 PostgreSQL FTS는 자연어 질문 전체를 \`simple\` 구성의 AND 조건으로 바꾼다.
한국어 조사와 질문 종결어까지 모두 일치해야 하므로 이 데이터셋과의 실행 적합성은 검증되지
않았다. Hybrid/RRF 비교 전에 FTS 질의 구성 방식을 별도로 사전 등록하고 후보 회수가 실제로
발생하는지 확인해야 한다. 이번 생성 단계에서 PostgreSQL 검색은 실행하지 않았다.

같은 사실에서 만든 직접·바꿔 묻기·정확값 질문을 서로 독립된 표본으로 과장하지 않는다. 결과를
비교할 때 질문 수뿐 아니라 사실·질문 그룹 수와 정확값 오류 수도 함께 보고한다.
일부 B/C 사실은 같은 검색 위험을 다른 기술과 맥락으로 바꾼 평행 시나리오군이다. 세 코호트를
통계적으로 독립한 표본이라고 가정하지 않고, 전체 결과와 함께 코호트별·평행 시나리오군별 결과를
확인한다.

## 생성과 확인

\`\`\`powershell
node scripts/generate-prizm-search-evaluation-dataset.mjs --check
\`\`\`

TEST 검색 평가는 검색 방식을 사전 등록한 뒤 명시적 허용 플래그를 켜고 한 번만 실행한다.
데이터셋을 만드는 이번 단계에서는 TEST 검색 평가를 실행하지 않았다.
`
}

function renderFreezeManifest({ matrix, documents, questions, files }) {
  const categories = countBy(questions, (question) => question.category)
  const splits = Object.fromEntries(SPLITS.map((split) => {
    const selected = questions.filter((question) => question.split === split)
    return [split, {
      documents: documents.filter((document) => document.split === split).length,
      questions: selected.length,
      evidenceQuestions: selected.filter((question) => !question.noEvidence).length,
      noEvidenceQuestions: selected.filter((question) => question.noEvidence).length,
    }]
  }))
  return {
    datasetId: matrix.datasetId,
    schemaVersion: matrix.schemaVersion,
    frozenAtSourceRevision: matrix.sourceRevision,
    hashAlgorithm: 'SHA-256',
    lineEnding: 'LF',
    frozenBeforeSearch: true,
    searchExecutedAtFreeze: false,
    immutableTestSplit: true,
    counts: {
      cohorts: COHORTS.length,
      documents: documents.length,
      questions: questions.length,
      evidenceQuestions: questions.filter((question) => !question.noEvidence).length,
      noEvidenceQuestions: questions.filter((question) => question.noEvidence).length,
      projectIdentifierAsciiDigitBlindParaphraseQuestions:
        questions.filter((question) => question.category === 'PARAPHRASE').length,
      splits,
      categories,
    },
    chunkingContract: {
      profile: 'production',
      maxChunkLength: 800,
      overlap: 120,
    },
    provenance: {
      syntheticOnly: true,
      personalDataIncluded: false,
      thirdPartyDatasetTextIncluded: false,
      realUserDocumentsUsed: false,
      sourceKind: 'PRIZM_AUTHORED_FICTIONAL_FACT_MATRIX',
      generationMethod: 'DETERMINISTIC_TEMPLATE',
      authoringAssistance: 'OpenAI Codex; exact service model metadata is not asserted',
      latentTrainingOverlapGuaranteedAbsent: false,
      license: 'Apache-2.0',
      thirdPartyNotices: [],
    },
    humanization: {
      skill: 'humanize-korean',
      mode: '사실 행렬 교정·문장 다듬기·AI 문체 신호 점검',
      intensity: '기본',
      protectedElements: [
        'project names',
        'identifiers',
        'numbers',
        'dates',
        'units',
        'technology terms',
        'negation scope',
        'qrels',
      ],
      reviewScope: 'AUTHORED_FACT_MATRIX_TEXT_ONLY',
      reviewedTextItems: countReviewedTextItems(matrix),
      protectedElementsBefore: 619,
      protectedElementsAfter: 619,
      addedCareerFacts: 0,
      newKnowledgeClaims: 0,
      protectedElementChanges: 0,
      auditGate: 'PASS',
      auditResultBasis: 'LOCAL_AUTHORING_AUDIT',
      auditInputArtifactsIncluded: false,
      deterministicContextTemplatesReviewedSeparately: true,
    },
    files: Object.fromEntries(Object.entries(files).map(([file, bytes]) => [file, {
      bytes: bytes.length,
      sha256: sha256(bytes),
    }])),
  }
}

function detailFixtureId(fact) {
  return `${fact.factId}-detail`
}

function summaryFixtureId(cohort, split) {
  return `${split.toLowerCase()}-${cohort.toLowerCase()}-career-summary`
}

function detailEvidenceId(fact) {
  return `${fact.factId}-detail-evidence`
}

function summaryEvidenceId(fact) {
  return `${fact.factId}-summary-evidence`
}

function negativeEvidenceId(fixture) {
  return `${fixture.fixtureId}-negative-evidence`
}

function cohortOf(record) {
  return record.cohort ?? 'A'
}

function countBy(values, key) {
  return Object.fromEntries([...values.reduce((counts, value) => {
    const name = key(value)
    counts.set(name, (counts.get(name) ?? 0) + 1)
    return counts
  }, new Map()).entries()].sort(([left], [right]) => left.localeCompare(right)))
}

function countReviewedTextItems(matrix) {
  const positiveFields = [
    'title',
    'anchor',
    'summaryAnchor',
    'directQuery',
    'paraphraseQuery',
    'exactQuery',
  ]
  const positive = matrix.positiveCases.reduce((count, fact) => count
    + positiveFields.filter((field) => typeof fact[field] === 'string' && fact[field].trim()).length, 0)
  const negative = matrix.negativeFixtures.reduce((count, fixture) =>
    count + 2 + fixture.queries.length, 0)
  return positive + negative
}

function normalize(value) {
  return value.normalize('NFKC').toLocaleLowerCase('ko-KR').replace(/\s+/gu, ' ').trim()
}

function assertUnique(values, label) {
  assert(new Set(values).size === values.length, `Duplicate ${label}.`)
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex')
}

function utf8(value) {
  return Buffer.from(value, 'utf8')
}
