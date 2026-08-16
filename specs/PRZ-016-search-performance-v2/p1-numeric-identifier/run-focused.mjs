import { mkdir, readFile, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const root = new URL("../../../", import.meta.url);
const outputDir = new URL("./", import.meta.url);
const dataset = JSON.parse(
  await readFile(new URL("../p0-benchmark/evaluation-dataset.json", import.meta.url), "utf8"),
);
const baseline = JSON.parse(
  await readFile(new URL("../p0-benchmark/baseline-results.json", import.meta.url), "utf8"),
);
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
if (!loginResponse.ok) throw new Error(`Normal USER login failed: HTTP ${loginResponse.status}`);
const login = await loginResponse.json();
if (login.user?.role !== "USER" || !login.accessToken) {
  throw new Error("Focused verification requires an authenticated USER token.");
}
const headers = {
  Authorization: `Bearer ${login.accessToken}`,
  "Content-Type": "application/json",
};

const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) throw new Error(`Document listing failed: HTTP ${documentsResponse.status}`);
const documents = await documentsResponse.json();
const activeVersions = new Map(
  documents
    .filter((document) => document.activeVersionId != null)
    .map((document) => [document.documentId, document.activeVersionId]),
);

const normalize = (value) =>
  String(value ?? "")
    .normalize("NFKC")
    .toLocaleLowerCase("ko-KR")
    .replace(/,/g, "")
    .replace(/\s+/g, " ");

function groundTruthMatch(query, result) {
  if (result.documentTitle !== query.expectedDocument) return false;
  const page = result.evidenceSourceIndex ?? result.sourceIndex;
  if (!query.acceptablePages.includes(page)) return false;
  const searchable = normalize(`${result.snippet ?? ""}\n${result.content ?? ""}`);
  return query.anchorAny.some((anchor) => searchable.includes(normalize(anchor)));
}

function contextualNumericMatch(query, result) {
  const anchors = [...query.matchAll(/(?<!\d)(\d[\d,]*(?:\.\d+)?)\s*(회|건|행|초|분|개|명|번|%|퍼센트)(?!\d)/g)];
  const searchable = normalize(result.snippet ?? "");
  return anchors.some(([, number, unit]) => {
    const escapedNumber = normalize(number).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    return new RegExp(`(^|[^0-9])${escapedNumber}(?![0-9])\\s*${unit}`).test(searchable);
  });
}

function sameAsBaseline(check, body) {
  const previous = baseline.results.find((result) => result.id === check.id);
  if (previous == null || previous.actualState !== body.state) return false;
  const expected = previous.top5;
  if (expected.length !== body.results.length) return false;
  return expected.every((result, index) => {
    const current = body.results[index];
    return result.chunkId === current.chunkId
      && result.score === current.score
      && result.distance === current.distance;
  });
}

async function search(query) {
  const started = performance.now();
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query }),
  });
  const latencyMs = performance.now() - started;
  if (!response.ok) throw new Error(`Search failed for ${query}: HTTP ${response.status}`);
  return { body: await response.json(), latencyMs: Number(latencyMs.toFixed(3)) };
}

const byId = new Map(dataset.queries.map((query) => [query.id, query]));
const checks = [
  ...["D01", "D02", "D03", "D07", "D08"].map((id) => ({ group: "numeric-rescue", ...byId.get(id) })),
  ...["D04", "D05", "D06"].map((id) => ({ group: "numeric-regression", ...byId.get(id) })),
  ...["A01", "A02", "A04", "D04", "A05"].map((id) => ({ group: "identifier-positive", ...byId.get(id) })),
  ...["F06", "F01", "F03"].map((id) => ({ group: "identifier-negative", ...byId.get(id) })),
  { id: "N01", group: "numeric-near-miss", query: "4,401회 테스트", expected: "NO_EVIDENCE" },
  { id: "N02", group: "numeric-near-miss", query: "676건 갱신", expected: "NO_EVIDENCE" },
  { id: "N03", group: "numeric-near-miss", query: "2,330행 처리", expected: "NO_EVIDENCE" },
];

const results = [];
for (const check of checks) {
  const { body, latencyMs } = await search(check.query);
  const activeBoundaryPreserved = body.results.every(
    (result) => activeVersions.get(result.documentId) === result.documentVersionId,
  );
  const correct =
    check.group.startsWith("numeric") && check.expected === "EVIDENCE_EXISTS"
      ? body.results.find((result) => contextualNumericMatch(check.query, result) || groundTruthMatch(check, result))
      : check.expected === "EVIDENCE_EXISTS"
        ? body.results.find((result) => groundTruthMatch(check, result))
        : null;
  const passed = check.group === "identifier-positive"
    ? sameAsBaseline(check, body) && activeBoundaryPreserved
    : check.expected === "EVIDENCE_EXISTS"
      ? correct != null && activeBoundaryPreserved
      : body.results.length === 0 && ["NO_RELEVANT_RESULTS", "NO_EVIDENCE"].includes(body.state);
  const top = body.results[0];
  const row = {
    id: check.id,
    group: check.group,
    query: check.query,
    expected: check.expected,
    passed,
    state: body.state,
    resultCount: body.results.length,
    matchedRank: correct == null ? null : body.results.indexOf(correct) + 1,
    topDocument: top?.documentTitle ?? null,
    topPage: top == null ? null : (top.evidenceSourceIndex ?? top.sourceIndex),
    topChunkId: top?.chunkId ?? null,
    topEvidenceChunkId: top?.evidenceChunkId ?? null,
    topScore: top?.score ?? null,
    topDistance: top?.distance ?? null,
    activeBoundaryPreserved,
    latencyMs,
  };
  results.push(row);
  console.log(`${passed ? "PASS" : "FAIL"} ${check.group} ${check.query} -> ${body.state} page=${row.topPage ?? "-"}`);
}

const ownerIsolationPreserved = results.every((result) => result.activeBoundaryPreserved);
const summary = {
  authenticatedRole: login.user.role,
  totalChecks: results.length,
  passedChecks: results.filter((result) => result.passed).length,
  failedChecks: results.filter((result) => !result.passed).length,
  ownerAndActiveIsolationPreserved: ownerIsolationPreserved,
  focusedGatePassed: results.every((result) => result.passed) && ownerIsolationPreserved,
};

await mkdir(outputDir, { recursive: true });
await writeFile(
  new URL("focused-results.json", outputDir),
  `${JSON.stringify({ executedAt: new Date().toISOString(), summary, results }, null, 2)}\n`,
  "utf8",
);
console.log(JSON.stringify(summary, null, 2));
if (!summary.focusedGatePassed) process.exitCode = 1;
