# PRZ-041 Evidence

## 최종 판정

`SEARCH_V3_RUNTIME_READY`

- branch: `PRZ-041-search-v3-runtime-completion`
- 기준: `refactor/search-v3@ded1adb4002e904eb4b5652db556faa9a0d6f8a2`
- 시작 working tree: `CLEAN`
- Production Search V2 변경: `0`
- 실제 PostgreSQL runtime 검증: `PASS`
- `REAL_BGE_M3=PASS`
- `OPENSQL_VALIDATION=NOT_RUN`

current active version의 자동 dispatch부터 claim·heartbeat·recovery, TXT/PDF shadow indexing,
inventory·READY·activation과 ACTIVE-only query까지 연결했다. 실제 로컬 Ollama BGE-M3로 TXT를 색인하고
동일 vector space에서 shadow query를 실행했다. Search V3 API 공개와 Search V2 cutover는 하지 않았다.

## Phase A

PRZ-040 `SHADOW_INDEXING_WORKER_READY` commit
`ded1adb4002e904eb4b5652db556faa9a0d6f8a2`를 `refactor/search-v3`에 fast-forward-only로 통합하고 origin
parity를 확인했다. 독립 commit이 `0`이고 spec/evidence가 통합선에 남은 뒤 PRZ-040 local·origin branch를
삭제했다. merge commit, rebase, amend, force push와 PR은 만들지 않았다.

## 구현 결과

| 경계 | 결과 |
| --- | --- |
| 자동 dispatch | `PASS` — concurrent caller 2개, generation/job 각 1개, 중복 소유 0 |
| 일반 claim과 due retry | `PASS` — PRZ-038 fenced claim 경로 재사용 |
| heartbeat | `PASS` — full-lineage current claim만 갱신, stale claim 거부 |
| expired lease recovery | `PASS` — exact token reclaim 뒤 새 claim을 즉시 processor로 전달 |
| TXT / text-layer PDF | `PASS` — B3 Passage·Child와 두 vector 계열 저장 |
| READY / activation | `PASS` — same-version만 ACTIVE, inactive version은 READY에서 연기 |
| Passage 검색 | `PASS` — owner-scoped ACTIVE+COMPLETED exact cosine Top20 |
| Child 선택 | `PASS` — Top5 Passage 내부 `CHILD_DENSE_V1`, Passage 순서 불변 |
| Typed validation | `PASS` — 숫자 조건 `FOUND`, semantic은 `UNASSESSED` |
| same-version reindex | `PASS` — 새 ACTIVE generation 근거만 반환 |
| source provenance | `PASS` — document/version/path/page/line/code-point 유지 |
| Search V3 API / V2 cutover | `NOT_IMPLEMENTED` — 이번 범위 아님 |

자동 scheduler는 `prizm.search-v3.worker-enabled=false`를 기본값으로 둔다. 켜면 dispatch, 일반 claim 처리와
expired recovery가 Search V2 scheduler 옆에서 독립적으로 동작한다. 한 recovery poll은 job 하나만 처리해
무제한 drain으로 scheduler thread를 점유하지 않는다.

## 실제 BGE-M3 smoke

SEALED와 무관한 synthetic TXT 한 건을 application context의 실제 `OllamaEmbeddingService`로 처리했다.

| 항목 | 실제 값 |
| --- | --- |
| configured model | `bge-m3` |
| Ollama artifact | `bge-m3:latest` |
| resolved digest | `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` |
| family / parameters | `bert` / `566.70M` |
| quantization / file size | `F16` / `1,157,672,605 bytes` |
| vector dimension | `1024` |
| Passage·Child 저장 vector | `PASS` — 같은 digest·dimension·input policy |
| 실제 shadow query | `PASS` — 직접 근거와 원문 경로 반환, 다른 owner 결과 0 |

## 실행한 검증

| 명령 / suite | 실제 결과 |
| --- | --- |
| `./gradlew check` | `PASS` — unit `657`, failures/errors `0`, skipped `20`; integration `164`, failures/errors `0`, skipped `9` |
| Search V3 generation/job/inventory/storage/Worker focused PostgreSQL | `PASS` — `45/45` |
| `SearchV3RealBgeM3RuntimeIntegrationTest` | `PASS` — `1/1` |
| PRZ-041 Worker runtime test | `PASS` — `10/10` |
| `node scripts/verify-oss-readiness.mjs` | `PASS` — Markdown `251`, local links `833`, verifier `16/16`, external links `97/97` |
| DEV/CAL materialization `--check` | `PASS` — documents `6`, queries `24`, combined `a1fcd76c...47d41df` |
| `git diff --check` | `PASS` |
| OpenSQL actual execution | `NOT_RUN` |

Focused PostgreSQL `45/45`는 generation contract `4`, job fencing `9`, inventory activation `11`, shadow storage
migration `11`, shadow Worker/runtime `10`이다. 실제 BGE-M3 smoke `1/1`은 별도로 집계했다.

## 보호 경계

- `SearchService`, `VectorSearchRepository`, V2 `DocumentIndexingProcessor`와 `document_chunks`: 변경 `0`
- migration, dependency, frontend, MCP, Production Search API: 변경 `0`
- `documents.active_version_id`: Search V3가 변경하지 않음
- Search V3 query는 application 내부 shadow service이며 외부 API에 연결하지 않음
- worker opt-in 기본값: `false`

## SEALED FINAL

- dataset: `search-v3-fresh-seed-1.0.1`
- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

metadata와 hash 확인만 허용하며 SEALED 검색은 실행하지 않는다.
