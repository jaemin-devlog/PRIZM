# PRZ-044 Tasks

## ORIENT / SPEC / PLAN

- [x] PRZ-043/refactor source parity와 clean branch 확인
- [x] INPUT ZIP physical entry·manifest·payload read-only 감사
- [x] Gold/sealed physical entry `0`, Gold 외부 탐색 `0`
- [x] source/model/dataset/one-shot/prediction/Gold release/metric Gate 동결

## IMPLEMENT

- [x] 안전한 Gold-free INPUT ZIP loader와 payload verifier
- [x] PRZ-044 prediction DTO, canonical writer와 disk reload verifier
- [x] contract-bound one-shot attempt/failure/completion receipt
- [x] 실제 V2/V3 TXT/PDF runtime adapter — V2 전체 후 V3 전체
- [x] Windows/Linux forbidden Gold/path regression test
- [x] synthetic actual PostgreSQL/BGE-M3 preflight
- [x] `build.gradle` focused/preflight/official task
- [x] execution contract source/model/input hash freeze
- [x] `CAREER_DESCRIPTION`, `PORTFOLIO`, `RESUME` 명시적 fail-closed 매핑
- [x] attempt-1 byte parity와 failure receipt 보존
- [x] 별도 attempt-2 contract/source/mapping hash freeze

## VERIFY / AUDIT

- [x] compile/focused unit tests — `PASS`, 28/28
- [x] synthetic runtime preflight — `PASS`, PostgreSQL 16.14·pgvector 0.8.2·실제 BGE-M3
- [x] official INPUT/model/source/attempt precheck
- [ ] official indexing 90 documents — `FAIL`, 첫 문서 적재 전 중단
- [ ] V2 prediction 600/600 freeze/reload — `NOT_RUN`, artifact 0
- [ ] V3 prediction 600/600 freeze/reload — `NOT_RUN`, artifact 0
- [ ] completion receipt — `NOT_CREATED`; failure receipt와 Gold absent/access `false/false` 확인
- [ ] 관련 backend/PostgreSQL/frontend 회귀 — official one-shot 실패 뒤 `NOT_RUN`
- [x] OSS readiness, Markdown, `git diff --check`, scope audit — `PASS`

### attempt-2

- [x] official 90-document DocumentType mapping — `PASS`, mapped/unmapped/ambiguous `90/0/0`
- [x] 실제 PostgreSQL 16.14·pgvector 0.8.2·BGE-M3 indexing preflight — `PASS`
- [x] V2 prediction `600/600` freeze/reload — `PASS`
- [ ] V3 prediction `600/600` — `NOT_CREATED`; 8개 문서 `PASSAGE_GENERATION` 실패
- [ ] completion receipt — `NOT_CREATED`; failure receipt 생성
- [ ] Gold·metric — `NOT_RUN`

### attempt-3

- [x] Passage 상한 production fix 별도 commit과 source SHA 동결
- [x] 새 attempt-3 contract와 source/model/input/mapping hash 동결
- [x] synthetic preflight — PostgreSQL 16.14·pgvector 0.8.2·실제 BGE-M3 `PASS`
- [x] official V2/V3 indexing — `90/90`, `90/90`
- [x] V2/V3 prediction freeze/reload — `600/600`, `600/600`
- [x] duplicate/missing query — `0/0`
- [x] completion receipt — `PASS`, official run `1/1`
- [x] Gold-after-completion 순서 검증 — prediction 이전 Gold 접근 `0`
- [x] focused/PostgreSQL/backend/OSS regression — `PASS`
- [ ] Gold·metric·Adoption Gate — `NOT_RUN`, 정식 PRZ-044 Gold artifact 미제공

## INTEGRATE

- [x] `evidence.md`, `prediction-failure-receipt.json`
- [x] Registry 상태·판정 일치
- [x] `prediction-completion-receipt.json` — attempt-3 `PASS`
- [ ] PRZ-044 branch commit/push와 origin parity
- [ ] `NOT_RUN`: Gold, metric, V3 adoption, PR/merge/cutover/다음 PRZ
