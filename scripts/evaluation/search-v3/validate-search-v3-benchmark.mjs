import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url))
export const DEFAULT_DATASET_ROOT = resolve(SCRIPT_DIR, '../../../src/test/resources/search-v3-evaluation')
const SPLITS = new Map([
  ['DEV', 'dev'],
  ['CALIBRATION', 'calibration'],
  ['SEALED_FINAL_TEST', 'sealed-final'],
])
const REQUIRED_CATEGORIES = [
  'literal_identifier', 'semantic_paraphrase', 'abstract_competency', 'numeric_quantity',
  'date_range', 'no_answer', 'hard_negative', 'negation', 'completion_state',
  'other_actor', 'multi_evidence', 'job_requirement', 'korean', 'english',
  'korean_english_mixed', 'typo_format_variation',
]
const REQUIRED_PROFESSIONS = [
  'BACKEND', 'FRONTEND_MOBILE', 'DATA_AI_INFRA', 'DESIGN_PRODUCT', 'PLANNING',
  'MARKETING_SALES', 'NON_DEVELOPMENT_GENERAL',
]
const REQUIRED_STRUCTURES = [
  'SHORT_RESUME', 'LONG_PORTFOLIO', 'CAREER_DESCRIPTION',
  'NARRATIVE_SELF_INTRODUCTION', 'TABLE_LIKE', 'CERTIFICATION_TRAINING',
]
const FORBIDDEN_GOLD_KEYS = new Set([
  'chunkId', 'expectedChunkId', 'documentChunkId', 'runtimeChunkId', 'runtimeParentId',
  'databaseParentId', 'dbChunkId',
])

export class DatasetValidationError extends Error {
  constructor(findings) {
    super(`Search V3 benchmark validation failed with ${findings.length} finding(s)\n${findings.map((finding) => `- ${finding}`).join('\n')}`)
    this.name = 'DatasetValidationError'
    this.findings = findings
  }
}

const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const posix = (value) => value.split(sep).join('/')
const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'))
const normalize = (value) => value
  .normalize('NFKC')
  .toLocaleLowerCase('und')
  .replace(/[\p{P}\p{S}]+/gu, ' ')
  .replace(/\s+/gu, ' ')
  .trim()

function record(findingList, condition, message) {
  if (!condition) findingList.push(message)
}

function uniqueMap(items, key, label, findings) {
  const map = new Map()
  for (const item of items) {
    const value = item[key]
    if (map.has(value)) findings.push(`duplicate ${label}: ${value}`)
    else map.set(value, item)
  }
  return map
}

function findRuntimeGoldContamination(value, path, findings) {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => findRuntimeGoldContamination(entry, `${path}[${index}]`, findings))
    return
  }
  if (!value || typeof value !== 'object') return
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_GOLD_KEYS.has(key)) findings.push(`${path}.${key} is a forbidden runtime database identifier`)
    findRuntimeGoldContamination(child, `${path}.${key}`, findings)
  }
}

function valueInEnum(schema, definition, value) {
  return schema.$defs?.[definition]?.enum?.includes(value) === true
}

function assertArtifactHeader(artifact, type, split, schema, label, findings) {
  record(findings, artifact.artifactType === type, `${label} artifactType must be ${type}`)
  record(findings, artifact.schemaVersion === schema.schemaVersion, `${label} schemaVersion mismatch`)
  record(findings, artifact.datasetVersion === 'search-v3-fresh-seed-1.0.1', `${label} datasetVersion mismatch`)
  record(findings, artifact.split === split, `${label} split must be ${split}`)
}

function codePointSlice(content, start, end) {
  return Array.from(content).slice(start, end).join('')
}

function expectedLine(content, charOffset) {
  return codePointSlice(content, 0, charOffset).split('\n').length
}

function validateSpan(span, parent, document, splitRoot, findings) {
  const label = span.spanId ?? 'span-without-id'
  record(findings, span.parentId === parent.parentId, `${label} parent does not match ${parent.parentId}`)
  record(findings, span.documentId === parent.documentId, `${label} document does not match parent`)
  record(findings, span.versionId === parent.versionId, `${label} version does not match parent`)
  record(findings, span.sourcePath === document.contentPath, `${label} sourcePath does not match document contentPath`)
  record(findings, Number.isInteger(span.charStart) && Number.isInteger(span.charEnd) && span.charStart < span.charEnd, `${label} char range is invalid`)
  const sourcePath = resolve(splitRoot, document.contentPath)
  if (!existsSync(sourcePath)) {
    findings.push(`${label} source file does not exist: ${document.contentPath}`)
    return
  }
  const content = readFileSync(sourcePath, 'utf8')
  const actual = codePointSlice(content, span.charStart, span.charEnd)
  record(findings, actual === span.text, `${label} source span text mismatch`)
  record(findings, sha256(Buffer.from(span.text, 'utf8')) === span.textSha256, `${label} text SHA-256 mismatch`)
  record(findings, span.lineStart === expectedLine(content, span.charStart), `${label} lineStart mismatch`)
  record(findings, span.lineEnd === expectedLine(content, span.charEnd), `${label} lineEnd mismatch`)
  if (document.fileType === 'TXT') {
    record(findings, span.sourceType === 'TXT_TEXT' && span.page === null, `${label} TXT source metadata mismatch`)
  }
}

function parseObservedNumber(surface) {
  const match = surface.replaceAll(',', '').match(/-?\d+(?:\.\d+)?/u)
  return match ? Number(match[0]) : null
}

function numericSatisfied(required, observed) {
  if (required.unit !== observed.unit || required.semanticType !== observed.semanticType) return false
  const value = observed.normalizedValue
  if (required.operator === 'EQ') return value === required.value
  if (required.operator === 'GTE') return value >= required.value
  if (required.operator === 'LTE') return value <= required.value
  if (required.operator === 'GT') return value > required.value
  if (required.operator === 'LT') return value < required.value
  if (required.operator === 'BETWEEN') return value >= required.value && value <= required.upperValue
  return false
}

function dateSatisfied(required, observed) {
  if (required.operator === 'CONTAINS') return observed.start <= required.start && observed.end >= required.end
  if (required.operator === 'WITHIN') return observed.start >= required.start && observed.end <= required.end
  if (required.operator === 'OVERLAPS') return observed.start <= required.end && observed.end >= required.start
  if (required.operator === 'STARTS_ON') return observed.start === required.start
  if (required.operator === 'ENDS_ON') return observed.end === required.end
  return false
}

function directExpected(aspectEntry, unitMap) {
  return aspectEntry.expectedEvidence
    .filter((entry) => entry.supportRelation === 'DIRECT_SUPPORT')
    .map((entry) => unitMap.get(entry.evidenceUnitId))
    .filter(Boolean)
}

function validateConstraints(query, aspectEntry, unitMap, findings) {
  const directUnits = directExpected(aspectEntry, unitMap)
  const label = `${query.queryId}/${aspectEntry.aspectId}`
  if (directUnits.length === 0) return
  for (const required of aspectEntry.constraints.entities) {
    const matched = directUnits.some((unit) => unit.entities.some((observed) => (
      observed.entityType === required.entityType && observed.canonicalValue === required.canonicalValue
    )))
    record(findings, matched, `${label} required entity has no DIRECT_SUPPORT observation: ${required.canonicalValue}`)
  }
  for (const required of aspectEntry.constraints.numerics) {
    const matched = directUnits.some((unit) => unit.numerics.some((observed) => numericSatisfied(required, observed)
      && required.qualifierTokens.every((token) => observed.qualifierTokens.map(normalize).includes(normalize(token)))))
    record(findings, matched, `${label} required numeric constraint has no satisfying DIRECT_SUPPORT observation`)
  }
  for (const required of aspectEntry.constraints.dates) {
    const matched = directUnits.some((unit) => unit.dates.some((observed) => dateSatisfied(required, observed)))
    record(findings, matched, `${label} required date constraint has no satisfying DIRECT_SUPPORT observation`)
  }
  if (aspectEntry.constraints.actors.length > 0) {
    record(findings, directUnits.some((unit) => aspectEntry.constraints.actors.includes(unit.actor)), `${label} required actor has no DIRECT_SUPPORT observation`)
  }
  if (aspectEntry.constraints.completionStates.length > 0) {
    record(findings, directUnits.some((unit) => aspectEntry.constraints.completionStates.includes(unit.completionState)), `${label} required completion state has no DIRECT_SUPPORT observation`)
  }
}

function validateAnswerability(query, unitMap, findings) {
  const requiredIds = query.aspectExpression.requiredAspectIds
  const aspectMap = uniqueMap(query.aspects, 'aspectId', `${query.queryId} aspectId`, findings)
  record(findings, requiredIds.every((id) => aspectMap.has(id)), `${query.queryId} aspect expression references a missing aspect`)
  record(findings, query.aspectExpression.minShouldMatch <= requiredIds.length, `${query.queryId} minShouldMatch exceeds required aspect count`)
  if (query.aspectExpression.operator === 'ALL') record(findings, query.aspectExpression.minShouldMatch === requiredIds.length, `${query.queryId} ALL expression must require every aspect`)
  if (query.aspectExpression.operator === 'ANY') record(findings, query.aspectExpression.minShouldMatch === 1, `${query.queryId} ANY expression must have minShouldMatch=1`)

  let supportedRequired = 0
  for (const aspectEntry of query.aspects) {
    const direct = aspectEntry.expectedEvidence.filter((entry) => entry.supportRelation === 'DIRECT_SUPPORT')
    if (aspectEntry.answerability === 'SUPPORTED') {
      record(findings, direct.length > 0, `${query.queryId}/${aspectEntry.aspectId} is SUPPORTED without DIRECT_SUPPORT`)
      const directGroupIds = new Set(direct.map((entry) => unitMap.get(entry.evidenceUnitId)?.groupId).filter(Boolean))
      record(findings, aspectEntry.requiredEvidenceGroupIds.every((id) => directGroupIds.has(id)), `${query.queryId}/${aspectEntry.aspectId} misses a required direct evidence group`)
      record(findings, directGroupIds.size >= aspectEntry.minEvidenceGroups, `${query.queryId}/${aspectEntry.aspectId} does not meet minEvidenceGroups`)
      if (requiredIds.includes(aspectEntry.aspectId)) supportedRequired += 1
    }
    if (aspectEntry.answerability === 'NOT_SUPPORTED') record(findings, direct.length === 0, `${query.queryId}/${aspectEntry.aspectId} is NOT_SUPPORTED but has DIRECT_SUPPORT`)
    validateConstraints(query, aspectEntry, unitMap, findings)
  }

  const threshold = query.aspectExpression.operator === 'ANY' ? 1 : query.aspectExpression.minShouldMatch
  const expressionSatisfied = supportedRequired >= threshold
  if (query.answerability === 'SUPPORTED') record(findings, expressionSatisfied, `${query.queryId} is SUPPORTED but its required aspect expression is not satisfied`)
  if (query.answerability === 'PARTIALLY_SUPPORTED') record(findings, supportedRequired > 0 && !expressionSatisfied, `${query.queryId} PARTIALLY_SUPPORTED does not represent a strict subset of required aspects`)
  if (query.answerability === 'NOT_SUPPORTED') record(findings, supportedRequired === 0, `${query.queryId} is NOT_SUPPORTED but has a supported required aspect`)
}

function validateDocument(document, splitRoot, findings) {
  const path = resolve(splitRoot, document.contentPath)
  if (!existsSync(path)) {
    findings.push(`${document.versionId} content file does not exist: ${document.contentPath}`)
    return
  }
  const bytes = readFileSync(path)
  const content = bytes.toString('utf8')
  record(findings, bytes.length < 3 || !(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf), `${document.versionId} must not have a UTF-8 BOM`)
  record(findings, !content.includes('\r'), `${document.versionId} must use LF line endings`)
  record(findings, sha256(bytes) === document.contentSha256, `${document.versionId} content SHA-256 mismatch`)
  record(findings, document.provenance.classification === 'SYNTHETIC', `${document.versionId} tracked seed provenance must be SYNTHETIC`)
  record(findings, document.provenance.license === 'Apache-2.0', `${document.versionId} tracked seed license must be Apache-2.0`)
  record(findings, document.fileType === 'TXT' && document.supportScope === 'SUPPORTED_BY_CURRENT', `${document.versionId} seed must stay in the Current/V3 comparable TXT cohort`)
}

function validateLineage(dataset, findings) {
  const seen = new Map()
  function block(keyType, key, split, owner) {
    const identity = `${keyType}:${key}`
    const existing = seen.get(identity)
    if (existing && existing.split !== split) findings.push(`${keyType} leakage across ${existing.split}/${split}: ${key} (${existing.owner}, ${owner})`)
    else if (!existing) seen.set(identity, { split, owner })
  }

  for (const bundle of dataset.bundles) {
    block('userBundleId', bundle.userBundleId, bundle.split, bundle.userBundleId)
    block('documentFamilyId', bundle.documentFamilyId, bundle.split, bundle.userBundleId)
    block('templateFamilyId', bundle.templateFamilyId, bundle.split, bundle.userBundleId)
    for (const document of bundle.documents) {
      block('logicalDocumentId', document.logicalDocumentId, bundle.split, bundle.userBundleId)
      block('versionLineageId', document.versionLineageId, bundle.split, bundle.userBundleId)
      block('generatorLineage', `${document.provenance.generatorName}@${document.provenance.generatorRevision}:${document.provenance.generatorSeedId}`, bundle.split, bundle.userBundleId)
    }
  }
  for (const unit of dataset.units) {
    const bundle = dataset.bundleMap.get(unit.userBundleId)
    block('sourceFactId', unit.sourceFactId, bundle.split, unit.userBundleId)
    block('sourceFactSignature', sha256(Buffer.from(normalize(unit.sourceSpans.map((span) => span.text).join(' ')), 'utf8')), bundle.split, unit.userBundleId)
  }
  for (const query of dataset.queries) {
    const bundle = dataset.bundleMap.get(query.userBundleId)
    block('questionGroupId', query.questionGroupId, bundle.split, query.userBundleId)
    block('normalizedQuery', query.normalizedQuery, bundle.split, query.userBundleId)
  }

  const declaredMap = new Map(dataset.lineage.bundles.map((entry) => [entry.userBundleId, entry]))
  for (const bundle of dataset.bundles) {
    const declared = declaredMap.get(bundle.userBundleId)
    record(findings, Boolean(declared), `${bundle.userBundleId} missing lineage record`)
    if (!declared) continue
    record(findings, declared.split === bundle.split, `${bundle.userBundleId} lineage split mismatch`)
    record(findings, declared.templateFamilyId === bundle.templateFamilyId, `${bundle.userBundleId} lineage template mismatch`)
    record(findings, declared.generatorSeedId === bundle.documents[0].provenance.generatorSeedId, `${bundle.userBundleId} lineage generator seed mismatch`)
  }
}

function loadArtifacts(root, findings) {
  const schemaPath = resolve(root, 'schema/search-v3-benchmark.schema.json')
  const predictionSchemaPath = resolve(root, 'schema/search-v3-prediction.schema.json')
  record(findings, existsSync(schemaPath), 'benchmark schema is missing')
  record(findings, existsSync(predictionSchemaPath), 'prediction adapter schema is missing')
  if (findings.length > 0) return null
  const schema = readJson(schemaPath)
  const predictionSchema = readJson(predictionSchemaPath)
  const lineage = readJson(resolve(root, 'lineage.json'))
  const artifacts = new Map()
  for (const [split, directory] of SPLITS) {
    const splitRoot = resolve(root, directory)
    artifacts.set(split, {
      splitRoot,
      corpus: readJson(resolve(splitRoot, 'corpus.json')),
      gold: readJson(resolve(splitRoot, 'gold-evidence.json')),
      questions: readJson(resolve(splitRoot, 'questions.json')),
      manifest: readJson(resolve(splitRoot, 'manifest.json')),
    })
  }
  return { schema, predictionSchema, lineage, artifacts, overallManifest: readJson(resolve(root, 'manifest.json')) }
}

function listFiles(root) {
  const output = []
  function visit(path) {
    for (const name of readdirSync(path)) {
      const child = resolve(path, name)
      if (statSync(child).isDirectory()) visit(child)
      else output.push(child)
    }
  }
  visit(root)
  return output
}

function combinedHash(files) {
  const recordText = [...files]
    .sort((left, right) => left.path.localeCompare(right.path))
    .map((entry) => `${entry.path}\0${entry.sha256}\n`)
    .join('')
  return sha256(Buffer.from(recordText, 'utf8'))
}

function validateManifest(root, manifest, manifestPath, findings) {
  const seenPaths = new Set()
  for (const entry of manifest.files) {
    if (seenPaths.has(entry.path)) findings.push(`${manifestPath} duplicates file entry ${entry.path}`)
    seenPaths.add(entry.path)
    const path = resolve(root, entry.path)
    if (!existsSync(path)) {
      findings.push(`${manifestPath} references missing file ${entry.path}`)
      continue
    }
    const bytes = readFileSync(path)
    record(findings, bytes.length === entry.bytes, `${manifestPath} byte count mismatch for ${entry.path}`)
    record(findings, sha256(bytes) === entry.sha256, `${manifestPath} SHA-256 mismatch for ${entry.path}`)
  }
  record(findings, combinedHash(manifest.files) === manifest.combinedSha256, `${manifestPath} combined SHA-256 mismatch`)
}

function validateManifestCoverage(root, loaded, findings) {
  for (const [split, directory] of SPLITS) {
    const manifest = loaded.artifacts.get(split).manifest
    const manifestPath = `${directory}/manifest.json`
    validateManifest(root, manifest, manifestPath, findings)
    record(findings, manifest.opened === false && manifest.searchExecuted === false, `${manifestPath} must remain unopened and unexecuted`)
    if (split === 'SEALED_FINAL_TEST') {
      record(findings, manifest.status === 'SEALED' && manifest.mutable === false, `${manifestPath} must be immutable SEALED`)
      record(findings, typeof manifest.sealedAt === 'string', `${manifestPath} must have sealedAt`)
      const resultFiles = listFiles(resolve(root, directory)).filter((path) => /(?:result|prediction|output)/iu.test(relative(root, path)))
      record(findings, resultFiles.length === 0, `${manifestPath} contains forbidden search result/prediction files`)
    }
  }
  validateManifest(root, loaded.overallManifest, 'manifest.json', findings)
  const actual = listFiles(root)
    .map((path) => posix(relative(root, path)))
    .filter((path) => path !== 'manifest.json')
    .sort()
  const declared = loaded.overallManifest.files.map((entry) => entry.path).sort()
  record(findings, JSON.stringify(actual) === JSON.stringify(declared), 'overall manifest file inventory does not exactly match the dataset tree')
  record(findings, loaded.overallManifest.status === 'FRESH_BENCHMARK_SEED_FROZEN', 'overall manifest status mismatch')
  record(findings, loaded.overallManifest.opened === false && loaded.overallManifest.searchExecuted === false, 'overall manifest must remain unopened and unexecuted')
}

function validateCounts(loaded, dataset, findings) {
  const manifest = loaded.overallManifest
  const counts = {
    userBundles: dataset.bundles.length,
    logicalDocuments: new Set(dataset.documents.map((entry) => entry.logicalDocumentId)).size,
    documentVersions: dataset.documents.length,
    activeDocumentVersions: dataset.documents.filter((entry) => entry.active).length,
    inactiveDocumentVersions: dataset.documents.filter((entry) => !entry.active).length,
    queries: dataset.queries.length,
    evidenceParents: dataset.parents.length,
    evidenceGroups: dataset.groups.length,
    evidenceUnits: dataset.units.length,
  }
  record(findings, JSON.stringify(counts) === JSON.stringify(manifest.counts), 'overall manifest counts mismatch')
  record(findings, counts.userBundles >= 7, 'seed must contain at least seven user bundles')
  record(findings, new Set(dataset.bundles.map((entry) => entry.professionGroup)).size === REQUIRED_PROFESSIONS.length, 'seed profession group coverage is incomplete')
  record(findings, dataset.bundles.some((entry) => entry.documents.length >= 2), 'seed needs at least one multi-document bundle')
  const categories = new Set(dataset.queries.flatMap((entry) => entry.categories))
  for (const category of REQUIRED_CATEGORIES) record(findings, categories.has(category), `required query category is not materialized: ${category}`)
  const professions = new Set(dataset.bundles.map((entry) => entry.professionGroup))
  for (const profession of REQUIRED_PROFESSIONS) record(findings, professions.has(profession), `required profession group is not materialized: ${profession}`)
  const structures = new Set(dataset.documents.map((entry) => entry.documentStructure))
  for (const structure of REQUIRED_STRUCTURES) record(findings, structures.has(structure), `required document structure is not materialized: ${structure}`)
  const languages = new Set(dataset.documents.map((entry) => entry.language))
  for (const language of ['KO', 'EN', 'KO_EN_MIXED']) record(findings, languages.has(language), `required document language is not materialized: ${language}`)
  const answerability = new Set(dataset.queries.map((entry) => entry.answerability))
  for (const state of ['SUPPORTED', 'PARTIALLY_SUPPORTED', 'NOT_SUPPORTED']) record(findings, answerability.has(state), `answerability state is not materialized: ${state}`)
  const relations = new Set(dataset.queries.flatMap((query) => query.aspects.flatMap((entry) => entry.expectedEvidence.map((expected) => expected.supportRelation))))
  for (const relation of ['DIRECT_SUPPORT', 'RELATED', 'CONTRADICTS', 'INSUFFICIENT']) record(findings, relations.has(relation), `support relation is not materialized: ${relation}`)
  const safety = new Set(dataset.queries.flatMap((entry) => entry.safetyExclusions.map((exclusion) => exclusion.reason)))
  for (const reason of ['OWNER_LEAKAGE', 'INACTIVE_VERSION_LEAKAGE', 'WRONG_VERSION_LEAKAGE', 'UNAUTHORIZED_SOURCE_EXPOSURE']) record(findings, safety.has(reason), `safety exclusion is not materialized: ${reason}`)
}

export function validateBenchmark({ root = DEFAULT_DATASET_ROOT, verifyManifests = true } = {}) {
  const findings = []
  const loaded = loadArtifacts(root, findings)
  if (!loaded) throw new DatasetValidationError(findings)
  const { schema, predictionSchema, lineage, artifacts } = loaded
  record(findings, schema.schemaVersion === '1.0.0', 'benchmark schema version must be 1.0.0')
  record(findings, predictionSchema.schemaVersion === '1.0.0', 'prediction adapter schema version must be 1.0.0')
  record(findings, REQUIRED_CATEGORIES.every((entry) => valueInEnum(schema, 'queryCategory', entry)), 'benchmark schema query category enum is incomplete')
  record(findings, REQUIRED_PROFESSIONS.every((entry) => valueInEnum(schema, 'professionGroup', entry)), 'benchmark schema profession enum is incomplete')
  record(findings, lineage.artifactType === 'LINEAGE' && lineage.schemaVersion === schema.schemaVersion, 'lineage artifact header mismatch')

  const bundles = []
  const documents = []
  const parents = []
  const groups = []
  const units = []
  const queries = []
  for (const [split, artifact] of artifacts) {
    assertArtifactHeader(artifact.corpus, 'CORPUS', split, schema, `${split}/corpus`, findings)
    assertArtifactHeader(artifact.gold, 'GOLD_EVIDENCE', split, schema, `${split}/gold`, findings)
    assertArtifactHeader(artifact.questions, 'QUESTIONS', split, schema, `${split}/questions`, findings)
    for (const bundle of artifact.corpus.userBundles) {
      record(findings, bundle.split === split, `${bundle.userBundleId} is stored under the wrong split`)
      record(findings, valueInEnum(schema, 'professionGroup', bundle.professionGroup), `${bundle.userBundleId} has invalid professionGroup`)
      record(findings, valueInEnum(schema, 'language', bundle.languageProfile), `${bundle.userBundleId} has invalid languageProfile`)
      bundles.push(bundle)
      for (const document of bundle.documents) {
        documents.push({ ...document, userBundleId: bundle.userBundleId, split })
        validateDocument(document, artifact.splitRoot, findings)
      }
    }
    parents.push(...artifact.gold.parents.map((entry) => ({ ...entry, split, splitRoot: artifact.splitRoot })))
    groups.push(...artifact.gold.evidenceGroups.map((entry) => ({ ...entry, split })))
    units.push(...artifact.gold.evidenceUnits.map((entry) => ({ ...entry, split, splitRoot: artifact.splitRoot })))
    queries.push(...artifact.questions.queries.map((entry) => ({ ...entry, split })))
  }

  const bundleMap = uniqueMap(bundles, 'userBundleId', 'userBundleId', findings)
  const documentVersionMap = uniqueMap(documents, 'versionId', 'versionId', findings)
  const parentMap = uniqueMap(parents, 'parentId', 'parentId', findings)
  const groupMap = uniqueMap(groups, 'groupId', 'evidence group ID', findings)
  const unitMap = uniqueMap(units, 'evidenceUnitId', 'evidence ID', findings)
  uniqueMap(queries, 'queryId', 'query ID', findings)

  for (const parent of parents) {
    const bundle = bundleMap.get(parent.userBundleId)
    const document = documentVersionMap.get(parent.versionId)
    record(findings, Boolean(bundle), `${parent.parentId} references a missing user bundle`)
    record(findings, Boolean(document), `${parent.parentId} references a missing document version`)
    if (!bundle || !document) continue
    record(findings, document.userBundleId === parent.userBundleId, `${parent.parentId} and document belong to different user bundles`)
    record(findings, document.documentId === parent.documentId, `${parent.parentId} documentId/versionId mismatch`)
    validateSpan(parent.sourceSpan, parent, document, parent.splitRoot, findings)
  }

  for (const group of groups) {
    record(findings, group.evidenceUnitIds.length > 0, `${group.groupId} has no evidence units`)
    for (const unitId of group.evidenceUnitIds) {
      const unit = unitMap.get(unitId)
      record(findings, Boolean(unit), `${group.groupId} references missing evidence ${unitId}`)
      if (unit) record(findings, unit.groupId === group.groupId && unit.userBundleId === group.userBundleId, `${group.groupId}/${unitId} cross-bundle or group mismatch`)
    }
  }

  for (const unit of units) {
    const parent = parentMap.get(unit.parentId)
    const group = groupMap.get(unit.groupId)
    const document = documentVersionMap.get(unit.versionId)
    record(findings, Boolean(parent), `${unit.evidenceUnitId} references missing parent ${unit.parentId}`)
    record(findings, Boolean(group), `${unit.evidenceUnitId} references missing group ${unit.groupId}`)
    record(findings, Boolean(document), `${unit.evidenceUnitId} references missing document/version/span source`)
    if (!parent || !group || !document) continue
    record(findings, unit.userBundleId === parent.userBundleId && unit.userBundleId === group.userBundleId && unit.userBundleId === document.userBundleId, `${unit.evidenceUnitId} child/parent/group/document cross-user mismatch`)
    record(findings, unit.documentId === parent.documentId && unit.versionId === parent.versionId, `${unit.evidenceUnitId} child/parent document version mismatch`)
    const spanMap = uniqueMap(unit.sourceSpans, 'spanId', `${unit.evidenceUnitId} spanId`, findings)
    record(findings, spanMap.has(unit.primarySpanId), `${unit.evidenceUnitId} primarySpanId is missing`)
    record(findings, unit.contextSpanIds.every((id) => spanMap.has(id) && id !== unit.primarySpanId), `${unit.evidenceUnitId} contextSpanIds are invalid`)
    for (const span of unit.sourceSpans) {
      validateSpan(span, parent, document, unit.splitRoot, findings)
      record(findings, span.charStart >= parent.sourceSpan.charStart && span.charEnd <= parent.sourceSpan.charEnd, `${unit.evidenceUnitId}/${span.spanId} escapes its Evidence Parent`)
    }
    const sourceText = unit.sourceSpans.map((span) => span.text).join(' ')
    for (const entity of unit.entities) {
      record(findings, entity.surfaceForms.every((surface) => sourceText.includes(surface)), `${unit.evidenceUnitId} entity surface is absent from source span: ${entity.canonicalValue}`)
    }
    for (const observed of unit.numerics) {
      record(findings, sourceText.includes(observed.sourceSurface), `${unit.evidenceUnitId} numeric sourceSurface is absent from source span`)
      record(findings, parseObservedNumber(observed.sourceSurface) === observed.normalizedValue, `${unit.evidenceUnitId} numeric normalizedValue does not match sourceSurface`)
      record(findings, observed.qualifierTokens.every((token) => normalize(sourceText).includes(normalize(token))), `${unit.evidenceUnitId} numeric qualifier is absent from source span`)
    }
    for (const observed of unit.dates) {
      record(findings, sourceText.includes(observed.sourceSurface), `${unit.evidenceUnitId} date sourceSurface is absent from source span`)
      record(findings, observed.start <= observed.end, `${unit.evidenceUnitId} date range is inverted`)
    }
  }

  for (const query of queries) {
    const bundle = bundleMap.get(query.userBundleId)
    record(findings, Boolean(bundle), `${query.queryId} references missing user bundle`)
    if (!bundle) continue
    record(findings, query.split === bundle.split, `${query.queryId} is stored under a different split than its user bundle`)
    record(findings, query.normalizedQuery === normalize(query.query), `${query.queryId} normalizedQuery mismatch`)
    record(findings, query.categories.every((category) => valueInEnum(schema, 'queryCategory', category)), `${query.queryId} has an invalid category`)
    record(findings, valueInEnum(schema, 'answerability', query.answerability), `${query.queryId} has invalid answerability`)
    for (const aspectEntry of query.aspects) {
      record(findings, valueInEnum(schema, 'answerability', aspectEntry.answerability), `${query.queryId}/${aspectEntry.aspectId} has invalid answerability`)
      for (const expected of aspectEntry.expectedEvidence) {
        const unit = unitMap.get(expected.evidenceUnitId)
        record(findings, Boolean(unit), `${query.queryId} expected evidence references missing unit ${expected.evidenceUnitId}`)
        if (!unit) continue
        record(findings, unit.userBundleId === query.userBundleId, `${query.queryId} expected evidence references another user bundle: ${expected.evidenceUnitId}`)
        record(findings, valueInEnum(schema, 'supportRelation', expected.supportRelation), `${query.queryId} has invalid support relation`)
        if (expected.supportRelation === 'DIRECT_SUPPORT') {
          const document = documentVersionMap.get(unit.versionId)
          record(findings, document?.active === true, `${query.queryId} DIRECT_SUPPORT references an inactive or missing version`)
        }
      }
    }
    for (const exclusion of query.safetyExclusions) {
      const unit = unitMap.get(exclusion.evidenceUnitId)
      record(findings, Boolean(unit), `${query.queryId} safety exclusion references missing unit ${exclusion.evidenceUnitId}`)
      if (!unit) continue
      const document = documentVersionMap.get(unit.versionId)
      if (exclusion.reason === 'OWNER_LEAKAGE' || exclusion.reason === 'UNAUTHORIZED_SOURCE_EXPOSURE') record(findings, unit.userBundleId !== query.userBundleId, `${query.queryId} ${exclusion.reason} must reference another owner bundle`)
      if (exclusion.reason === 'INACTIVE_VERSION_LEAKAGE' || exclusion.reason === 'WRONG_VERSION_LEAKAGE') record(findings, unit.userBundleId === query.userBundleId && document?.active === false, `${query.queryId} ${exclusion.reason} must reference an inactive same-owner version`)
    }
    validateAnswerability(query, unitMap, findings)
  }

  const dataset = { bundles, documents, parents, groups, units, queries, bundleMap, lineage }
  validateLineage(dataset, findings)
  findRuntimeGoldContamination({ lineage, gold: [...artifacts.values()].map((entry) => entry.gold), questions: [...artifacts.values()].map((entry) => entry.questions) }, 'dataset', findings)
  validateCounts(loaded, dataset, findings)
  if (verifyManifests) validateManifestCoverage(root, loaded, findings)
  if (findings.length > 0) throw new DatasetValidationError(findings)
  return {
    status: loaded.overallManifest.status,
    counts: loaded.overallManifest.counts,
    distributions: loaded.overallManifest.distributions,
    combinedSha256: loaded.overallManifest.combinedSha256,
    sealedAt: loaded.overallManifest.sealedAt,
    sealedFinalSearchExecuted: artifacts.get('SEALED_FINAL_TEST').manifest.searchExecuted,
  }
}

function isMain() {
  return process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url
}

if (isMain()) {
  const result = validateBenchmark()
  console.log(JSON.stringify(result, null, 2))
}
