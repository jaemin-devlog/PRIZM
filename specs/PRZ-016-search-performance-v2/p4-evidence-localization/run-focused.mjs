import { readFile, writeFile } from "node:fs/promises";

const root = new URL("../../../", import.meta.url);
const outputDir = new URL("./", import.meta.url);
const dataset = JSON.parse(await readFile(new URL("../p0-benchmark/evaluation-dataset.json", import.meta.url), "utf8"));
const before = JSON.parse(await readFile(new URL("../p3-query-understanding/benchmark-results.json", import.meta.url), "utf8"));
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
if (login.user?.role !== "USER" || !login.accessToken) throw new Error("USER token required");
const headers = { Authorization: `Bearer ${login.accessToken}`, "Content-Type": "application/json" };

const normalize = (value) => String(value ?? "").normalize("NFKC").toLocaleLowerCase("ko-KR")
  .replace(/,/g, "").replace(/\s+/g, " ");
const search = async (query) => {
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query }),
  });
  if (!response.ok) throw new Error(`${query} failed: HTTP ${response.status}`);
  return response.json();
};
const groundTruthRank = (definition, body) => body.results.findIndex((result) => {
  const evidencePage = result.evidenceSourceIndex ?? result.sourceIndex;
  const searchable = normalize(`${result.snippet ?? ""}\n${result.content ?? ""}`);
  return result.documentTitle === definition.expectedDocument
    && definition.acceptablePages.includes(evidencePage)
    && definition.anchorAny.some((anchor) => searchable.includes(normalize(anchor)));
}) + 1;
const compact = (body) => ({
  state: body.state,
  results: body.results.map((result, index) => ({
    rank: index + 1,
    chunkId: result.chunkId,
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
    documentTitle: result.documentTitle,
    sourceIndex: result.sourceIndex,
    evidenceChunkId: result.evidenceChunkId,
    evidenceSourceIndex: result.evidenceSourceIndex,
    score: result.score,
    distance: result.distance,
    snippet: result.snippet,
  })),
});

const targetIds = ["B02", "B04", "C06", "E07", "B05"];
const regressionIds = ["A01", "A03", "E01", "B14", "C08", "A04", "E04", "D05", "E06"];
const targetResults = [];
const allBodies = [];
for (const id of [...targetIds, ...regressionIds]) {
  const definition = dataset.queries.find((query) => query.id === id);
  const body = await search(definition.query);
  allBodies.push(body);
  const rank = groundTruthRank(definition, body);
  const beforeResult = before.results.find((result) => result.id === id);
  const beforeRanking = (beforeResult?.top5 ?? []).map((result) => ({
    chunkId: result.chunkId,
    score: result.score,
    distance: result.distance,
  }));
  const afterRanking = body.results.map((result) => ({
    chunkId: result.chunkId,
    score: result.score,
    distance: result.distance,
  }));
  targetResults.push({
    id,
    group: targetIds.includes(id) ? "target" : "regression",
    query: definition.query,
    beforeCorrectRank: beforeResult?.correctRank ?? null,
    afterCorrectRank: rank || null,
    rankingContractPreserved: JSON.stringify(beforeRanking) === JSON.stringify(afterRanking),
    ...compact(body),
  });
}

const numericQueries = [
  "4,400회 테스트", "675건 갱신", "2,329행 처리", "1,480건 선점", "1,654건 제외",
];
const nearMissQueries = ["4,401회 테스트", "676건 갱신", "2,330행 처리"];
const strongNegativeQueries = [
  "GraphQL API 구현 경험", "Kubernetes 운영 경험", "Kafka 운영 경험", "결제 시스템 구현 경험",
  "AWS Lambda 경험", "Elasticsearch 운영 경험", "MongoDB 경험", "Terraform 경험", "Jenkins 경험",
  "RabbitMQ 경험", "gRPC 경험",
];
const guards = [];
for (const query of numericQueries) {
  const body = await search(query);
  allBodies.push(body);
  guards.push({ group: "numeric", query, state: body.state, passed: body.results.length > 0 });
}
for (const query of nearMissQueries) {
  const body = await search(query);
  allBodies.push(body);
  guards.push({ group: "near-miss", query, state: body.state, passed: body.results.length === 0 });
}
for (const query of strongNegativeQueries) {
  const body = await search(query);
  allBodies.push(body);
  guards.push({
    group: "strong-negative",
    query,
    state: body.state,
    passed: body.results.length === 0 && ["NO_RELEVANT_RESULTS", "NO_EVIDENCE"].includes(body.state),
  });
}

const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) throw new Error(`Document listing failed: HTTP ${documentsResponse.status}`);
const documents = await documentsResponse.json();
const activeVersions = new Map(documents.map((document) => [document.documentId, document.activeVersionId]));
const activeIsolationPreserved = allBodies.every((body) => body.results.every((result) =>
  activeVersions.get(result.documentId) === result.documentVersionId));
const targetById = new Map(targetResults.map((result) => [result.id, result]));
const localizationPassed = ["B02", "B04", "C06"].every((id) => targetById.get(id).afterCorrectRank === 1);
const regressionPassed = regressionIds.every((id) => targetById.get(id).afterCorrectRank === 1);
const candidateContractPreserved = targetResults.every((result) => result.rankingContractPreserved);
const summary = {
  authenticatedRole: login.user.role,
  localizationPassed,
  regressionPassed,
  guardChecks: guards.length,
  guardFailures: guards.filter((guard) => !guard.passed).length,
  candidateContractPreserved,
  activeIsolationPreserved,
  focusedGatePassed: localizationPassed && regressionPassed
    && guards.every((guard) => guard.passed)
    && candidateContractPreserved && activeIsolationPreserved,
};

await writeFile(new URL("focused-results.json", outputDir), `${JSON.stringify({
  executedAt: new Date().toISOString(), summary, targetResults, guards,
}, null, 2)}\n`, "utf8");
console.log(targetResults.map((result) =>
  `${result.id}: ${result.beforeCorrectRank ?? "-"} -> ${result.afterCorrectRank ?? "-"}; contract=${result.rankingContractPreserved}`,
).join("\n"));
console.log(JSON.stringify(summary, null, 2));
if (!summary.focusedGatePassed) process.exitCode = 1;
