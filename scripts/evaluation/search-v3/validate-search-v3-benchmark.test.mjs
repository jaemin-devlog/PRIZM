import assert from 'node:assert/strict'
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import test from 'node:test'

import {
  DatasetValidationError,
  DEFAULT_DATASET_ROOT,
  validateBenchmark,
} from './validate-search-v3-benchmark.mjs'

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'))
const writeJson = (path, value) => writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`)

function withDataset(t) {
  const temporaryRoot = mkdtempSync(join(tmpdir(), 'prizm-search-v3-'))
  const datasetRoot = resolve(temporaryRoot, 'search-v3-evaluation')
  cpSync(DEFAULT_DATASET_ROOT, datasetRoot, { recursive: true })
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }))
  return datasetRoot
}

function expectFinding(root, pattern, options = { verifyManifests: false }) {
  assert.throws(
    () => validateBenchmark({ root, ...options }),
    (error) => error instanceof DatasetValidationError && error.findings.some((finding) => pattern.test(finding)),
  )
}

test('accepts the frozen seed and reports unopened SEALED FINAL', () => {
  const result = validateBenchmark()
  assert.equal(result.status, 'FRESH_BENCHMARK_SEED_FROZEN')
  assert.equal(result.counts.userBundles, 7)
  assert.equal(result.sealedFinalSearchExecuted, false)
})

test('rejects NOT_SUPPORTED with DIRECT_SUPPORT', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/questions.json')
  const artifact = readJson(path)
  artifact.queries.find((query) => query.queryId === 'SV3-U01-Q03').aspects[0].expectedEvidence[0].supportRelation = 'DIRECT_SUPPORT'
  writeJson(path, artifact)
  expectFinding(root, /NOT_SUPPORTED but has DIRECT_SUPPORT/u)
})

test('rejects SUPPORTED without DIRECT_SUPPORT', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/questions.json')
  const artifact = readJson(path)
  artifact.queries.find((query) => query.queryId === 'SV3-U01-Q01').aspects[0].expectedEvidence[0].supportRelation = 'RELATED'
  writeJson(path, artifact)
  expectFinding(root, /SUPPORTED without DIRECT_SUPPORT/u)
})

test('rejects expected evidence from another user bundle', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/questions.json')
  const artifact = readJson(path)
  artifact.queries.find((query) => query.queryId === 'SV3-U01-Q01').aspects[0].expectedEvidence[0].evidenceUnitId = 'SV3-U04-P01-E01'
  writeJson(path, artifact)
  expectFinding(root, /expected evidence references another user bundle/u)
})

test('rejects child and parent from different user bundles', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'calibration/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits.find((unit) => unit.evidenceUnitId === 'SV3-U02-P01-E01').userBundleId = 'SV3-U03'
  writeJson(path, artifact)
  expectFinding(root, /child\/parent\/group\/document cross-user mismatch/u)
})

test('rejects a nonexistent document version reference', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'calibration/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits[0].versionId = 'SV3-U02-D01-V99'
  writeJson(path, artifact)
  expectFinding(root, /references missing document\/version\/span source/u)
})

test('rejects cross-parent multi-span direct evidence', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/gold-evidence.json')
  const artifact = readJson(path)
  const unit = artifact.evidenceUnits.find((entry) => entry.evidenceUnitId === 'SV3-U01-P02-E01')
  unit.sourceSpans[1].parentId = 'SV3-U01-P01'
  writeJson(path, artifact)
  expectFinding(root, /parent does not match SV3-U01-P02/u)
})

test('rejects numeric observations not grounded in the source surface', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits.find((entry) => entry.evidenceUnitId === 'SV3-U01-P01-E02').numerics[0].normalizedValue = 999
  writeJson(path, artifact)
  expectFinding(root, /numeric normalizedValue does not match sourceSurface/u)
})

test('rejects entity surfaces absent from source text', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'calibration/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits.find((entry) => entry.evidenceUnitId === 'SV3-U02-P01-E01').entities[0].surfaceForms = ['WCAG 9.9 ZZ']
  writeJson(path, artifact)
  expectFinding(root, /entity surface is absent from source span/u)
})

test('rejects duplicate query IDs', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/questions.json')
  const artifact = readJson(path)
  artifact.queries.push(structuredClone(artifact.queries[0]))
  writeJson(path, artifact)
  expectFinding(root, /duplicate query ID/u)
})

test('rejects duplicate evidence IDs', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits.push(structuredClone(artifact.evidenceUnits[0]))
  writeJson(path, artifact)
  expectFinding(root, /duplicate evidence ID/u)
})

test('rejects template family leakage across splits', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'sealed-final/corpus.json')
  const artifact = readJson(path)
  artifact.userBundles[0].templateFamilyId = 'SV3-TEMPLATE-BACKEND-01'
  writeJson(path, artifact)
  expectFinding(root, /templateFamilyId leakage across DEV\/SEALED_FINAL_TEST/u)
})

test('rejects generator seed lineage leakage across splits', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'sealed-final/corpus.json')
  const artifact = readJson(path)
  artifact.userBundles[0].documents[0].provenance.generatorSeedId = 'SV3-SEED-BACKEND-8F1C'
  writeJson(path, artifact)
  expectFinding(root, /generatorLineage leakage across DEV\/SEALED_FINAL_TEST/u)
})

test('rejects normalized identical queries across splits', (t) => {
  const root = withDataset(t)
  const dev = readJson(resolve(root, 'dev/questions.json')).queries[0]
  const path = resolve(root, 'sealed-final/questions.json')
  const artifact = readJson(path)
  artifact.queries[0].query = dev.query
  artifact.queries[0].normalizedQuery = dev.normalizedQuery
  writeJson(path, artifact)
  expectFinding(root, /normalizedQuery leakage across DEV\/SEALED_FINAL_TEST/u)
})

test('rejects source fact leakage across splits', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'sealed-final/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits[0].sourceFactId = 'SV3-FACT-U01-PAYGW'
  writeJson(path, artifact)
  expectFinding(root, /sourceFactId leakage across DEV\/SEALED_FINAL_TEST/u)
})

test('rejects runtime chunk IDs in gold artifacts', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/questions.json')
  const artifact = readJson(path)
  artifact.queries[0].expectedChunkId = 1234
  writeJson(path, artifact)
  expectFinding(root, /forbidden runtime database identifier/u)
})

test('rejects a mutated source span', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'dev/gold-evidence.json')
  const artifact = readJson(path)
  artifact.evidenceUnits[0].sourceSpans[0].charEnd -= 1
  writeJson(path, artifact)
  expectFinding(root, /source span text mismatch/u)
})

test('detects a sealed-final file mutation through SHA-256', (t) => {
  const root = withDataset(t)
  const path = resolve(root, 'sealed-final/documents/sv3-u05-planning-narrative-v01.txt')
  writeFileSync(path, `${readFileSync(path, 'utf8')}mutated\n`)
  expectFinding(root, /SHA-256 mismatch/u, { verifyManifests: true })
})
