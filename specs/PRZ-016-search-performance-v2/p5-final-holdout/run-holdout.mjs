import { createHash } from "node:crypto";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { relative } from "node:path";
import { fileURLToPath } from "node:url";
import { performance } from "node:perf_hooks";

const outputDir = new URL("./", import.meta.url);
const root = new URL("../../../", import.meta.url);
const datasetUrl = new URL("holdout-dataset.json", outputDir);
const groundTruthUrl = new URL("holdout-ground-truth.json", outputDir);
const searchSourceUrl = new URL("src/main/java/com/prizm/search/", root);
const expectedHashes = {
  dataset: "4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa",
  groundTruth: "da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d",
  productionSearch: "32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31",
};

const sha256 = (value) => createHash("sha256").update(value).digest("hex");
const hashFile = async (url) => sha256(await readFile(url));
const listJavaFiles = async (directory) => {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const url = new URL(`${entry.name}${entry.isDirectory() ? "/" : ""}`, directory);
    if (entry.isDirectory()) return listJavaFiles(url);
    return entry.name.endsWith(".java") ? [url] : [];
  }));
  return nested.flat();
};
const hashProductionSearch = async () => {
  const rootPath = fileURLToPath(root);
  const files = (await listJavaFiles(searchSourceUrl)).sort((left, right) =>
    fileURLToPath(left).localeCompare(fileURLToPath(right)));
  const lines = await Promise.all(files.map(async (url) => {
    const path = relative(rootPath, fileURLToPath(url)).replaceAll("\\", "/");
    return `${await hashFile(url)}  ${path}`;
  }));
  return { fileCount: files.length, aggregate: sha256(lines.join("\n")) };
};
const assertFrozenInputs = async () => {
  const datasetHash = await hashFile(datasetUrl);
  const groundTruthHash = await hashFile(groundTruthUrl);
  const production = await hashProductionSearch();
  if (datasetHash !== expectedHashes.dataset) throw new Error("Frozen dataset hash mismatch");
  if (groundTruthHash !== expectedHashes.groundTruth) throw new Error("Frozen ground-truth hash mismatch");
  if (production.aggregate !== expectedHashes.productionSearch || production.fileCount !== 30) {
    throw new Error("Production search source changed after P5 freeze");
  }
  return { datasetHash, groundTruthHash, ...production };
};

const frozenBefore = await assertFrozenInputs();
const dataset = JSON.parse(await readFile(datasetUrl, "utf8"));
const groundTruth = JSON.parse(await readFile(groundTruthUrl, "utf8"));
if (dataset.status !== "FROZEN_PRE_SEARCH" || groundTruth.status !== "FROZEN_PRE_SEARCH") {
  throw new Error("Holdout inputs must be frozen before search");
}

const envText = await readFile(new URL(".env", root), "utf8");
const env = Object.fromEntries(envText.split(/\r?\n/)
  .filter((line) => /^[A-Za-z_][A-Za-z0-9_]*=/.test(line))
  .map((line) => {
    const separator = line.indexOf("=");
    return [line.slice(0, separator), line.slice(separator + 1)];
  }));
const baseUrl = "http://127.0.0.1:15174";
const loginResponse = await fetch(`${baseUrl}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    email: env.PRIZM_BOOTSTRAP_DEMO_USER_EMAIL,
    password: env.PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD,
  }),
});
if (!loginResponse.ok) throw new Error(`USER login failed: HTTP ${loginResponse.status}`);
const login = await loginResponse.json();
if (login.user?.role !== "USER" || login.user?.id !== groundTruth.ownerId || !login.accessToken) {
  throw new Error("Frozen owner USER token required");
}
const headers = { Authorization: `Bearer ${login.accessToken}`, "Content-Type": "application/json" };
const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) throw new Error(`Document listing failed: HTTP ${documentsResponse.status}`);
const documents = await documentsResponse.json();
const activeVersions = new Map(documents.map((document) => [document.documentId, document.activeVersionId]));
const allowedDocuments = new Set(groundTruth.activeCorpus.map((entry) => entry.documentId));

const normalize = (value) => String(value ?? "").normalize("NFKC").toLocaleLowerCase("ko-KR")
  .replace(/,/g, "").replace(/\s+/g, " ");
const percentile = (values, ratio) => {
  if (values.length === 0) return null;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)];
};
const average = (values) => values.reduce((sum, value) => sum + value, 0) / values.length;
const median = (values) => {
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
};

const gtById = new Map(groundTruth.positives.map((entry) => [entry.id, entry]));
const results = [];
for (const definition of dataset.queries) {
  const started = performance.now();
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query: definition.query }),
  });
  const latencyMs = performance.now() - started;
  if (!response.ok) throw new Error(`${definition.id} failed: HTTP ${response.status}`);
  const body = await response.json();
  const compactResults = body.results.map((result, index) => ({
    rank: index + 1,
    chunkId: result.chunkId,
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
    documentTitle: result.documentTitle,
    sourceIndex: result.sourceIndex,
    evidenceChunkId: result.evidenceChunkId,
    evidenceSourceIndex: result.evidenceSourceIndex,
    snippet: result.snippet,
    score: result.score,
    distance: result.distance,
  }));
  let correctRank = null;
  if (definition.expected === "EVIDENCE_EXISTS") {
    const positiveGt = gtById.get(definition.id);
    const index = body.results.findIndex((result) => positiveGt.acceptableEvidence.some((acceptable) => {
      const evidencePage = result.evidenceSourceIndex ?? result.sourceIndex;
      const evidenceChunk = result.evidenceChunkId ?? result.chunkId;
      const searchable = normalize(`${result.snippet ?? ""}\n${result.content ?? ""}`);
      return result.documentId === acceptable.documentId
        && result.documentVersionId === acceptable.versionId
        && acceptable.pages.includes(evidencePage)
        && acceptable.chunkIds.includes(evidenceChunk)
        && acceptable.anchorsAny.some((anchor) => searchable.includes(normalize(anchor)));
    }));
    if (index >= 0) correctRank = index + 1;
  }
  const falsePositive = definition.expected === "NO_RELEVANT_RESULTS" && body.results.length > 0;
  const passed = definition.expected === "EVIDENCE_EXISTS" ? correctRank !== null : !falsePositive;
  let failureCategory = null;
  if (!passed && falsePositive) {
    failureCategory = "FALSE_POSITIVE";
  } else if (!passed && correctRank !== null && correctRank > 1) {
    failureCategory = "RANKING";
  } else if (!passed && definition.category === "NUMERIC_IDENTIFIER") {
    failureCategory = "NUMERIC_IDENTIFIER";
  } else if (!passed) {
    const positiveGt = gtById.get(definition.id);
    const rightDocumentReturned = body.results.some((result) => positiveGt.acceptableEvidence.some((acceptable) =>
      result.documentId === acceptable.documentId && result.documentVersionId === acceptable.versionId));
    failureCategory = rightDocumentReturned ? "EVIDENCE_LOCALIZATION" : "CANDIDATE_RECALL";
  }
  results.push({
    id: definition.id,
    category: definition.category,
    query: definition.query,
    expected: definition.expected,
    state: body.state,
    latencyMs,
    correctRank,
    falsePositive,
    passed,
    failureCategory,
    results: compactResults,
  });
}

const frozenAfter = await assertFrozenInputs();
const positiveResults = results.filter((result) => result.expected === "EVIDENCE_EXISTS");
const negativeResults = results.filter((result) => result.expected === "NO_RELEVANT_RESULTS");
const latencies = results.map((result) => result.latencyMs);
const warmLatencies = latencies.slice(1);
const ownerIsolationPassed = results.every((evaluation) => evaluation.results.every((result) =>
  allowedDocuments.has(result.documentId)));
const activeIsolationPassed = results.every((evaluation) => evaluation.results.every((result) =>
  activeVersions.get(result.documentId) === result.documentVersionId));
const top1 = positiveResults.filter((result) => result.correctRank === 1).length / positiveResults.length;
const recallAt3 = positiveResults.filter((result) => result.correctRank !== null && result.correctRank <= 3).length
  / positiveResults.length;
const recallAt5 = positiveResults.filter((result) => result.correctRank !== null && result.correctRank <= 5).length
  / positiveResults.length;
const mrrAt5 = positiveResults.reduce((sum, result) =>
  sum + (result.correctRank !== null && result.correctRank <= 5 ? 1 / result.correctRank : 0), 0) / positiveResults.length;
const negativeFpr = negativeResults.filter((result) => result.falsePositive).length / negativeResults.length;
const failureTaxonomy = Object.fromEntries(Object.entries(Object.groupBy(
  results.filter((result) => !result.passed), (result) => result.failureCategory,
)).map(([category, entries]) => [category, entries.length]));
const summary = {
  totalQueries: results.length,
  positiveQueries: positiveResults.length,
  negativeQueries: negativeResults.length,
  passedQueries: results.filter((result) => result.passed).length,
  failedQueries: results.filter((result) => !result.passed).length,
  top1Accuracy: top1,
  recallAt3,
  recallAt5,
  mrrAt5,
  negativeFalsePositiveRate: negativeFpr,
  failureTaxonomy,
  ownerIsolationPassed,
  activeIsolationPassed,
  datasetModifiedAfterFreeze: frozenBefore.datasetHash !== frozenAfter.datasetHash,
  groundTruthModifiedAfterFreeze: frozenBefore.groundTruthHash !== frozenAfter.groundTruthHash,
  productionSearchModifiedAfterFreeze: frozenBefore.aggregate !== frozenAfter.aggregate,
  latencyMs: {
    average: average(latencies),
    warmAverage: average(warmLatencies),
    median: median(latencies),
    warmMedian: median(warmLatencies),
    p95: percentile(latencies, 0.95),
    warmP95: percentile(warmLatencies, 0.95),
    max: Math.max(...latencies),
    coldFirst: latencies[0],
  },
};
await writeFile(new URL("holdout-results.json", outputDir), `${JSON.stringify({
  // Historical frozen artifact label retained after the parent Spec moved to PRZ-016.
  phase: "PRZ-013-P5",
  executedAt: new Date().toISOString(),
  authenticatedUser: { id: login.user.id, role: login.user.role },
  frozenHashes: frozenAfter,
  summary,
  results,
}, null, 2)}\n`, "utf8");
console.log(JSON.stringify(summary, null, 2));
