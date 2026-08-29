# PRZ-022 Backend Reliability Evidence

## 최종 판정

`BACKEND_EVIDENCE_READY`

기준선 `3af4db0`에서 Worker, USER owner isolation, cleanup을 실제 PostgreSQL로
반복 검증했고, Production `LocalFileStorage` 테스트 23건을 Docker Linux에서 모두
통과했다. 검색 축은 새 benchmark를 만들지 않았다. 기존 P0·P4·P5·P7-B 원시 결과와
freeze hash의 무결성만 다시 확인했으므로 판정은 `HISTORICAL_EVIDENCE_VERIFIED`다.

Production 소스, migration, 설정은 바꾸지 않았다. 이번 변경은 평가용 통합 테스트와
검증 스크립트, Spec/Evidence에만 한정한다.

## 기준선과 실행 환경

- branch: `PRZ-022-backend-reliability-evidence`
- baseline main: `3af4db05f5f1b2d9802335de5eac9ad7b98555fa`
- Docker Server: `29.7.2 / linux / x86_64`
- Worker·Owner·전체 integration: Windows JVM, PostgreSQL 16.14 + pgvector 0.8.2
  Testcontainers Linux container
- Owner 검색 경로: Ollama `bge-m3:latest`, 1024차원, digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- Cleanup: Docker Linux JVM, 외부 PostgreSQL 16.14 + pgvector 0.8.2 container,
  `SecureDirectoryStream`
- Linux storage/unit runner: `gradle:8.14.3-jdk17`
- current-main 검색 정확도 재실행: `NOT_RUN_BY_SCOPE`
- OpenSQL: `NOT_RUN`

전체 집계는 [results-summary.json](results-summary.json), source/test 계약 hash는
[backend-contracts-summary.json](backend-contracts-summary.json), 검색 재계산 결과는
[search-results-summary.json](search-results-summary.json)에 기록했다.

## 실행 결과 요약

| 축 | 실행 환경 | 실제 실행 수 | 결과 | 최종 판정 |
|---|---|---:|---|---|
| 검색 품질·일반화 | 동결 P0·P4·P5·P7-B JSON 및 현재 Production 경로 감사 | 과거 결과 4세트 재계산, freeze hash 3건 확인 | 원시 지표·summary·freeze hash 일치 | `HISTORICAL_EVIDENCE_VERIFIED` |
| Worker correctness | Windows JVM + Testcontainers PostgreSQL Linux | 10회 × 2·4·8 Worker, claim 140회 | 중복 claim·stale 완료·미복구 0 | `VERIFIED` |
| USER owner isolation | USER A/B/C + PostgreSQL + Ollama `bge-m3` | 10회, 총 240회 | 노출·무단 변경·inactive leak 0 | `VERIFIED` |
| Cleanup reliability | Linux JVM + PostgreSQL + Production cleanup service | D1–D5 각 10회, D6 30회; 총 80개 시나리오 | 70 jobs 완료, 고아·무관 파일 삭제 0 | `VERIFIED` |
| Linux `LocalFileStorage` | Docker Linux, Java 17 | 23 tests | 23 PASS, 0 SKIP, 0 FAIL | `VERIFIED` |

## 1. 검색 품질·일반화

`verify-prz022-search-evidence.mjs`가 P0·P4·P5·P7-B 원시 JSON에서 지표를 다시
계산했다. 계산값은 저장된 summary와 일치했고, P5 dataset·ground truth와 P7-B raw의
freeze hash도 일치했다. 현재 Production 검색 핵심 경로에 PostgreSQL FTS, RRF,
Evidence Judge, NLI shadow가 승격되지 않았다는 점도 source에서 확인했다.

| 자료 | 성격 | Top1 | Recall@5 | MRR@5 | Negative FPR |
|---|---|---:|---:|---:|---:|
| P0 | historical development baseline | 57.14% | 67.86% | 0.6146 | 6.25% |
| P4 | historical development | 82.14% | 85.71% | 0.8363 | 0% |
| P5 | frozen holdout | 50.00% | 61.11% | 0.5509 | 25.00% |
| P7-B | independent users/documents/questions | 33.33% | 58.33% | 0.4491 | 41.67% |

P4 `82.14%`는 development Top1이다. 현재 정확도나 일반화 정확도로 사용할 수 없다.
P5와 P7-B의 Gate는 각각 `FAIL`이며, 이번 작업은 현재 코드로 Ollama retrieval을 다시
실행하지 않았다. 따라서 이 축은 `HISTORICAL_EVIDENCE_VERIFIED`로만 기록한다.

## 2. 비동기 Worker correctness

[worker-results.json](worker-results.json)은 10회 반복에서 2·4·8 Worker 동시 claim과
lease·heartbeat·recovery·fencing·ACTIVE 안정성을 검증한 결과다. claim 이후
heartbeat/completion이 중단된 Worker 소실 등가 상태를 만들었다.

| 항목 | 원시 수치 |
|---|---:|
| claim attempts / 성공 / 빈 claim | 140 / 30 / 110 |
| duplicate claims | 0 |
| completion attempts / 완료 처리 | 80 / 60 |
| duplicate completion attempts / accepted | 10 / 0 |
| Worker 소실 등가 상태 / recovery / 미복구 | 10 / 10 / 0 |
| heartbeat 유지 | 10 |
| stale completion attempts / accepted | 10 / 0 |
| ACTIVE version 안정성 assertion | 60 |

focused test와 전체 PostgreSQL integration에서 같은 평가 메서드가 모두 통과했다.

## 3. USER owner isolation

PRZ-020 이후의 현재 계약대로 A·B·C 모두 활성 `USER`로 만들었다. 다른 사용자의 목록,
상세, 수정, 삭제, 새 버전, 버전 삭제, TXT/PDF 원본, inactive version, REST 검색과 MCP
검색을 10회 반복했다. 각 mutation 뒤에는 owner A의 문서와 버전 row가 그대로인지 DB에서
다시 확인했다.

[owner-isolation-results.json](owner-isolation-results.json)의 원시 수치는 다음과 같다.

| 항목 | 원시 수치 |
|---|---:|
| total attempts | 240 |
| read attempts | 100 |
| mutation attempts | 80 |
| REST search attempts | 30 |
| MCP search attempts | 30 |
| data exposure | 0 |
| unauthorized mutation | 0 |
| inactive leak | 0 |
| REST cross-owner result | 0 |
| MCP cross-owner evidence | 0 |

과거 `SYSTEM_ADMIN` 행렬이나 1,100건 수치는 사용하지 않았다.

## 4. DB ↔ Filesystem Cleanup 실패 복구

[cleanup-results.json](cleanup-results.json)은 Linux에서 Production cleanup service와
PostgreSQL을 연결해 D1–D6를 실행한 결과다.

- D1 DB rollback 뒤 동기 보상 삭제
- D2 보상 삭제 실패 뒤 `REQUIRES_NEW` cleanup 등록과 완료
- D3 일시적 삭제 실패 → retry → 성공
- D4 파일 삭제 성공 뒤 DB 완료 기록 실패 → lease recovery → 완료
- D5 stale cleanup Worker의 늦은 완료 차단
- D6 2·4·8 Worker 경쟁에서 단일 claim과 중복 완료 차단

| 항목 | 원시 수치 |
|---|---:|
| D1 compensation completed | 10 |
| cleanup jobs / completed | 70 / 70 |
| retry / recovered | 30 / 20 |
| D6 claim attempts / duplicate claims | 140 / 0 |
| stale attempts / accepted | 20 / 0 |
| duplicate completion attempts / accepted | 30 / 0 |
| unrecovered / orphan remaining | 0 / 0 |
| unrelated file deletion | 0 |

Linux `LocalFileStorageTest`는 [별도 결과](linux-local-file-storage-results.json)에 기록했다.
경로 이탈, symlink, 부모 교체, descriptor-relative 삭제, `NOFOLLOW_LINKS`, fail-closed,
missing-file 멱등성을 포함한 23건이 `23 PASS / 0 SKIP / 0 FAIL`이었다. Windows skip을
Linux PASS로 간주한 것이 아니라 Linux 컨테이너에서 테스트 클래스를 직접 다시 실행했다.

## 전체 검증

| 검사 | 결과 |
|---|---|
| Worker focused | 1 PASS, 0 SKIP, 0 FAIL |
| USER owner isolation focused | 1 PASS, 0 SKIP, 0 FAIL |
| Cleanup D1–D6 Linux focused | 1 PASS, 0 SKIP, 0 FAIL |
| Backend unit, Docker Linux | 604 PASS, 6 SKIP, 0 FAIL — 총 610건 |
| PostgreSQL integration | 109 PASS, 9 SKIP, 0 FAIL — 총 118건 |
| Linux `LocalFileStorageTest` | 23 PASS, 0 SKIP, 0 FAIL |
| 검색 raw metric·freeze hash | `PASS` |
| 현재 backend source/test 계약 audit | `PASS` |
| Markdown/local links | 165 files / 708 local links `PASS` |
| PRZ-022 JSON parse | 7 files `PASS` |
| `git diff --check` | `PASS` |
| OpenSQL | `NOT_RUN` — 이번 필수 범위 아님 |

PostgreSQL integration의 skip 9건은 OpenSQL 6건과 Windows에서
`SecureDirectoryStream`을 요구하는 cleanup 3건이다. Linux cleanup과 storage 검증은 위의
별도 실행에서 모두 통과했다.

## 평가 하네스 보정 기록

첫 Owner 실행은 Ollama가 꺼져 있어 검색 호출 전에 중단됐다. 로컬에 이미 설치된 Ollama와
동결된 `bge-m3` 모델을 시작한 뒤 동일 행렬을 실행했다. MCP 직접 호출은 Spring Security
7.1의 인증 완료 토큰 생성자를 사용하도록 평가 helper를 고쳤다.

첫 Linux cleanup 실행은 JPA의 `stored_file_path` 변경을 flush하기 전에 JDBC로 조회해
임시 값 `pending`을 파일 경로로 사용했다. D1·D2에서 실제 저장 키를 조회하도록 flush를
추가한 뒤, 동결한 반복 수와 판정 기준을 바꾸지 않고 D1–D6 전체를 다시 실행했다. 두 경우
모두 Production 결함이 아니라 평가 하네스 준비 문제였다.

Backend unit은 `.env`와 ignored 개인 평가 Java 6개를 포함하지 않은 추적 파일 기반 임시
작업본에서 실행했다. 해당 개인 파일은 이동·수정·삭제하지 않았고 PRZ-022 결과에도
포함하지 않았다.

## 범위 한계

- OpenSQL은 실행하지 않았으므로 `NOT_RUN`이다. PostgreSQL 결과를 OpenSQL 근거로 쓰지 않는다.
- 검색 수치는 과거 동결 실행의 무결성 근거다. 현재 `main` 검색 정확도라고 표현하지 않는다.
- commit, push, PR 생성은 수행하지 않았다.
