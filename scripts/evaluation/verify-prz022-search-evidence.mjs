import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const root = process.cwd()
const output = process.argv[2]
if (!output) throw new Error('Usage: node verify-prz022-search-evidence.mjs <output.json>')

const inputs = {
  P0: 'specs/PRZ-016-search-performance-v2/p0-benchmark/baseline-results.json',
  P4: 'specs/PRZ-016-search-performance-v2/p4-evidence-localization/benchmark-results.json',
  P5: 'specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-results.json',
  'P7-B': 'specs/PRZ-016-search-performance-v2/p7-b-independent-generalization/evaluated-results.json',
}

const sha256 = value => createHash('sha256').update(value).digest('hex')
const readJson = async file => JSON.parse(await readFile(path.join(root, file), 'utf8'))
const ratio = (n, d) => d === 0 ? 0 : n / d

function recompute(results) {
  const positives = results.filter(row => row.expected === 'EVIDENCE_EXISTS')
  const negatives = results.filter(row => row.expected !== 'EVIDENCE_EXISTS')
  return {
    totalQueries: results.length,
    positiveQueries: positives.length,
    negativeQueries: negatives.length,
    top1Accuracy: ratio(positives.filter(row => row.correctRank === 1).length, positives.length),
    recallAt3: ratio(positives.filter(row => row.correctRank && row.correctRank <= 3).length, positives.length),
    recallAt5: ratio(positives.filter(row => row.correctRank && row.correctRank <= 5).length, positives.length),
    mrrAt5: ratio(positives.reduce((sum, row) => sum + (row.correctRank && row.correctRank <= 5 ? 1 / row.correctRank : 0), 0), positives.length),
    negativeFalsePositiveRate: ratio(negatives.filter(row => row.falsePositive).length, negatives.length),
  }
}

function recomputeP7(results) {
  const positives = results.filter(row => row.polarity === 'POSITIVE')
  const negatives = results.filter(row => row.polarity === 'NEGATIVE')
  return {
    total: results.length,
    positive: positives.length,
    negative: negatives.length,
    top1: ratio(positives.filter(row => row.correctRank === 1).length, positives.length),
    recallAt3: ratio(positives.filter(row => row.correctRank && row.correctRank <= 3).length, positives.length),
    recallAt5: ratio(positives.filter(row => row.correctRank && row.correctRank <= 5).length, positives.length),
    mrrAt5: ratio(positives.reduce((sum, row) => sum + (row.correctRank && row.correctRank <= 5 ? 1 / row.correctRank : 0), 0), positives.length),
    negativeFpr: ratio(negatives.filter(row => row.falsePositive).length, negatives.length),
  }
}

function selectSummary(summary, phase) {
  const keys = phase === 'P7-B'
    ? ['total', 'positive', 'negative', 'top1', 'recallAt3', 'recallAt5', 'mrrAt5', 'negativeFpr']
    : ['totalQueries', 'positiveQueries', 'negativeQueries', 'top1Accuracy', 'recallAt3', 'recallAt5', 'mrrAt5', 'negativeFalsePositiveRate']
  return Object.fromEntries(keys.map(key => [key, summary[key]]))
}

const phases = {}
for (const [phase, file] of Object.entries(inputs)) {
  const raw = await readFile(path.join(root, file), 'utf8')
  const parsed = JSON.parse(raw)
  const computed = phase === 'P7-B' ? recomputeP7(parsed.evaluations) : recompute(parsed.results)
  const recorded = selectSummary(parsed.summary, phase)
  const consistent = JSON.stringify(computed) === JSON.stringify(recorded)
  if (!consistent) throw new Error(`${phase} raw metrics do not match recorded summary`)
  phases[phase] = { file, sha256: sha256(raw), recorded, recomputed: computed, consistent }
}

const p5FreezeFile = 'specs/PRZ-016-search-performance-v2/p5-final-holdout/freeze-record.json'
const p5Freeze = await readJson(p5FreezeFile)
const p5Dataset = await readFile(path.join(root, 'specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-dataset.json'))
const p5GroundTruth = await readFile(path.join(root, 'specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-ground-truth.json'))
const p7RawFreeze = await readJson('specs/PRZ-016-search-performance-v2/p7-b-independent-generalization/raw-freeze.json')
const p7Raw = await readFile(path.join(root, 'specs/PRZ-016-search-performance-v2/p7-b-independent-generalization/raw-results.json'))

const freezeChecks = {
  p5Dataset: sha256(p5Dataset) === p5Freeze.sha256.holdoutDataset,
  p5GroundTruth: sha256(p5GroundTruth) === p5Freeze.sha256.holdoutGroundTruth,
  p7Raw: sha256(p7Raw) === p7RawFreeze.rawResultsSha256,
}
if (Object.values(freezeChecks).some(value => !value)) throw new Error('Frozen input hash mismatch')

const productionRoot = path.join(root, 'src/main/java/com/prizm/search')
const productionFiles = [
  'service/SearchService.java',
  'repository/VectorSearchRepository.java',
  'profile/CompositeSearchProfile.java',
]
const productionText = (await Promise.all(productionFiles.map(file => readFile(path.join(productionRoot, file), 'utf8')))).join('\n')
const shadowNotPromoted = {
  postgresFts: !productionText.includes('tsvector') && !productionText.includes('ts_rank'),
  reciprocalRankFusion: !productionText.includes('ReciprocalRankFusion'),
  evidenceJudge: !productionText.includes('EvidenceJudge'),
  semanticNli: !productionText.includes('Nli'),
}
if (Object.values(shadowNotPromoted).some(value => !value)) throw new Error('A rejected shadow component appears in Production search')

const report = {
  schemaVersion: 1,
  baselineMain: '3af4db05f5f1b2d9802335de5eac9ad7b98555fa',
  generatedAt: new Date().toISOString(),
  scope: 'historical frozen raw-result consistency and current Production-path audit',
  currentAccuracyClaim: 'NOT_CLAIMED',
  phases,
  freezeChecks,
  shadowNotPromoted,
  limitation: 'P0/P4/P5/P7-B are frozen historical executions; this audit does not rerun Ollama retrieval on current main.',
  status: 'PASS',
}
await writeFile(path.join(root, output), `${JSON.stringify(report, null, 2)}\n`, 'utf8')
console.log(`PRZ-022 search evidence audit PASS: ${output}`)
