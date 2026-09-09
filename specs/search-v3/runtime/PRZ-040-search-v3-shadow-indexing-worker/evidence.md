# PRZ-040 Evidence

## 최종 판정

`SHADOW_INDEXING_WORKER_READY`

실제 application component가 TXT와 text-layer PDF 원문을 B3 `RetrievalPassage`·`EvidenceChild`로 구조화하고,
두 BGE-M3 vector 계열을 미리 계산해 shadow DB에 원자 저장하는 경로를 구현했다. exact inventory 검증,
`READY`, 같은-version shadow activation과 stale Worker 차단을 실제 PostgreSQL에서 확인했다.

- branch: `PRZ-040-search-v3-shadow-indexing-worker`
- 기준: `refactor/search-v3@31500d449579937130f14e3608a07f625ffff28f`
- 시작 working tree: `CLEAN`
- Production Search V2 source·query·API·frontend·MCP 변경: `0`
- 실제 Ollama BGE-M3 실행: `NOT_RUN`
- `OPENSQL_VALIDATION=NOT_RUN`

## Phase A

PRZ-039 `INVENTORY_ACTIVATION_READY` commit
`31500d449579937130f14e3608a07f625ffff28f`를 `refactor/search-v3`에 fast-forward-only로 통합하고 origin parity를
확인했다. 보호 branch 대비 독립 commit이 `0`이고 spec/evidence가 통합선에 남은 뒤 PRZ-039 local·origin
branch를 삭제했다. merge commit, rebase, amend, force push와 PR은 만들지 않았다.

## 구현 흐름

```text
claim + heartbeat
→ immutable DocumentVersion 원문 읽기
→ TXT/PDF text 추출
→ EvidenceChild + B3 RetrievalPassage
→ expected logical manifest 동결
→ Passage retrievalText / Child sourceText embedding
→ Ollama model digest 재확인
→ 네 artifact 계열 원자 전체 치환
→ exact inventory 검증과 READY
→ 같은 Production active version일 때만 shadow activation
```

V20은 적용된 V18/V19를 수정하지 않고, `BUILDING` generation이 expected manifest 세 필드를 모두 null로
시작하도록 허용한다. current full claim, 빈 inventory와 구조 생성 결과를 확인한 뒤 한 번만 동결한다.
partial manifest와 manifest 없는 READY 이후 상태는 DB constraint가 거부한다.

TXT는 `page_no=null`, PDF는 추출된 1-based page를 보존한다. Passage는 page와 structural parent를 넘지 않으며,
모든 Child는 `sourceText`를 그대로 유지한다. 실제 Ollama 대신 deterministic 1024차원 test double을 사용했으므로
이 결과는 BGE-M3 가용성이나 속도 근거가 아니다. model metadata provider의 exact/`:latest` 이름, digest
정규화·검증, 미설치·장애 처리는 별도 unit test로 확인했다.

## PostgreSQL 결과

| 범위 | 결과 |
| --- | --- |
| PRZ-040 Worker end-to-end | `7/7 PASS` |
| claim-first manifest runtime | `4/4 PASS` |
| PRZ-038 job fencing 현재 회귀 | `9/9 PASS` |
| PRZ-039 inventory·activation | `11/11 PASS` |
| shadow/V20 migration | `11/11 PASS` |
| V1~V20 fresh migration | `9/9 PASS` |
| ChangeLog migration 경계 | `1/1 PASS` |
| 위 PostgreSQL focused 합계 | `52/52`, failure·error·skip `0` |

Worker 시나리오는 다음을 확인했다.

- TXT와 2-page PDF의 Passage·Child 및 artifact당 vector 1개 저장
- pre-insert logical manifest와 persisted inventory의 독립 검증 및 verified vector fingerprint 저장
- 모든 Passage·Child vector의 model ID, digest, 1024차원과 input policy 일치
- same-version 첫 activation과 reindex의 `old ACTIVE → SUPERSEDED`, `new READY → ACTIVE`
- PROCESSING인 non-current version은 `READY + RETRY_WAIT`까지만 진행하며 V2 active version 불변
- file·빈 구조·Passage embedding·Child embedding·storage·model digest drift 실패에서 기존 ACTIVE 보존
- retryable Ollama 장애 뒤 같은 generation을 due retry해 duplicate·mixed artifact 없이 완료
- reclaim 뒤 이전 claim의 저장 거부, 일부 insert 실패의 delete 포함 전체 rollback과 clean retry
- `documents.active_version_id`, `document_versions.status`, `document_chunks` 변경 0

READY 이후 현재 active version이 아니거나 document lock이 경합하면 parsing·embedding을 반복하지 않고 activation만
1분 뒤 재시도한다. 자동 scheduler·dispatch와 recovery coordinator 연결은 `NOT_IMPLEMENTED`다.

## 회귀 검증

| 검증 | 결과 |
| --- | --- |
| PRZ-040 Search V3 unit | `38/38`, failure·error·skip `0` |
| 기존 Search V2 Worker focused unit | `36/36`, failure·error·skip `0` |
| PRZ-036 lifecycle·Child reuse | `28/28`, failure·error·skip `0` |
| Search V3 dataset·SEALED integrity | `15/15`, failure·error·skip `0` |
| benchmark validator mutation tests | `18/18 PASS` |
| 전체 backend `check` | unit `648`, integration `160`, failure·error `0`, skip `20`·`9` |
| OSS readiness | `PASS` |
| `git diff --check` | `PASS` |

실행한 focused test와 전체 `check`는 현재 branch source를 사용했다. PostgreSQL 결과를 OpenSQL 결과로 확대하지
않으며 실제 Ollama·OpenSQL은 실행하지 않았다.

## 남은 경계

- Search V3 job 자동 생성·dispatch scheduler와 recovery coordinator
- Child vector 재사용 최적화
- Search V3 query/API와 Production Search V2 cutover
- cleanup·retention 정책
- Ollama tag가 색인 중 바뀌었다가 검사 전에 원래 digest로 되돌아오는 외부 TOCTOU의 더 강한 통제

## SEALED FINAL

- dataset: `search-v3-fresh-seed-1.0.1`
- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- Git tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

validator는 metadata와 hash만 확인했고 SEALED 검색은 실행하지 않았다.
