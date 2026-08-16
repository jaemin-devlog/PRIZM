import { readFile, writeFile } from "node:fs/promises";

const root = new URL("../../../", import.meta.url);
const outputDir = new URL("./", import.meta.url);
const dataset = JSON.parse(await readFile(new URL("../p0-benchmark/evaluation-dataset.json", import.meta.url), "utf8"));
const beforeBenchmark = JSON.parse(await readFile(new URL("../p2-evidence-reranking/benchmark-results.json", import.meta.url), "utf8"));
const envText = await readFile(new URL(".env", root), "utf8");
const env = Object.fromEntries(envText.split(/\r?\n/)
  .filter((line) => /^[A-Za-z_][A-Za-z0-9_]*=/.test(line))
  .map((line) => {
    const separator = line.indexOf("=");
    return [line.slice(0, separator), line.slice(separator + 1)];
  }));

const targetIds = ["B05", "B12", "B14", "C08", "C11", "A04", "E04"];
const generatedVariants = new Map([
  ["A04", ["Docker Compose 배포 환경 구축 경험"]],
  ["B05", ["실제 운영 환경에 배포해본 경험", "운영 환경 배포 환경 구축 경험"]],
  ["B12", ["실사용 서비스를 운영 경험은?", "실사용 서비스 운영 경험"]],
  ["B14", ["스프레드시트 엑셀에서 기존 데이터 갱신한 적 있어?", "스프레드시트 엑셀 기존 데이터 갱신 경험"]],
  ["C08", ["후보 상태가 변경 여부 확정 전 재검증한 경험은?", "확정 전 상태 재검증 경험"]],
  ["C11", ["업로드 파일을 웹 서버가 직접 서빙 경험은?", "웹 서버 파일 직접 서빙 경험"]],
  ["E04", [
    "GCP Docker Compose Nginx Spring Boot 서비스를 배포한 경험은?",
    "GCP Docker Compose Nginx Spring Boot 서비스 배포 환경 구축 경험은?",
  ]],
]);
const cases = targetIds.map((id) => dataset.queries.find((query) => query.id === id));
const beforeById = new Map(beforeBenchmark.results.map((result) => [result.id, result]));

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

const search = async (query) => {
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query }),
  });
  if (!response.ok) throw new Error(`${query} failed: HTTP ${response.status}`);
  return response.json();
};
const normalize = (value) => String(value ?? "").normalize("NFKC").toLocaleLowerCase("ko-KR")
  .replace(/,/g, "").replace(/\s+/g, " ");
const correctRank = (definition, body) => body.results.findIndex((result) => {
  const page = result.evidenceSourceIndex ?? result.sourceIndex;
  const evidence = normalize(`${result.snippet ?? ""}\n${result.content ?? ""}`);
  return result.documentTitle === definition.expectedDocument
    && definition.acceptablePages.includes(page)
    && definition.anchorAny.some((anchor) => evidence.includes(normalize(anchor)));
}) + 1;
const compact = (body) => ({
  state: body.state,
  chunks: body.results.map((result, index) => ({
    rank: index + 1,
    chunkId: result.chunkId,
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
    page: result.evidenceSourceIndex ?? result.sourceIndex,
    score: result.score,
    distance: result.distance,
  })),
});

const targetResults = [];
const allBodies = [];
for (const definition of cases) {
  const variants = generatedVariants.get(definition.id) ?? [];
  const variantResults = [];
  for (const variant of variants) {
    const body = await search(variant);
    allBodies.push(body);
    variantResults.push({ variant, correctRank: correctRank(definition, body), ...compact(body) });
  }
  const body = await search(definition.query);
  allBodies.push(body);
  const afterRank = correctRank(definition, body);
  const before = beforeById.get(definition.id);
  targetResults.push({
    id: definition.id,
    query: definition.query,
    before: { state: before.actualState, correctRank: before.correctRank },
    generatedVariants: variants,
    variantResults,
    after: { correctRank: afterRank, ...compact(body) },
    improved: before.correctRank == null && afterRank > 0,
    passed: afterRank > 0,
  });
}

const naturalQueries = [
  "AirConnect 프로젝트", "AirConnect에서 뭐했어?", "Springboot", "springboot", "Spring Boot",
  "SpringBoot를 활용한 경험", "Redis를 왜 사용했어?", "배포 경험 알려줘", "동시성 문제를 어떻게 해결했어?",
];
const numericQueries = [
  "4,400회 테스트", "675건 갱신", "2,329행 처리", "1,480건 선점", "1,654건 제외",
  "FOR UPDATE SKIP LOCKED", "19분 22초에서 11초", "1,252건 API 처리",
];
const nearMissQueries = ["4,401회 테스트", "676건 갱신", "2,330행 처리"];
const negativeQueries = [
  "GraphQL API 구현 경험", "Kubernetes 운영 경험", "Kafka 운영 경험", "결제 시스템 구현 경험",
  "AWS Lambda 경험", "Elasticsearch 운영 경험", "MongoDB 경험", "Terraform 경험", "Jenkins 경험",
  "RabbitMQ 경험", "gRPC 경험",
];
const regression = [];
for (const query of [...naturalQueries, ...numericQueries]) {
  const body = await search(query);
  allBodies.push(body);
  regression.push({ group: naturalQueries.includes(query) ? "natural" : "numeric", query,
    state: body.state, passed: body.results.length > 0 });
}
for (const query of nearMissQueries) {
  const body = await search(query);
  allBodies.push(body);
  regression.push({ group: "numeric-near-miss", query, state: body.state,
    passed: body.results.length === 0 });
}
for (const query of negativeQueries) {
  const body = await search(query);
  allBodies.push(body);
  regression.push({ group: "strong-negative", query, state: body.state,
    passed: body.results.length === 0 && ["NO_RELEVANT_RESULTS", "NO_EVIDENCE"].includes(body.state) });
}

const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) throw new Error(`Document listing failed: HTTP ${documentsResponse.status}`);
const documents = await documentsResponse.json();
const activeVersions = new Map(documents.map((document) => [document.documentId, document.activeVersionId]));
const ownerAndActiveIsolationPreserved = allBodies.every((body) => body.results.every((result) =>
  activeVersions.get(result.documentId) === result.documentVersionId));
const summary = {
  authenticatedRole: login.user.role,
  targetPassed: targetResults.filter((result) => result.passed).length,
  targetImproved: targetResults.filter((result) => result.improved).length,
  focusedGatePassed: targetResults.filter((result) => result.improved).length >= 4,
  regressionChecks: regression.length,
  regressionFailures: regression.filter((result) => !result.passed).length,
  ownerAndActiveIsolationPreserved,
};

await writeFile(new URL("focused-results.json", outputDir), `${JSON.stringify({
  executedAt: new Date().toISOString(), summary, targetResults, regression,
}, null, 2)}\n`, "utf8");
console.log(targetResults.map((result) => `${result.id}: ${result.before.state}/${result.before.correctRank ?? "-"} -> rank ${result.after.correctRank || "-"}`).join("\n"));
console.log(JSON.stringify(summary, null, 2));
if (!summary.focusedGatePassed || summary.regressionFailures > 0 || !ownerAndActiveIsolationPreserved) {
  process.exitCode = 1;
}
