# PRZ-037 Evidence

## 최종 판정

`SHADOW_STORAGE_READY`

PRZ-036 lifecycle을 V18 additive migration으로 옮기고 실제 PostgreSQL 16+pgvector에서 검증했다.
V1~V18 fresh migration과 기존 migration 회귀 `9/9`, Search V3 lineage·vector·active pointer 제약
`7/7`이 모두 통과했다. Production Search V2 source와 `document_chunks` DDL은 바꾸지 않았다.

## 기준과 범위

- 기준: `PRZ-036-search-v3-index-lifecycle@7accea2b28d3cfb1a3d09dd50cf0237c72b627b9`
- `origin/PRZ-036-search-v3-index-lifecycle`: 기준 SHA와 일치
- 시작 working tree: `CLEAN`
- migration: `V18__create_search_v3_shadow_storage.sql`
- Production Search V2 source 변경: `0`
- `document_chunks` DDL 변경: `0`
- JPA entity/repository·Worker·검색 query: `NOT_IMPLEMENTED`

## 구현한 shadow 저장 구조

| 대상 | 구현 내용 |
| --- | --- |
| generation | version과 독립된 세대, 5개 상태, 정책·모델 계약, expected count·manifest hash, 실패 metadata |
| V3 job | generation별 한 건, 상태·claim version·attempt·lease·recovery token·완료/실패 metadata |
| Passage | generation별 key·순서, source/retrieval text, 입력 hash와 page·line·code-point provenance |
| Child | 같은 generation Passage FK, 원문·hash·순서·source block·Parent provenance |
| 두 vector table | Passage/Child와 최대 1:1, artifact input hash와 generation model 계약 composite FK, `vector(1024)` |
| active pointer | nullable `documents.active_search_v3_generation_id`, owner·문서·active version composite FK |

기존 `processing_jobs`는 version별 한 건만 허용해 same-version reindex generation을 구분할 수 없다. 이
때문에 `search_v3_indexing_jobs`를 별도로 선택했다. 기존 작업과 잠금 코드는 수정하지 않았다.

## DB와 service 책임 분리

| PRZ-036 계약 | 상태 | 근거 |
| --- | --- | --- |
| generation과 DocumentVersion 분리 | `PASS_POSTGRESQL` | 같은 version의 generation 여러 건 생성 |
| frozen expected manifest | `PASS_SOURCE` | expected Passage/Child count와 SHA-256 필수 column·check |
| owner-document-version-generation lineage | `PASS_POSTGRESQL` | cross-owner·cross-document·cross-generation 연결 거부 |
| V3 job·lease·claim·recovery metadata | `PASS_POSTGRESQL` | generation별 1:1과 recovery token shape 검증 |
| active generation pointer | `PASS_POSTGRESQL` | cross-owner·cross-document pointer 거부 |
| same-version reindex | `PASS_POSTGRESQL` | version pointer를 유지하고 generation 교체 |
| 최초 업로드 | `PASS_POSTGRESQL` | 두 active pointer null과 실패 generation 비활성 유지 |
| stale Worker fencing | `PARTIAL` | metadata·check만 구현, 실제 claim/recovery SQL·service는 미구현 |
| exact inventory 뒤 READY/activation | `NOT_IMPLEMENTED` | 후속 service transaction 책임 |
| 원자 활성화 transaction | `NOT_IMPLEMENTED` | 후속 service에서 `job -> version -> document` 잠금 필요 |

`PASS_POSTGRESQL`은 Testcontainers의 `pgvector/pgvector:0.8.2-pg16-bookworm`에서 실제 제약을 실행한
결과다. `PASS_SOURCE`는 후속 service가 사용해야 할 저장 필드와 check가 존재한다는 범위에 한정한다.

후속 정적 감사에서 V18의 composite FK 자체에는 blocking finding이 없었다. 다만 최초 test source에
Passage vector의 중복·잘못된 input hash·cross-generation 연결과 동일 owner의 cross-document active
pointer 거부가 명시돼 있지 않았다. V18은 바꾸지 않고 해당 assertion만 기존 7개 test에 보완했다.

## PostgreSQL 실행 환경과 복구

- Docker context: `desktop-linux`
- Docker Desktop: `4.83.0 (234302)`
- Docker Engine: `29.6.2`, Linux/amd64
- `docker-desktop` WSL2 배포판: `Running`
- Docker CLI와 Testcontainers: 정상 연결
- 로컬 PostgreSQL·Podman 등 대체 disposable runtime: 없음

첫 실행은 종료되지 않은 backend가 남긴 `Docker/run/dockerInference`와
`docker-secrets-engine/engine.sock` AF_UNIX endpoint를 제거하지 못해 중단됐다. Docker를 완전히 종료한
뒤 runtime 디렉터리를 `*.stale-prz037` 이름으로 보존 이동해 복구했다. 진단 중 Docker Model Runner는
일시 비활성화했지만 검증 뒤 공식 CLI로 다시 활성화했고 Docker server가 계속 정상임을 확인했다. Docker
이미지·volume과 data VHDX는 변경하지 않았다. 복구 뒤 기존 PRIZM 컨테이너를 건드리지 않고
Testcontainers 검증을 수행했다.

## 실제 검증

| 검증 | 결과 |
| --- | --- |
| integration test Java compile | `PASS` |
| `SearchV3ShadowStorageMigrationTest` | `7/7 PASS`, failure·error·skip `0` |
| `CareerPlatformMigrationTest` | `9/9 PASS`, failure·error·skip `0` |
| V18 기대값 회귀 재현·수정 | PostgreSQL 3개 클래스 `36`건, failure·error `0`, skip `3` |
| 전체 backend `check` | unit `610`건·integration `125`건, failure·error `0`, skip `20`·`9` |
| 기존 owner·lease·완료·실패·복구 unit | `19/19 PASS` |
| PRZ-036 lifecycle·Child reuse | `28/28 PASS` |
| Search V3 dataset·SEALED guard | `15/15 PASS` |
| OSS readiness | `PASS` — Markdown 235개·local link 810개, Node 16/16, external link 97/97 |
| `git diff --check` | `PASS` |
| V18 migration 적용 | `PASS_POSTGRESQL` |
| PostgreSQL composite FK·unique·vector check | `PASS_POSTGRESQL` |
| H2 DB 검증 | `NOT_RUN` — PostgreSQL 근거로 대체하지 않음 |
| DEV/CAL benchmark·model 실행 | `NOT_RUN` |
| OpenSQL migration | `NOT_RUN` |

실행 명령:

```text
gradlew.bat compileIntegrationTestJava integrationTest --tests ...SearchV3ShadowStorageMigrationTest --no-daemon --rerun-tasks
gradlew.bat integrationTest --tests ...CareerPlatformMigrationTest --no-daemon --rerun-tasks
gradlew.bat integrationTest --tests ...DocumentChangeLogMigrationDatabaseIntegrationTest --tests ...PgVectorInfrastructureTest --tests ...PostgreSqlOpenSqlCompatibilityTest --no-daemon --rerun-tasks
gradlew.bat check --no-daemon --dependency-verification=strict --rerun-tasks
gradlew.bat test --tests ...IndexingCompletionOwnershipTest --tests ...ProcessingJobLeaseServiceTest --tests ...WorkerLeaseHeartbeatTest --tests ...IndexingFailureServiceTest --tests ...ProcessingJobRecoveryServiceTest --no-daemon --rerun-tasks
gradlew.bat searchEvaluation --tests ...SearchV3IndexLifecycleTest --tests ...SearchV3ChildEmbeddingReusePlannerTest --no-daemon --rerun-tasks
gradlew.bat searchEvaluation --tests ...SearchV3DenseAblationDatasetTest --tests ...SearchV3MinimalShadowIntegrityTest --no-daemon --rerun-tasks
node scripts/verify-oss-readiness.mjs
git diff --check
```

초기 `initializationError`는 Docker 환경 복구 전 결과로 보존한다. 최종 실행에서는 실제 DB test method
7개와 기존 migration test 9개가 모두 실행됐고 blocking schema finding은 `0`이었다.

첫 push의 GitHub CI는 V18이 정상 적용된 뒤에도 기존 테스트가 migration 수와 최신 버전을 V17로
고정해 둬 4건 실패했다. 같은 실패를 로컬에서 재현한 뒤 migration 기대값, 오류 추적 범위와 OpenSQL
객체 inventory를 V18로 맞췄다. V3 shadow table과 sequence는 소유권 검사에만 포함했고 Production
runtime 권한은 추가하지 않았다. 수정 후 전체 `check`가 통과했다. 이는 OpenSQL 실행 근거가 아니므로
OpenSQL migration 상태는 계속 `NOT_RUN`이다.

## 호환성과 남은 Gate

- V18은 V17 뒤에 추가됐고 기존 migration, Search V2 table, dependency, API, frontend, MCP와 Docker
  구성을 수정하지 않았다.
- JPA가 새 column/table을 읽지 않으므로 현재 Production 검색·색인 경로는 그대로다.
- active pointer 대상의 `ACTIVE + COMPLETED` 상태, exact manifest inventory와 activation rollback은
  교차 table transaction이 필요해 후속 service 범위다.
- `SUPERSEDED`/`FAILED` retention 기간과 실제 cleanup Worker는 `OPEN_DECISION`이다.
- V18 shadow schema는 `SHADOW_STORAGE_READY`다. 이는 schema 제약 검증 완료를 뜻하며 Worker,
  activation service나 Search V3 query 구현 완료를 뜻하지 않는다.

## SEALED FINAL

- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- Git tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

검색은 실행하지 않았고 기존 guard로 metadata와 hash 불변만 확인했다.
