import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const PREVIOUS_VERSION = 'search-v3-typed-constraints-stress-1.0.0'
const DATASET_VERSION = 'search-v3-typed-constraints-stress-1.0.1'
const PREVIOUS_SHA256 = '693331c20cd483a8e90696be8e8a39e845475d4e330505c45804b91b80614aae'
const SEALED_FINAL_SHA256 = 'e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383'
const FROZEN_AT = '2026-08-31T19:00:00+09:00'
const MATERIALIZATION_BASE_REVISION = '4bbbc5de040aa3c84fcb9869ece2fce85d983c0c'
const GENERATOR_REVISION = 'prz028-typed-stress-correction-v1'
const PREVIOUS_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.0',
)
const OUTPUT_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.1',
)
const SEALED_MANIFEST = path.resolve(
  'src/test/resources/search-v3-evaluation/sealed-final/manifest.json',
)

const QUERY_TEXT = new Map([
  ['SV3-U32-Q01', 'Did the approved flow reach a task completion rate of at least 80%?'],
  ['SV3-U32-Q02', 'Did the approved flow reach a task completion rate of at least 90%?'],
  ['SV3-U32-Q03', 'Was the approved service launch date after 2025-06-30?'],
  ['SV3-U32-Q04', 'Was the approved service launch date before 2025-01-01?'],
  ['SV3-U35-Q01', '정책 조사 기간이 2024-03-01부터 2025-02-28까지였나요?'],
  ['SV3-U35-Q02', '전국 rollout 시작일이 2025-06-30 이후인가요?'],
  ['SV3-U35-Q03', '전국 rollout 시작일이 2024-12-31 이전인가요?'],
  ['SV3-U36-Q01', 'production batch service가 Java 17에서 실행되나요?'],
  ['SV3-U36-Q02', 'production batch service가 Java 21에서 실행되나요?'],
])

const FACT_TEXT = new Map([
  ['SV3-U32-P03', 'The approved service launch date was 2025-07-15 after the accessibility review.'],
  ['SV3-U35-P02', '전국 rollout 시작일은 2025-07-01이었고 지역별 담당자가 적용 상태를 확인했다.'],
])

const QUERY_QUALIFIER = new Map([
  ['SV3-U32-Q03', 'approved service launch date'],
  ['SV3-U32-Q04', 'approved service launch date'],
  ['SV3-U35-Q02', '전국 rollout 시작일'],
  ['SV3-U35-Q03', '전국 rollout 시작일'],
])

const OBSERVATION_QUALIFIER = new Map([
  ['SV3-TC-U32-P03-O01', 'approved service launch date'],
  ['SV3-TC-U35-P02-O01', '전국 rollout 시작일'],
])

const CONSTRAINT_SURFACE = new Map([
  ['SV3-U31-Q02', '1,300명'],
  ['SV3-U36-Q04', '2,329명'],
])

const DATE_OPERATOR = new Map([
  ['SV3-U32-Q03', 'GT'],
  ['SV3-U32-Q04', 'LT'],
  ['SV3-U35-Q01', 'RANGE'],
  ['SV3-U35-Q02', 'GTE'],
  ['SV3-U35-Q03', 'LT'],
])

const DIRECTION_SURFACE = new Map([
  ['SV3-TC-U31-P03-O01', '감소'],
  ['SV3-TC-U31-P04-O01', '증가'],
  ['SV3-TC-U36-P03-O01', '감소'],
])

const FORBIDDEN_RUNTIME_KEYS = new Set([
  'chunkId', 'expectedChunkId', 'runtimeChunkId', 'runtimeParentId',
  'databaseParentId', 'dbChunkId', 'retrievalPassageId',
])

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function json(value) {
  return `${JSON.stringify(value, null, 2)}\n`
}

function clone(value) {
  return structuredClone(value)
}

function normalize(value) {
  return value.normalize('NFKC').toLocaleLowerCase('und')
    .replace(/[\p{P}\p{S}]+/gu, ' ').replace(/\s+/gu, ' ').trim()
}

function codePoints(value) {
  return Array.from(value)
}

function codePointOffset(value, utf16Offset) {
  return codePoints(value.slice(0, utf16Offset)).length
}

function codePointSlice(value, start, end) {
  return codePoints(value).slice(start, end).join('')
}

function locate(content, surface, label, withinStart = 0, withinEnd = codePoints(content).length) {
  const within = codePointSlice(content, withinStart, withinEnd)
  const firstUtf16 = within.indexOf(surface)
  if (firstUtf16 < 0 || within.indexOf(surface, firstUtf16 + surface.length) >= 0) {
    throw new Error(`${label} must occur exactly once in its allowed source range`)
  }
  const start = withinStart + codePointOffset(within, firstUtf16)
  return [start, start + codePoints(surface).length]
}

function lineAt(content, offset) {
  return codePointSlice(content, 0, offset).split('\n').length
}

function readPrevious(relative) {
  return readFileSync(path.join(PREVIOUS_ROOT, relative), 'utf8')
}

function readPreviousJson(relative) {
  return JSON.parse(readPrevious(relative))
}

function updateDatasetVersion(artifact) {
  artifact.datasetVersion = DATASET_VERSION
  return artifact
}

function updateSpan(span, text, content) {
  const [charStart, charEnd] = locate(content, text, span.spanId)
  span.charStart = charStart
  span.charEnd = charEnd
  span.lineStart = lineAt(content, charStart)
  span.lineEnd = lineAt(content, charEnd)
  span.text = text
  span.textSha256 = sha256(Buffer.from(text, 'utf8'))
}

function fileEntry(relative, content) {
  const bytes = Buffer.from(content, 'utf8')
  return { path: relative, bytes: bytes.length, sha256: sha256(bytes) }
}

function combinedHash(entries) {
  const record = [...entries].sort((left, right) => left.path.localeCompare(right.path))
    .map((entry) => `${entry.path}\0${entry.sha256}\n`).join('')
  return sha256(Buffer.from(record, 'utf8'))
}

function countBy(values) {
  return Object.fromEntries([...new Set(values)].sort().map((value) => [
    value, values.filter((candidate) => candidate === value).length,
  ]))
}

function refreshSplit(directory) {
  const split = directory === 'dev' ? 'DEV' : 'CALIBRATION'
  const corpus = updateDatasetVersion(clone(readPreviousJson(`${directory}/corpus.json`)))
  const gold = updateDatasetVersion(clone(readPreviousJson(`${directory}/gold-evidence.json`)))
  const questions = updateDatasetVersion(clone(readPreviousJson(`${directory}/questions.json`)))
  const typed = updateDatasetVersion(clone(readPreviousJson(`${directory}/typed-annotations.json`)))
  const documents = new Map()

  for (const bundle of corpus.userBundles) {
    for (const document of bundle.documents) {
      let content = readPrevious(`${directory}/${document.contentPath}`)
      for (const parent of gold.parents.filter((value) => value.versionId === document.versionId)) {
        const replacement = FACT_TEXT.get(parent.parentId)
        if (replacement) content = content.replace(parent.sourceSpan.text, replacement)
      }
      document.contentSha256 = sha256(Buffer.from(content, 'utf8'))
      document.provenance.generatorRevision = GENERATOR_REVISION
      documents.set(document.versionId, { content, contentPath: document.contentPath })
    }
  }

  const parentMap = new Map()
  for (const parent of gold.parents) {
    const text = FACT_TEXT.get(parent.parentId) ?? parent.sourceSpan.text
    updateSpan(parent.sourceSpan, text, documents.get(parent.versionId).content)
    parentMap.set(parent.parentId, parent)
  }
  const unitMap = new Map()
  for (const unit of gold.evidenceUnits) {
    const text = FACT_TEXT.get(unit.parentId) ?? unit.sourceSpans[0].text
    for (const span of unit.sourceSpans) updateSpan(span, text, documents.get(unit.versionId).content)
    unitMap.set(unit.evidenceUnitId, unit)
  }

  const queryMap = new Map()
  for (const query of questions.queries) {
    query.query = QUERY_TEXT.get(query.queryId) ?? query.query
    query.normalizedQuery = normalize(query.query)
    queryMap.set(query.queryId, query)
  }

  for (const annotation of typed.queryAnnotations) {
    const query = queryMap.get(annotation.queryId)
    const constraint = annotation.constraint
    constraint.surface = CONSTRAINT_SURFACE.get(annotation.queryId) ?? constraint.surface
    constraint.qualifier = QUERY_QUALIFIER.get(annotation.queryId) ?? constraint.qualifier
    if (DATE_OPERATOR.has(annotation.queryId)) constraint.operator = DATE_OPERATOR.get(annotation.queryId)
    ;[constraint.queryCharStart, constraint.queryCharEnd] = locate(
      query.query, constraint.surface, `${annotation.queryId} constraint surface`,
    )
    if (constraint.qualifier) {
      ;[constraint.qualifierCharStart, constraint.qualifierCharEnd] = locate(
        query.query, constraint.qualifier, `${annotation.queryId} query qualifier`,
      )
    } else {
      delete constraint.qualifierCharStart
      delete constraint.qualifierCharEnd
    }
  }

  for (const observation of typed.observations) {
    const unit = unitMap.get(observation.evidenceUnitId)
    const span = unit.sourceSpans.find((value) => value.spanId === observation.sourceSpanId)
    const content = documents.get(unit.versionId).content
    observation.qualifier = OBSERVATION_QUALIFIER.get(observation.observationId) ?? observation.qualifier
    ;[observation.charStart, observation.charEnd] = locate(
      content, observation.sourceSurface, `${observation.observationId} surface`,
      span.charStart, span.charEnd,
    )
    if (observation.qualifier) {
      ;[observation.qualifierCharStart, observation.qualifierCharEnd] = locate(
        content, observation.qualifier, `${observation.observationId} qualifier`,
        span.charStart, span.charEnd,
      )
    } else {
      delete observation.qualifierCharStart
      delete observation.qualifierCharEnd
    }
    const directionSurface = DIRECTION_SURFACE.get(observation.observationId)
    if (observation.direction !== undefined && observation.direction !== 'NONE') {
      if (!directionSurface) throw new Error(`${observation.observationId} lacks a direction source surface`)
      observation.directionSourceSurface = directionSurface
      ;[observation.directionCharStart, observation.directionCharEnd] = locate(
        content, directionSurface, `${observation.observationId} direction`,
        span.charStart, span.charEnd,
      )
    } else {
      delete observation.directionSourceSurface
      delete observation.directionCharStart
      delete observation.directionCharEnd
    }
  }
  return { split, corpus, gold, questions, typed, documents, parentMap, unitMap, queryMap }
}

function splitDistributions(value) {
  return {
    profession: countBy(value.corpus.userBundles.map((bundle) => bundle.professionGroup)),
    documentLanguage: countBy(value.corpus.userBundles.flatMap((bundle) => bundle.documents.map((document) => document.language))),
    queryLanguage: countBy(value.questions.queries.map((query) => query.language)),
    answerability: countBy(value.questions.queries.map((query) => query.answerability)),
    category: countBy(value.questions.queries.flatMap((query) => query.categories)),
    typedKind: countBy(value.typed.queryAnnotations.map((annotation) => annotation.constraint.kind)),
    typedOperator: countBy(value.typed.queryAnnotations.map((annotation) => annotation.constraint.operator ?? 'EXACT')),
    stressFamily: countBy(value.typed.queryAnnotations.flatMap((annotation) => annotation.stressFamilies)),
    expectedState: countBy(value.typed.queryAnnotations.flatMap((annotation) => (
      annotation.expectedEvidenceStates.map((state) => state.state)
    ))),
  }
}

function buildArtifacts() {
  const files = new Map()
  const splits = [refreshSplit('dev'), refreshSplit('calibration')]
  const splitHashes = {}
  for (const value of splits) {
    const directory = value.split === 'DEV' ? 'dev' : 'calibration'
    for (const bundle of value.corpus.userBundles) {
      for (const document of bundle.documents) {
        files.set(`${directory}/${document.contentPath}`, value.documents.get(document.versionId).content)
      }
    }
    files.set(`${directory}/corpus.json`, json(value.corpus))
    files.set(`${directory}/gold-evidence.json`, json(value.gold))
    files.set(`${directory}/questions.json`, json(value.questions))
    files.set(`${directory}/typed-annotations.json`, json(value.typed))
    const inputs = [...files.keys()].filter((relative) => relative.startsWith(`${directory}/`)).sort()
    const entries = inputs.map((relative) => fileEntry(relative.slice(directory.length + 1), files.get(relative)))
    const manifest = {
      artifactType: 'MANIFEST', schemaVersion: '1.0.0', datasetVersion: DATASET_VERSION,
      split: value.split, status: 'FRESH_BENCHMARK_SEED_FROZEN', mutable: false,
      opened: true, searchExecuted: false, sealedAt: null, frozenAt: FROZEN_AT,
      generationSourceRevision: MATERIALIZATION_BASE_REVISION,
      generatorRevision: GENERATOR_REVISION,
      previousVersion: PREVIOUS_VERSION, previousVersionStatus: 'INVALID_INPUT_HISTORICAL',
      counts: {
        userBundles: value.corpus.userBundles.length,
        documents: value.corpus.userBundles.flatMap((bundle) => bundle.documents).length,
        queries: value.questions.queries.length,
        typedQueryAnnotations: value.typed.queryAnnotations.length,
        evidenceParents: value.gold.parents.length,
        evidenceGroups: value.gold.evidenceGroups.length,
        evidenceUnits: value.gold.evidenceUnits.length,
        typedObservations: value.typed.observations.length,
      },
      distributions: splitDistributions(value), files: entries,
      combinedSha256: combinedHash(entries),
    }
    splitHashes[value.split] = manifest.combinedSha256
    files.set(`${directory}/manifest.json`, json(manifest))
  }

  const oldLineage = readPreviousJson('lineage.json')
  const lineage = clone(oldLineage)
  lineage.datasetVersion = DATASET_VERSION
  lineage.previousVersion = PREVIOUS_VERSION
  lineage.previousVersionStatus = 'INVALID_INPUT_HISTORICAL'
  lineage.previousVersionBenchmarkExecuted = false
  lineage.correctionReason = 'Pre-implementation extraction-feasibility audit found ungrounded qualifier wording and ambiguous date operators.'
  lineage.generator = 'scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs'
  lineage.generatorRevision = GENERATOR_REVISION
  lineage.materializationBaseRevision = MATERIALIZATION_BASE_REVISION
  lineage.sealedFinalPolicy = 'SEALED_FINAL_TEST is not copied, opened, or searched; only manifest metadata is checked.'
  for (const entry of lineage.bundles) {
    const split = splits.find((candidate) => candidate.split === entry.split)
    const bundleUnits = split.gold.evidenceUnits.filter((unit) => unit.userBundleId === entry.userBundleId)
    const bundleQueries = split.questions.queries.filter((query) => query.userBundleId === entry.userBundleId)
    entry.sourceFactSignatures = bundleUnits.map((unit) => sha256(Buffer.from(
      normalize(unit.sourceSpans.map((span) => span.text).join(' ')), 'utf8',
    )))
    entry.normalizedQueries = bundleQueries.map((query) => query.normalizedQuery)
  }
  files.set('lineage.json', json(lineage))
  files.set('README.md', `# PRZ-028 Typed Constraint Stress DEV/CAL 1.0.1\n\n- dataset: \`${DATASET_VERSION}\`\n- status: \`FRESH_BENCHMARK_SEED_FROZEN\`\n- previous: \`${PREVIOUS_VERSION}\` = \`INVALID_INPUT_HISTORICAL\`\n- previous benchmark/search execution: \`NOT_RUN\`\n- correction: exact query/source qualifier grounding, unambiguous date comparison operators, mixed-language truth, and explicit qualifier/direction source offsets\n- scope: synthetic DEV/CAL only; no personal data\n- SEALED FINAL: not copied, not opened, not searched\n- generator: \`scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs\`\n\nRun \`node scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs --check\` for deterministic validation. A non-check run refuses to overwrite this frozen directory.\n`)

  const rootEntries = [...files.keys()].sort().map((relative) => fileEntry(relative, files.get(relative)))
  const allQueries = splits.flatMap((value) => value.questions.queries)
  const allTyped = splits.flatMap((value) => value.typed.queryAnnotations)
  const allUnits = splits.flatMap((value) => value.gold.evidenceUnits)
  const allObservations = splits.flatMap((value) => value.typed.observations)
  const manifest = {
    artifactType: 'MANIFEST', schemaVersion: '1.0.0', datasetVersion: DATASET_VERSION,
    split: 'ALL', status: 'FRESH_BENCHMARK_SEED_FROZEN', mutable: false,
    opened: true, searchExecuted: false, sealedAt: null, frozenAt: FROZEN_AT,
    generationSourceRevision: MATERIALIZATION_BASE_REVISION,
    generatorRevision: GENERATOR_REVISION,
    previousDatasets: [
      { datasetVersion: PREVIOUS_VERSION, status: 'INVALID_INPUT_HISTORICAL', benchmarkExecuted: false },
      { datasetVersion: 'search-v3-fresh-seed-1.0.1', status: 'FROZEN_INPUT' },
      { datasetVersion: 'search-v3-fresh-devcal-1.1.0', status: 'FROZEN_INPUT' },
      { datasetVersion: 'search-v3-fresh-devcal-robustness-1.0.0', status: 'FROZEN_INPUT' },
    ],
    splitCombinedSha256: splitHashes,
    counts: {
      userBundles: 6, documents: 6, queries: allQueries.length,
      typedQueryAnnotations: allTyped.length, evidenceParents: allUnits.length,
      evidenceGroups: allUnits.length, evidenceUnits: allUnits.length,
      typedObservations: allObservations.length,
    },
    distributions: {
      split: { CALIBRATION: 3, DEV: 3 },
      profession: countBy(splits.flatMap((value) => value.corpus.userBundles.map((bundle) => bundle.professionGroup))),
      documentLanguage: countBy(splits.flatMap((value) => value.corpus.userBundles.flatMap((bundle) => bundle.documents.map((document) => document.language)))),
      queryLanguage: countBy(allQueries.map((query) => query.language)),
      answerability: countBy(allQueries.map((query) => query.answerability)),
      category: countBy(allQueries.flatMap((query) => query.categories)),
      typedKind: countBy(allTyped.map((annotation) => annotation.constraint.kind)),
      typedOperator: countBy(allTyped.map((annotation) => annotation.constraint.operator ?? 'EXACT')),
      stressFamily: countBy(allTyped.flatMap((annotation) => annotation.stressFamilies)),
      expectedState: countBy(allTyped.flatMap((annotation) => annotation.expectedEvidenceStates.map((state) => state.state))),
    },
    files: rootEntries, combinedSha256: combinedHash(rootEntries),
  }
  files.set('manifest.json', json(manifest))
  return files
}

function detectRuntimeKeys(value, location, findings) {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => detectRuntimeKeys(entry, `${location}[${index}]`, findings))
    return
  }
  if (value === null || typeof value !== 'object') return
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_RUNTIME_KEYS.has(key)) findings.push(`${location}.${key} is a forbidden runtime identifier`)
    detectRuntimeKeys(child, `${location}.${key}`, findings)
  }
}

function exactGrounding(content, value, start, end) {
  if (!Number.isInteger(start) || !Number.isInteger(end) || start >= end) return false
  const actual = codePointSlice(content, start, end)
  if (actual !== value || normalize(actual) !== normalize(value)) return false
  const normalizedContent = normalize(content)
  return normalize(value).split(' ').filter(Boolean).every((token) => normalizedContent.includes(token))
}

function validateFiles(files) {
  const findings = []
  const parsed = (relative) => JSON.parse(files.get(relative))
  const root = parsed('manifest.json')
  const lineage = parsed('lineage.json')
  let queries = 0
  let observations = 0
  for (const directory of ['dev', 'calibration']) {
    const corpus = parsed(`${directory}/corpus.json`)
    const gold = parsed(`${directory}/gold-evidence.json`)
    const questions = parsed(`${directory}/questions.json`)
    const typed = parsed(`${directory}/typed-annotations.json`)
    const manifest = parsed(`${directory}/manifest.json`)
    const documentMap = new Map()
    for (const bundle of corpus.userBundles) {
      for (const document of bundle.documents) {
        const content = files.get(`${directory}/${document.contentPath}`)
        if (content === undefined || document.contentSha256 !== sha256(Buffer.from(content, 'utf8'))) {
          findings.push(`${document.versionId} content hash mismatch`)
        }
        documentMap.set(document.versionId, { content, userBundleId: bundle.userBundleId })
      }
    }
    const parentMap = new Map(gold.parents.map((parent) => [parent.parentId, parent]))
    const unitMap = new Map(gold.evidenceUnits.map((unit) => [unit.evidenceUnitId, unit]))
    for (const unit of gold.evidenceUnits) {
      const document = documentMap.get(unit.versionId)
      const parent = parentMap.get(unit.parentId)
      if (!document || !parent || document.userBundleId !== unit.userBundleId) {
        findings.push(`${unit.evidenceUnitId} graph mismatch`)
        continue
      }
      for (const span of [parent.sourceSpan, ...unit.sourceSpans]) {
        const actual = codePointSlice(document.content, span.charStart, span.charEnd)
        if (actual !== span.text || sha256(Buffer.from(actual, 'utf8')) !== span.textSha256) {
          findings.push(`${span.spanId} source span mismatch`)
        }
      }
    }
    const queryMap = new Map(questions.queries.map((query) => [query.queryId, query]))
    queries += questions.queries.length
    observations += typed.observations.length
    for (const annotation of typed.queryAnnotations) {
      const query = queryMap.get(annotation.queryId)
      const constraint = annotation.constraint
      if (!query || !exactGrounding(query.query, constraint.surface, constraint.queryCharStart, constraint.queryCharEnd)) {
        findings.push(`${annotation.queryId} constraint surface is not exactly query-grounded`)
      }
      if (constraint.qualifier && !exactGrounding(
        query.query, constraint.qualifier, constraint.qualifierCharStart, constraint.qualifierCharEnd,
      )) findings.push(`${annotation.queryId} qualifier is not exactly query-grounded`)
      if (constraint.kind === 'QUANTITY') {
        if (!/[0-9]/u.test(constraint.surface)) findings.push(`${annotation.queryId} quantity core lacks a numeral`)
        if (constraint.qualifier && normalize(constraint.surface).includes(normalize(constraint.qualifier))) {
          findings.push(`${annotation.queryId} quantity surface improperly includes its qualifier`)
        }
        if (!new Set(['EQ', 'GTE', 'RANGE']).has(constraint.operator)) {
          findings.push(`${annotation.queryId} invalid quantity operator ${constraint.operator}`)
        }
      }
      if (constraint.kind === 'DATE' && !new Set(['GT', 'LT', 'GTE', 'RANGE']).has(constraint.operator)) {
        findings.push(`${annotation.queryId} ambiguous date operator ${constraint.operator}`)
      }
      if (constraint.kind === 'IDENTIFIER_NUMBER') {
        if (!normalize(constraint.surface).includes(normalize(constraint.identifier))
            || !constraint.surface.includes(constraint.numberSurface)) {
          findings.push(`${annotation.queryId} identifier-number is not surface-grounded`)
        }
      }
      if (constraint.kind === 'LITERAL_IDENTIFIER'
          && normalize(constraint.surface) !== constraint.normalizedLiteral) {
        findings.push(`${annotation.queryId} literal normalization mismatch`)
      }
    }
    for (const observation of typed.observations) {
      const unit = unitMap.get(observation.evidenceUnitId)
      const document = unit && documentMap.get(unit.versionId)
      const span = unit?.sourceSpans.find((candidate) => candidate.spanId === observation.sourceSpanId)
      if (!document || !span || !exactGrounding(
        document.content, observation.sourceSurface, observation.charStart, observation.charEnd,
      ) || observation.charStart < span.charStart || observation.charEnd > span.charEnd) {
        findings.push(`${observation.observationId} source surface/offset mismatch`)
        continue
      }
      if (observation.qualifier && !exactGrounding(
        document.content, observation.qualifier,
        observation.qualifierCharStart, observation.qualifierCharEnd,
      )) findings.push(`${observation.observationId} qualifier is not exactly source-grounded`)
      if (observation.qualifier
          && (observation.qualifierCharStart < span.charStart || observation.qualifierCharEnd > span.charEnd)) {
        findings.push(`${observation.observationId} qualifier escapes its evidence span`)
      }
      if (observation.direction && observation.direction !== 'NONE') {
        if (!exactGrounding(
          document.content, observation.directionSourceSurface,
          observation.directionCharStart, observation.directionCharEnd,
        ) || observation.directionCharStart < span.charStart || observation.directionCharEnd > span.charEnd) {
          findings.push(`${observation.observationId} direction is not exactly source-grounded`)
        }
      } else if (observation.directionSourceSurface !== undefined
          || observation.directionCharStart !== undefined || observation.directionCharEnd !== undefined) {
        findings.push(`${observation.observationId} has inapplicable direction offsets`)
      }
      if (observation.kind === 'IDENTIFIER_NUMBER'
          && (!normalize(observation.sourceSurface).includes(normalize(observation.identifier))
            || !observation.sourceSurface.includes(observation.numberSurface))) {
        findings.push(`${observation.observationId} identifier-number observation mismatch`)
      }
      if (observation.kind === 'LITERAL_IDENTIFIER'
          && normalize(observation.sourceSurface) !== observation.normalizedLiteral) {
        findings.push(`${observation.observationId} literal observation normalization mismatch`)
      }
    }
    const entries = manifest.files
    for (const entry of entries) {
      const content = files.get(`${directory}/${entry.path}`)
      if (content === undefined || Buffer.byteLength(content, 'utf8') !== entry.bytes
          || sha256(Buffer.from(content, 'utf8')) !== entry.sha256) {
        findings.push(`${directory} manifest mismatch: ${entry.path}`)
      }
    }
    if (combinedHash(entries) !== manifest.combinedSha256 || manifest.mutable !== false
        || manifest.previousVersionStatus !== 'INVALID_INPUT_HISTORICAL') {
      findings.push(`${directory} manifest freeze/lineage mismatch`)
    }
  }
  if (queries !== 24 || observations !== 25 || root.counts.userBundles !== 6 || root.counts.documents !== 6) {
    findings.push('dataset count mismatch')
  }
  if (root.previousDatasets[0]?.datasetVersion !== PREVIOUS_VERSION
      || root.previousDatasets[0]?.status !== 'INVALID_INPUT_HISTORICAL'
      || root.previousDatasets[0]?.benchmarkExecuted !== false
      || lineage.previousVersionStatus !== 'INVALID_INPUT_HISTORICAL'
      || lineage.previousVersionBenchmarkExecuted !== false) {
    findings.push('v1.0.0 historical-invalid lineage is missing')
  }
  detectRuntimeKeys({ lineage, typed: ['dev', 'calibration'].map((value) => parsed(`${value}/typed-annotations.json`)) }, 'stress', findings)
  for (const entry of root.files) {
    const content = files.get(entry.path)
    if (content === undefined || Buffer.byteLength(content, 'utf8') !== entry.bytes
        || sha256(Buffer.from(content, 'utf8')) !== entry.sha256) {
      findings.push(`root manifest mismatch: ${entry.path}`)
    }
  }
  if (combinedHash(root.files) !== root.combinedSha256 || root.mutable !== false
      || root.searchExecuted !== false) findings.push('root manifest freeze/hash/state mismatch')
  const declared = root.files.map((entry) => entry.path).sort()
  const actual = [...files.keys()].filter((relative) => relative !== 'manifest.json').sort()
  if (JSON.stringify(declared) !== JSON.stringify(actual)) findings.push('root manifest inventory mismatch')
  return findings
}

function priorCollisionFindings(current, previous, prefix) {
  const findings = []
  const priorBundles = previous.bundles.filter((entry) => entry.split !== 'SEALED_FINAL_TEST')
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    const known = new Set(priorBundles.map((entry) => entry[field]))
    if (current.bundles.some((entry) => known.has(entry[field]))) findings.push(`${prefix} collision: ${field}`)
  }
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const known = new Set(priorBundles.flatMap((entry) => entry[field]))
    if (current.bundles.flatMap((entry) => entry[field]).some((value) => known.has(value))) {
      findings.push(`${prefix} collision: ${field}`)
    }
  }
  return findings
}

async function externalFindings(files) {
  const findings = []
  const oldManifest = readPreviousJson('manifest.json')
  if (oldManifest.combinedSha256 !== PREVIOUS_SHA256 || oldManifest.searchExecuted !== false) {
    findings.push('v1.0.0 bytes/state changed')
  }
  const current = JSON.parse(files.get('lineage.json'))
  const oldLineage = readPreviousJson('lineage.json')
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    if (JSON.stringify(current.bundles.map((entry) => entry[field]))
        !== JSON.stringify(oldLineage.bundles.map((entry) => entry[field]))) {
      findings.push(`v1.0.0 continuity mismatch: ${field}`)
    }
  }
  for (const field of ['logicalDocumentIds', 'versionLineageIds', 'sourceFactIds', 'questionGroupIds']) {
    if (JSON.stringify(current.bundles.map((entry) => entry[field]))
        !== JSON.stringify(oldLineage.bundles.map((entry) => entry[field]))) {
      findings.push(`v1.0.0 continuity mismatch: ${field}`)
    }
  }
  const previousPaths = [
    ['original DEV/CAL', path.resolve('src/test/resources/search-v3-evaluation/lineage.json')],
    ['long-form 1.1.0', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/lineage.json')],
    ['robustness 1.0.0', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0/lineage.json')],
  ]
  for (const [label, location] of previousPaths) {
    findings.push(...priorCollisionFindings(current, JSON.parse(await readFile(location, 'utf8')), label))
  }
  const sealed = JSON.parse(await readFile(SEALED_MANIFEST, 'utf8'))
  if (sealed.combinedSha256 !== SEALED_FINAL_SHA256
      || sealed.opened !== false || sealed.searchExecuted !== false) {
    findings.push('SEALED FINAL manifest metadata changed')
  }
  return findings
}

async function listFiles(root, current = '') {
  const entries = await readdir(path.join(root, current), { withFileTypes: true }).catch(() => [])
  const result = []
  for (const entry of entries) {
    const relative = path.posix.join(current.replaceAll('\\', '/'), entry.name)
    if (entry.isDirectory()) result.push(...await listFiles(root, relative))
    else result.push(relative)
  }
  return result.sort()
}

async function check(files) {
  const findings = [...validateFiles(files), ...await externalFindings(files)]
  for (const [relative, expected] of files) {
    const actual = await readFile(path.join(OUTPUT_ROOT, relative), 'utf8').catch(() => null)
    if (actual !== expected) findings.push(`content mismatch: ${relative}`)
  }
  if (JSON.stringify(await listFiles(OUTPUT_ROOT)) !== JSON.stringify([...files.keys()].sort())) {
    findings.push('materialized inventory mismatch')
  }
  if (findings.length > 0) {
    findings.forEach((finding) => console.error(`FAIL ${finding}`))
    process.exitCode = 1
    return
  }
  const manifest = JSON.parse(files.get('manifest.json'))
  console.log(`PASS dataset=${DATASET_VERSION}`)
  console.log(`PASS files=${files.size}`)
  console.log(`PASS queries=${manifest.counts.queries}`)
  console.log(`PASS observations=${manifest.counts.typedObservations}`)
  console.log(`PASS combinedSha256=${manifest.combinedSha256}`)
  console.log(`PASS previous=${PREVIOUS_VERSION} status=INVALID_INPUT_HISTORICAL searchExecuted=false`)
  console.log(`PASS sealedFinal=${SEALED_FINAL_SHA256} opened=false searchExecuted=false`)
}

async function materialize(files) {
  if (existsSync(OUTPUT_ROOT)) {
    const status = existsSync(path.join(OUTPUT_ROOT, 'manifest.json'))
      ? JSON.parse(readFileSync(path.join(OUTPUT_ROOT, 'manifest.json'), 'utf8')).status
      : 'existing directory without manifest'
    throw new Error(`Refusing to overwrite ${OUTPUT_ROOT} (${status}); use --check or a new version`)
  }
  const findings = [...validateFiles(files), ...await externalFindings(files)]
  if (findings.length > 0) throw new Error(`Refusing invalid correction:\n${findings.join('\n')}`)
  for (const [relative, content] of files) {
    const destination = path.join(OUTPUT_ROOT, relative)
    await mkdir(path.dirname(destination), { recursive: true })
    await writeFile(destination, content, 'utf8')
  }
  console.log(`Materialized ${DATASET_VERSION} at ${OUTPUT_ROOT}`)
}

const files = buildArtifacts()
if (process.argv.includes('--check')) await check(files)
else await materialize(files)
