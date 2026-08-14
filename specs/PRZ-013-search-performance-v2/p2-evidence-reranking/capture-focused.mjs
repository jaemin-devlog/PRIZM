import { mkdir, readFile, writeFile } from "node:fs/promises";

const root = new URL("../../../", import.meta.url);
const outputDir = new URL("./", import.meta.url);
const envText = await readFile(new URL(".env", root), "utf8");
const env = Object.fromEntries(
  envText
    .split(/\r?\n/)
    .filter((line) => /^[A-Za-z_][A-Za-z0-9_]*=/.test(line))
    .map((line) => {
      const separator = line.indexOf("=");
      return [line.slice(0, separator), line.slice(separator + 1)];
    }),
);
const cases = [
  ["R01", "Spring Boot 백엔드 경험", "정재민 신입 백엔드 이력서", 2],
  ["R02", "동시성 처리 경험", "정재민 백엔드 포트폴리오", 3],
  ["R03", "TourAPI 연동 경험", "정재민 백엔드 포트폴리오", 4],
  ["R04", "여러 요청이 동시에 들어오면 어떻게 처리했어?", "정재민 백엔드 포트폴리오", 3],
  ["R05", "외부 호출 대기 시간이 누적되는 문제를 줄인 방법은?", "정재민 백엔드 포트폴리오", 4],
  ["R06", "Redis와 DB lock을 같이 사용해서 동시성 문제를 해결한 경험이 있어?", "정재민 백엔드 포트폴리오", 2],
  ["G13", "관광지 데이터를 가져오는 작업이 오래 걸렸을 때 뭘 했어?", "정재민 백엔드 포트폴리오", 4],
  ["G16", "서버 재시작 때 여러 서비스를 따로 관리하던 부담을 줄인 경험은?", "정재민 신입 백엔드 이력서", 2],
];

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
const encode = (value) => Buffer.from(String(value ?? ""), "utf8").toString("base64");
const rows = [];
const results = [];
const search = async (query) => {
  const response = await fetch(`${baseUrl}/api/v2/career-evidence/search`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query }),
  });
  if (!response.ok) throw new Error(`${query} failed: HTTP ${response.status}`);
  return response.json();
};

for (const [id, query, expectedDocument, expectedPage] of cases) {
  const body = await search(query);
  const correctRank = body.results.findIndex(
    (result) => result.documentTitle === expectedDocument
      && (result.evidenceSourceIndex ?? result.sourceIndex) === expectedPage,
  ) + 1;
  results.push({
    id,
    query,
    state: body.state,
    correctRank,
    topChunkId: body.results[0]?.chunkId ?? null,
    topDocument: body.results[0]?.documentTitle ?? null,
    topPage: body.results[0] == null ? null : (body.results[0].evidenceSourceIndex ?? body.results[0].sourceIndex),
  });
  body.results.forEach((result, index) => rows.push([
    id,
    encode(query),
    index + 1,
    result.chunkId,
    result.documentId,
    result.documentVersionId,
    encode(result.documentTitle),
    result.sourceType,
    result.sourceIndex,
    encode(result.sourceLabel),
    encode(result.content),
    result.distance,
    result.score,
  ].join("\t")));
}

const positiveQueries = [
  "AirConnect 프로젝트",
  "AirConnect에서 뭐했어?",
  "Springboot",
  "springboot",
  "Spring Boot",
  "Redis를 왜 사용했어?",
  "배포 경험 알려줘",
  "동시성 문제를 어떻게 해결했어?",
  "FOR UPDATE SKIP LOCKED",
  "19분 22초에서 11초",
  "1,252건 API 처리",
  "2,329행 처리",
  "1,480건 선점",
];
const negativeQueries = [
  "GraphQL API 구현 경험",
  "Kubernetes 운영 경험",
  "Kafka 운영 경험",
  "결제 시스템 구현 경험",
  "AWS Lambda 경험",
  "Terraform 경험",
  "Jenkins 경험",
];
const regression = [];
for (const query of positiveQueries) {
  const body = await search(query);
  regression.push({ query, expected: "EVIDENCE_FOUND", state: body.state, passed: body.results.length > 0 });
}
for (const query of negativeQueries) {
  const body = await search(query);
  regression.push({
    query,
    expected: "NO_EVIDENCE",
    state: body.state,
    passed: body.results.length === 0 && ["NO_RELEVANT_RESULTS", "NO_EVIDENCE"].includes(body.state),
  });
}
const documentsResponse = await fetch(`${baseUrl}/api/documents`, { headers });
if (!documentsResponse.ok) throw new Error(`Document listing failed: HTTP ${documentsResponse.status}`);
const documents = await documentsResponse.json();
const activeVersions = new Map(documents.map((document) => [document.documentId, document.activeVersionId]));
const ownerAndActiveIsolationPreserved = rows.every((row) => {
  const fields = row.split("\t");
  return activeVersions.get(Number(fields[4])) === Number(fields[5]);
});
const focusedTop1 = results.filter((result) => result.id.startsWith("R") && result.correctRank === 1).length;
const summary = {
  focusedTop1,
  focusedGatePassed: focusedTop1 >= 4,
  regressionChecks: regression.length,
  regressionFailures: regression.filter((result) => !result.passed).length,
  ownerAndActiveIsolationPreserved,
};

await mkdir(outputDir, { recursive: true });
await writeFile(new URL("focused-candidates.tsv", outputDir), `${rows.join("\n")}\n`, "utf8");
await writeFile(
  new URL("focused-results.json", outputDir),
  `${JSON.stringify({ authenticatedRole: login.user.role, summary, results, regression }, null, 2)}\n`,
  "utf8",
);
console.log(results.map((result) => `${result.id}: rank ${result.correctRank || "-"}, top ${result.topPage ?? "-"}`).join("\n"));
console.log(JSON.stringify(summary, null, 2));
if (!summary.focusedGatePassed
    || summary.regressionFailures > 0
    || !summary.ownerAndActiveIsolationPreserved) {
  process.exitCode = 1;
}
