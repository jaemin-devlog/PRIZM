# PRZ-013 — OpenProxy 단일 Primary SQL Gate Evidence

> **판정:** `VERIFIED`
> **기준 source:** `a65f91d`
> **검증일:** 2026-08-14

## 검증 범위

- 기존 OpenProxy 1.1.3과 systemd unit, 기존 config를 재사용했다.
- OpenProxy는 `:6432`에서 LISTEN하고 검증 전용 DB의 현재 OpenSQL
  Primary `:5432` 하나만 backend로 사용했다.
- runtime은 `prizm_app` 계정과 OpenProxy pool을, Flyway와 test-only
  maintenance는 `prizm_owner`와 direct `:5432`를 사용했다.
- 다중 DB node, Replica 승격, Primary 장애 주입, VIP, OpenProxy 이중화는 실행하지
  않았다.

## 구성과 credential 계약

- OpenProxy config는 VM 내 기존 관리 경로에서 백업 후 수정했고
  `opensql:opensql`, mode `0600`을 유지했다.
- client/backend 계정은 둘 다 `prizm_app`이며 admin과 `prizm_owner`를 runtime
  proxy credential로 사용하지 않았다.
- 로컬 Git-ignored `.env`와 실제 DB role credential을 맞추기 위해 기존
  관리자 로컬 socket에서 `prizm_app`과 `prizm_owner` 비밀번호만 동기화했다.
  role 속성·권한·소유권은 변경하지 않았다.
- `credcheck.encrypted_password_allowed=on`은 관리자 SQL session에서만 일시
  적용했고 session 종료로 해제됐다. Patroni/OpenSQL 설정 파일은 변경하지
  않았다.
- credential, 실제 IP, hostname, 전체 JDBC URL은 저장소와 이 문서에
  기록하지 않았다.

## SQL Gate

| 항목 | 결과 |
|---|---|
| Windows → OpenProxy `:6432` TCP | `PASS` |
| OpenProxy `:6432` → Primary `:5432` | `PASS` |
| `current_user = prizm_app` | `PASS` |
| backend DB = 검증 전용 DB | `PASS` |
| `pg_is_in_recovery() = false` | `PASS` |
| SELECT | `PASS` |
| 임시 테이블 INSERT → UPDATE → SELECT → DELETE | `PASS` |
| 임시 쓰기 transaction rollback | `PASS` |

## PRIZM focused E2E

다음 기존 test만 Flyway direct/runtime proxy 분리 환경에서 실행했다.

```powershell
.\gradlew.bat integrationTest --no-daemon --rerun-tasks `
  --tests com.prizm.infrastructure.PgVectorInfrastructureTest
```

- 최종 결과: `BUILD SUCCESSFUL`
- 30 tests: `28 PASS / 2 SKIP / 0 FAIL`
- SKIP 2건: Windows `SecureDirectoryStream` 미지원 cleanup-worker case
- TXT upload → ChangeLog → indexing → ACTIVE → search: `PASS`
- PDF PAGE indexing → search: `PASS`
- 실제 Ollama `bge-m3`, 1024 dimension: `PASS`
- owner isolation: `PASS`
- 실패한 새 version 발생 시 기존 ACTIVE 보존·검색: `PASS`

첫 focused 실행은 로컬 `.env`와 `prizm_owner` role credential 불일치로
Spring context 시작 전 `FAIL`했다. 승인된 credential 동기화 후 동일
test를 재실행해 위 최종 결과를 확인했다.

## OpenProxy 재시작

- OpenProxy service restart: `PASS`
- restart 후 `:6432` LISTEN: `PASS`
- restart 후 새 `prizm_app` SQL connection: `PASS`
- 지속 실행 중인 PRIZM/Hikari process의 무재시작 회복: `NOT_RUN`
  - 검증 시점에 지속 실행 중인 application process가 없었다.
  - 이 경계는 G1 필수 PASS 조건을 바꾸지 않으며 장애 전환 검증 근거로
    사용하지 않는다.

## 최종 판정

- PRZ-013-R1∼R5, R7: `PASS`
- PRZ-013-R6: service restart·new SQL connection `PASS`, application continuity
  `NOT_RUN`
- production code change: `0`
- migration change: `0`
- `git diff --check`: `PASS`
- 변경 Markdown 10개 상대 링크 검사: 손상 `0`
- repository diff의 credential·실제 IP·hostname·전체 JDBC URL pattern: `0`
- 다중 노드/failover: `NOT_RUN`
- **G1 FINAL: `PASS`**
