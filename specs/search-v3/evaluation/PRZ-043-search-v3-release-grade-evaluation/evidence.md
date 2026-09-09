# PRZ-043 Evidence

## 최종 판정

`EVALUATION_INVALID`

공식 prediction 전에 Gold 제외용 glob이 Windows 경로 구분자와 맞지 않아
`sealed/gold.json`의 일부 item-level 의미 필드가 명령 출력에 포함됐다. 계약상 조기 Gold 접근은
즉시 평가 무효이므로 V2/V3 공식 검색을 시작하지 않았다. 결과를 만들거나 같은 데이터셋으로
다시 시도하지 않는다.

| 항목 | 현재 사실 |
| --- | --- |
| branch | `PRZ-043-search-v3-release-grade-evaluation` |
| 평가 하네스 기준 | `e389d27235e16a5e14d05272999a60b7fc95c35f` |
| Search V2/V3 source 기준 | `0e95472bb68f72accf0d6b2171c22f0719fe6941` |
| ZIP SHA-256 | `b513fbfced174b8ced15d122a80d7e12c0315376d1f9770a3f2ac29ade07cdee` |
| combined SHA-256 | `78b8612eab01ec09be4e30f665366b45366b0f12f1d9d516ab247bf6a33617a8` |
| Gold JSON parser 실행 | `NOT_RUN` |
| Gold 의미 필드 조기 노출 | `YES` — prediction 전 |
| V2/V3 official prediction | `NOT_RUN / NOT_RUN` |
| official attempt | `0` |
| metric | `NOT_ASSESSED` |
| Production cutover | `NOT_RUN` |

## Gold-free 사전 감사

아래 내용은 조기 노출 사고 전에 완료한 사전 감사 결과다.

- ZIP entry `102`, payload `97`, unsafe path·symlink·암호화·CRC 오류 `0`
- payload 누락·size/hash 불일치 `0`
- manifest canonical self-hash와 combined SHA-256 독립 재계산 `PASS`
- user `75`, document `90`, query `600`, TXT/PDF `45/45`, profession `15×5`
- ID·path·document SHA·normalized query 중복 `0`
- owner별 프로젝트명 exact-present/absent `247/353`
- 이 사전 감사 시점에는 Gold의 존재·size·raw SHA만 확인했고 JSON 의미 내용은 열지 않았다.

첫 combined 계산에서 PowerShell byte-list 누적 오류로 잘못된 값이 한 번 나왔으나, ZIP 내부
validator와 같은 streaming SHA 방식 및 독립 Python canonical 계산으로 다시 확인해 기대값과
정확히 일치했다. dataset 문제나 공식 attempt가 아니며 검색·Gold 접근은 발생하지 않았다.

## 무결성 중단 기록

2026-09-03 01:00 KST 이전 구현 감사 중, `rg`에 전달한 `--glob '!sealed/gold.json'`이 ZIP을
추출한 Windows 경로의 역슬래시 표현과 맞지 않았다. 그 결과 검색 대상에서 Gold가 제외되지
않았고 `answerability`, `queryCategory`, `expectedDocumentId` 같은 일부 item-level 값이 출력됐다.
질문 본문이나 `exactEvidenceText`를 의도적으로 요청하지 않았고 JSON parser도 실행하지 않았지만,
prediction 전 의미 내용 노출이라는 계약 위반은 달라지지 않는다.

중단 직후 확인한 상태:

- 고정 official run directory 존재: `false`
- official attempt: `0`
- V2 prediction row/file: `0 / 없음`
- V3 prediction row/file: `0 / 없음`
- prediction SHA-256: `없음 / 없음`
- metric·bootstrap·Gate 실행: `NOT_RUN`
- dataset payload 수정: `0`
- `src/main/**`, migration, frontend, MCP 변경: `0`

하네스 구현, synthetic preflight, PostgreSQL 공식 runtime, Gold reference/span/page 검증, metric,
전체 회귀는 `NOT_RUN`이다. neutral Ollama 호출로 `bge-m3:latest` digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension `1024`만
확인했으며 dataset query는 사용하지 않았다.

## 보존 경계

PRZ-042 `V3_NO_GO / SEED_FINAL_PROTOCOL_RESULT`는 그대로 유지한다. 이번 기록은 V2/V3 품질의
우열을 말하지 않으며 모든 metric은 `NOT_ASSESSED`다. 검색 구현·평가 데이터·Gold를 수정하지
않았고 동일한 v1.0.2 dataset의 공식 재시도도 하지 않는다.

## 종료 검증

| 검사 | 결과 |
| --- | --- |
| 원본 ZIP SHA-256 재확인 | `PASS` |
| `final-run-receipt.json` / `metrics-report.json` JSON parse | `PASS` |
| Markdown 전체 검사 | `PASS` — 259 files, local links 845, external links 97 |
| OSS readiness | `PASS` — verifier test 16/16 |
| `git diff --check` | `PASS` |
| Production/evaluation source diff | `0` |
| backend/PostgreSQL/frontend 전체 회귀 | `NOT_RUN` — 공식 평가 전 무결성 중단, 문서-only 기록 |
| OpenSQL | `NOT_RUN` |
