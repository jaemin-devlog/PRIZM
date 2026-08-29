# PRZ-022 Backend Reliability Evidence

## 상태

- lifecycle: `VERIFIED`
- 기준선: `3af4db05f5f1b2d9802335de5eac9ad7b98555fa`
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT`
- 제외: Production 검색 조정, 기능 변경, migration, OpenSQL 실행, commit, push, PR

## 목적

현재 `main`의 네 가지 백엔드 핵심 경험을 재검증한다. 제3자가 source·test·원시 결과를
따라가며 확인할 수 있는 형태로 근거를 남기는 것이 목적이다.

1. 검색 품질과 일반화
2. 비동기 Worker correctness
3. USER owner isolation
4. DB transaction 밖 파일 cleanup 실패 복구

## 사전 감사 판정

| 축 | 판정 | 이유 |
|---|---|---|
| 검색 | `NEEDS_CURRENT_REVERIFY` | P0/P4/P5/P7-B 동결 자료는 있지만, 현재 기준선과 소스·원시 결과가 맞는지 다시 확인해야 한다. |
| Worker | `NEEDS_NEW_EVIDENCE` | 계약별 통합 테스트는 있지만, 현재 기준선의 반복 검증 결과를 한데 모은 Evidence가 없다. |
| Owner isolation | `NEEDS_NEW_EVIDENCE` | 기존 인증 테스트는 일부 경로만 검증한다. PRZ-020 이후 USER A/B/C 전체 행렬과 mutation 뒤 DB 재조회는 동결하지 않았다. |
| Cleanup | `NEEDS_NEW_EVIDENCE` | Production 계약별 테스트는 있지만, D1–D6와 반복 결과를 현재 기준선 Evidence로 정리하지 않았다. |

## 동결 검증 계약

### 검색

- 기존 P0 development baseline, P4 development, P5 holdout, P7-B independent corpus를
  서로 다른 자료로 유지한다.
- 원시 JSON의 지표를 다시 계산해 문서 수치와 대조한다. 현재 Production 검색 소스가 당시
  채택 경로와 일치하는지도 확인한다.
- `82.14%`는 P4 development Top1일 뿐 현재 일반화 정확도라고 부르지 않는다.
- FTS/RRF/Judge/NLI/literal shadow 실패 결과는 Production 성능으로 합치지 않는다.

### Worker

- W1 `SKIP LOCKED` 동시 선점, W2 DB-time lease, W3 heartbeat, W4 recovery,
  W5 `claim_version` fencing, W6 완료 transaction의 ACTIVE 원자성을 검증한다.
- 소실 표현은 “claim 이후 heartbeat/completion이 중단된 Worker 소실 등가 상태”로 한정한다.

### USER owner isolation

- 사용자 A/B/C는 모두 활성 `USER`다.
- 대상은 목록, 상세, 수정, 삭제, 새 버전, 버전 삭제, TXT/PDF original, inactive version,
  REST 검색, MCP 검색이다.
- 타 owner 요청을 거부하는지 확인하고, mutation 뒤 DB를 다시 조회해 소유자 A의 row가
  변하지 않았는지도 검사한다.
- 동일 동결 행렬을 10회 반복한다.

### Cleanup D1–D6

- D1 명시적 rollback 뒤 동기 보상 삭제
- D2 보상 삭제 실패 뒤 `REQUIRES_NEW` cleanup 등록
- D3 일시적 삭제 실패 뒤 retry와 최종 성공
- D4 파일 삭제 성공 뒤 DB 완료 기록 실패와 lease recovery
- D5 stale cleanup Worker의 늦은 완료 차단
- D6 2·4·8 Worker 경쟁에서 `SKIP LOCKED` 단일 claim과 중복 완료 차단

Fault-injected `FileStorage` + Production Cleanup Service + PostgreSQL 결과와 Linux 실제
`LocalFileStorage`의 `SecureDirectoryStream`·`NOFOLLOW_LINKS`·fail-closed 결과를 분리한다.

## 완료 조건

- 네 축의 source/test/수치/한계를 `evidence.md`와 `results/*.json`에서 확인할 수 있다.
- focused test, backend unit, PostgreSQL integration, Linux filesystem test,
  Markdown/local link, JSON parse, `git diff --check` 결과를 기록한다.
- OpenSQL은 `NOT_RUN`으로 기록한다.
