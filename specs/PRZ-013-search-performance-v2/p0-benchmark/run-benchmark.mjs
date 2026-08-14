import { readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const root = new URL("../../../", import.meta.url);
const benchmarkDir = new URL("./", import.meta.url);
const dataset = JSON.parse(await readFile(new URL("evaluation-dataset.json", benchmarkDir), "utf8"));
const envText = await readFile(new URL(".env", root), "utf8");
const env = Object.fromEntries(
  envText
    .split(/\r?\n/)
    .filter((line) => /^[A-Za-z_][A-Za-z0-9_]*=/.test(line))
    .map((line) => {
      const index = line.indexOf("=");
      return [line.slice(0, index), line.slice(index + 1)];
    }),
);

const baseUrl = "http://127.0.0.1:15174";
const loginResponse = await fetch(`${baseUrl}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    email: env.PRIZM_BOOTSTRAP_DEMO_USER_EMAIL,
    password: env.PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD,
  }),
});
if (!loginResponse.ok) {
  throw new Error(`Normal USER login failed: HTTP ${loginResponse.status}`);
}
const login = await loginResponse.json();
if (login.user?.role !== "USER" || !login.accessToken) {
  throw new Error("Benchmark requires an authenticated USER token.");
}
const headers = {
  Authorization: `Bearer ${login.accessToken}`,
  "Content-Type": "application/json",
};

const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) {
  throw new Error(`Owner-scoped document listing failed: HTTP ${documentsResponse.status}`);
}
const documents = await documentsResponse.json();
const expectedDocuments = dataset.documents.map((title) =>
  documents.find((document) => document.title === title),
);
if (expectedDocuments.some((document) => !document)) {
  throw new Error("The fixed benchmark documents were not found for the authenticated USER.");
}
if (
  expectedDocuments.some(
    (document) =>
      document.latestProcessingStatus !== "COMPLETED" ||
      document.activeVersionId !== document.latestVersionId,
  )
) {
  throw new Error("The fixed benchmark documents must have COMPLETED ACTIVE versions.");
}

const normalize = (value) =>
  String(value ?? "")
    .normalize("NFKC")
    .toLocaleLowerCase("ko-KR")
    .replace(/\s+/g, " ");

function matchGroundTruth(query, result) {
  if (query.expected !== "EVIDENCE_EXISTS") return false;
  if (result.documentTitle !== query.expectedDocument) return false;
  const evidencePage = result.evidenceSourceIndex ?? result.sourceIndex;
  if (!query.acceptablePages.includes(evidencePage)) return false;
  const searchable = normalize(`${result.snippet ?? ""}\n${result.content ?? ""}`);
  return query.anchorAny.some((anchor) => searchable.includes(normalize(anchor)));
}

function classifyFailure(query, response, correctRank) {
  if (query.expected === "NO_EVIDENCE") return "FALSE_POSITIVE";
  if (correctRank != null && correctRank > 1) return "RANKING";
  if (query.category === "NUMERIC_IDENTIFIER") return "NUMERIC_IDENTIFIER";
  const expectedDocumentReturned = response.results.some(
    (result) => result.documentTitle === query.expectedDocument,
  );
  if (expectedDocumentReturned) return "EVIDENCE_LOCALIZATION";
  if (query.category === "NATURAL_VARIATION" || query.category === "INDIRECT_PROBLEM") {
    return "QUERY_UNDERSTANDING";
  }
  return "CANDIDATE_RECALL";
}

const results = [];
for (const [index, query] of dataset.queries.entries()) {
  const started = performance.now();
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query: query.query }),
  });
  const latencyMs = performance.now() - started;
  if (!response.ok) {
    throw new Error(`${query.id} search failed: HTTP ${response.status}`);
  }
  const body = await response.json();
  const ranked = body.results.map((result, resultIndex) => ({
    rank: resultIndex + 1,
    chunkId: result.chunkId,
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
    documentTitle: result.documentTitle,
    sourceType: result.sourceType,
    sourceIndex: result.sourceIndex,
    sourceLabel: result.sourceLabel,
    evidenceChunkId: result.evidenceChunkId,
    evidenceSourceType: result.evidenceSourceType,
    evidenceSourceIndex: result.evidenceSourceIndex,
    evidenceSourceLabel: result.evidenceSourceLabel,
    score: result.score,
    distance: result.distance,
    groundTruthMatch: matchGroundTruth(query, result),
  }));
  const correct = ranked.find((result) => result.groundTruthMatch);
  const falsePositive = query.expected === "NO_EVIDENCE" && body.results.length > 0;
  const passed =
    query.expected === "EVIDENCE_EXISTS"
      ? correct?.rank === 1
      : !falsePositive && ["NO_RELEVANT_RESULTS", "NO_EVIDENCE"].includes(body.state);
  const failureCategory = passed ? null : classifyFailure(query, body, correct?.rank ?? null);
  results.push({
    id: query.id,
    category: query.category,
    query: query.query,
    expected: query.expected,
    expectedDocument: query.expectedDocument ?? null,
    acceptablePages: query.acceptablePages ?? [],
    actualState: body.state,
    latencyMs: Number(latencyMs.toFixed(3)),
    correctRank: correct?.rank ?? null,
    top1: ranked.slice(0, 1),
    top3: ranked.slice(0, 3),
    top5: ranked.slice(0, 5),
    falsePositive,
    passed,
    failureCategory,
  });
  process.stdout.write(`${String(index + 1).padStart(2, "0")}/${dataset.queries.length} ${query.id} ${body.state} ${latencyMs.toFixed(0)}ms\n`);
}

const positive = results.filter((result) => result.expected === "EVIDENCE_EXISTS");
const negative = results.filter((result) => result.expected === "NO_EVIDENCE");
const latencies = results.map((result) => result.latencyMs).sort((a, b) => a - b);
const warmLatencies = results.slice(1).map((result) => result.latencyMs).sort((a, b) => a - b);
const average = (values) => values.reduce((sum, value) => sum + value, 0) / values.length;
const percentile = (values, ratio) => values[Math.max(0, Math.ceil(values.length * ratio) - 1)];
const summary = {
  totalQueries: results.length,
  positiveQueries: positive.length,
  negativeQueries: negative.length,
  top1Accuracy: positive.filter((result) => result.correctRank === 1).length / positive.length,
  recallAt3: positive.filter((result) => result.correctRank != null && result.correctRank <= 3).length / positive.length,
  recallAt5: positive.filter((result) => result.correctRank != null && result.correctRank <= 5).length / positive.length,
  mrrAt5:
    positive.reduce(
      (sum, result) => sum + (result.correctRank != null && result.correctRank <= 5 ? 1 / result.correctRank : 0),
      0,
    ) / positive.length,
  negativeFalsePositiveRate: negative.filter((result) => result.falsePositive).length / negative.length,
  latencyMs: {
    average: average(latencies),
    median: percentile(latencies, 0.5),
    p95: percentile(latencies, 0.95),
    max: latencies.at(-1),
    coldFirst: results[0].latencyMs,
    warmAverage: average(warmLatencies),
    warmMedian: percentile(warmLatencies, 0.5),
    warmP95: percentile(warmLatencies, 0.95),
  },
  failedQueries: results.filter((result) => !result.passed).length,
  failureCategories: Object.fromEntries(
    Object.entries(
      results
        .filter((result) => result.failureCategory)
        .reduce((counts, result) => {
          counts[result.failureCategory] = (counts[result.failureCategory] ?? 0) + 1;
          return counts;
        }, {}),
    ).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0])),
  ),
};

const output = {
  benchmarkId: dataset.benchmarkId,
  executedAt: new Date().toISOString(),
  productionCodeChangedByBenchmark: 0,
  authenticatedRole: login.user.role,
  fixedDocuments: expectedDocuments.map((document) => ({
    documentId: document.documentId,
    title: document.title,
    documentType: document.documentType,
    activeVersionId: document.activeVersionId,
    activeVersionStatus: document.activeVersionStatus,
    chunks: document.latestTotalChunks,
  })),
  summary,
  results,
};
await writeFile(
  new URL(process.argv[2] ?? "baseline-results.json", benchmarkDir),
  `${JSON.stringify(output, null, 2)}\n`,
  "utf8",
);
console.log(JSON.stringify(summary, null, 2));
