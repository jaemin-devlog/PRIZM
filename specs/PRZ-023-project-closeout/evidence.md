# PRZ-023 PRIZM Project Closeout Evidence

## 최종 판정

`PRIZM_PROJECT_COMPLETE`

새 기능을 추가하지 않고 final baseline `main`의 구현, 회귀, dependency, 문서와
오픈소스 배포 경계를 감사했다. blocking correctness defect와 blocking security defect는
각각 0건이다. 현재 제품 범위의 필수 기능은 구현돼 있고, 실행 가능한 현재 회귀와 기존
실환경 근거의 경계를 구분해 기록했다.

## 기준선

- final baseline main: `6e966e571793e17d7a8d8b7f6900a6094cc90961`
- closeout branch: `PRZ-023-project-closeout`
- 시작 시 `main = origin/main`: `PASS`
- 시작 시 working tree: `CLEAN`
- 시작 시 열린 PR / Issue: `0 / 0`
- baseline CI: [CI run 33262813914](https://github.com/jaemin-devlog/PRIZM/actions/runs/33262813914) `PASS`
- baseline OSS Readiness: [run 33262813898](https://github.com/jaemin-devlog/PRIZM/actions/runs/33262813898) `PASS`
- closeout PR·branch CI: PR 생성 뒤 최종 확인

## 제품 범위와 source 감사

현재 source, V1–V17 migration과 test를 기준으로 다음 흐름을 확인했다.

```text
회원가입 → 로그인 → TXT/PDF 업로드 → 불변 version·ChangeLog 보존
→ Worker 추출·분할·Ollama bge-m3 임베딩 → ACTIVE 원자적 전환
→ owner 범위 검색 → TXT/PDF 원문 위치
→ 문서 태그 → 채용공고 항목별 근거 → 읽기 전용 MCP 검색
```

- 회원가입은 `USER`로 고정되고 JWT 발급은 로그인에서만 수행한다.
- JWT 서명 뒤에도 enabled·email·role을 DB와 다시 비교하며 개인 데이터 경로는 `ROLE_USER`만 허용한다.
- document·version·chunk·job의 owner를 service, SQL과 복합 외래 키에서 함께 제한한다.
- 검색 SQL은 세 owner와 `active_version_id`, version `ACTIVE` 상태를 모두 확인한다.
- 완료 transaction이 청크 수 확인, version 활성화, active pointer와 job 완료를 함께 확정한다.
- Worker lease·heartbeat·DB time·`claim_version` fencing·recovery 계약을 유지한다.
- rollback 보상과 cleanup retry·lease recovery가 있고, 안전한 descriptor-relative 삭제를 사용할 수 없으면 fail-closed한다.
- MCP `search_career_evidence`는 별도 검색 없이 현재 사용자 ID로 기존 V2 검색을 호출한다.
- V1–V17 migration 파일은 수정하지 않았고 새 migration도 만들지 않았다.

Production Java·React source와 migration 변경은 0개다.

## Dependency와 SBOM

`nanoid 3.3.16`은 `vite 8.1.4 → postcss 8.5.25 → nanoid`의 개발 전이 의존성이었다.
공식 npm audit에서 `<3.3.18` 범위의 high advisory 1건을 확인했다. Vite, PostCSS와
package manifest를 바꾸지 않고 lockfile만 같은 3.x의 `3.3.18`로 갱신했다.

- 변경 전 전체 audit: high 1, 나머지 0
- 변경 전 production dependency audit: 0
- 변경 후 `npm ci`: `PASS`
- 실제 설치 tree: `nanoid 3.3.18`
- 변경 후 전체 audit: 0
- 변경 후 production dependency audit: 0
- frontend CycloneDX SBOM: lockfile에서 재생성
- `SHA256SUMS`: 새 SBOM checksum으로 갱신
- backend SBOM regeneration drift: 0
- SBOM 구조·checksum·회귀: `PASS`

## 현재 실행 검증

| 범위 | 실제 결과 |
|---|---|
| `gradlew check --no-daemon --dependency-verification=strict` | `PASS` |
| Backend unit | 610건: 590 PASS, 20 SKIP, 0 FAIL, 0 ERROR |
| PostgreSQL·pgvector integration | 118건: 109 PASS, 9 SKIP, 0 FAIL, 0 ERROR |
| Frontend unit | 89 PASS, 0 SKIP, 0 FAIL |
| Frontend lint | `PASS` |
| Frontend typecheck | `PASS` |
| Frontend production build | `PASS` |
| Clean-clone tooling | 26건: 25 PASS, Windows POSIX mode 1 SKIP, 0 FAIL |
| Docker Compose `config --quiet` | `PASS` |
| OSS Readiness | `PASS` |
| Markdown local link·anchor | 169 files / 745 local links `PASS` |
| 외부 링크 | 83 `PASS`, indeterminate·permanent failure 0 |
| tracked-file safety·민감 경로 | 854 tracked files `PASS` |
| Registry PRZ-000–PRZ-023 | 누락·중복 0 |
| PRZ-022 결과 경로 | 7개 존재, 옛 JSON 경로 참조 0 |
| tracked JSON parse | 144 PASS, 0 FAIL |
| Registry SVG XML parse | `PASS` |
| `git diff --check` | `PASS` |

Backend unit의 skip은 Windows의 symlink·`SecureDirectoryStream`, 로컬 전용 동결 평가
fixture와 opt-in OpenSQL migration test다. PostgreSQL integration의 skip 9건은 OpenSQL
opt-in 6건과 Windows `SecureDirectoryStream` 3건이다. 이를 PASS로 바꾸어 기록하지 않는다.

## Worker·Owner·Cleanup·Linux storage

PRZ-022 기준선 `3af4db0` 이후 final baseline까지 Production source와 migration 변경은
0개다. 현재 `check`에서 관련 unit·PostgreSQL integration이 다시 통과했다. 대규모 반복
stress와 Linux storage는 불필요하게 재실행하지 않았다.

- Worker 2·4·8 경쟁·lease·heartbeat·fencing·recovery: [PRZ-022 현재 근거](../PRZ-022-backend-reliability-evidence/evidence.md#2-비동기-worker-correctness)
- USER A/B/C owner isolation 10회: [PRZ-022 현재 근거](../PRZ-022-backend-reliability-evidence/evidence.md#3-user-owner-isolation)
- Cleanup D1–D6: [PRZ-022 현재 근거](../PRZ-022-backend-reliability-evidence/evidence.md#4-db--filesystem-cleanup-실패-복구)
- Linux Production `LocalFileStorage`: 23 PASS, 0 SKIP, 0 FAIL의 기존 근거 유지
- 검색 수치: 과거 동결 자료의 무결성 근거이며 현재 정확도나 일반화 보장으로 확대하지 않음

Linux storage 상태는 `HISTORICAL_VERIFIED_SOURCE_UNCHANGED_NOT_RERUN`이다.

## MCP

현재 source의 unit·protocol·security 회귀는 backend `check`에서 통과했다. MCP는
`POST /mcp`, protocol `2025-11-25`, 활성 `ROLE_USER` Bearer JWT와
`search_career_evidence({"query":"..."})` 계약을 유지한다. 현재 PostgreSQL 회귀와
과거 실제 OpenSQL/OpenProxy P2 근거는 서로 대신하지 않는다.

실제 OpenSQL/OpenProxy MCP E2E 상세는 [PRZ-015 Evidence](../PRZ-015-mcp-career-evidence-search/evidence.md#p2-actual-opensqlopenproxy-gate--pass)를 따른다.

## OpenSQL / OpenProxy

Closeout 시점 FAST CHECK에서 Ollama와 고정된 `bge-m3` digest는 응답했지만 VirtualBox
실행 VM은 0개였고 `localhost:6432`는 닫혀 있었다. `localhost:5432`의 순간 TCP 결과는
OpenSQL process로 귀속할 수 없어 OpenSQL 근거로 사용하지 않았다. 기존 서비스나 포트를
중단하거나 VM·NAT·OpenProxy를 복구하지 않았다.

따라서 final baseline에서 OpenSQL opt-in 6건은 `NOT_RUN`이며 최종 상태는 다음과 같다.

`HISTORICAL_VERIFIED_NOT_RERUN_ON_FINAL_MAIN`

역사 근거는 OpenSQL single direct와 OpenProxy single-Primary에만 한정한다. 최신 Flyway,
OpenSQL direct, signup/login, TXT/PDF, ACTIVE, search, owner isolation과 MCP를 final
baseline에서 다시 실행했다는 뜻이 아니다. PostgreSQL 결과를 이 상태의 대체 근거로 쓰지 않는다.

## 문서·Registry 감사

- `docs/roadmap.md`의 존재하지 않던 Registry anchor 2개를 실제 `연구·미채택 기록` heading으로 고쳤다.
- OSS Markdown 검사에 GitHub식 로컬 Markdown heading anchor 검증을 추가하고 회귀 test를 보강했다.
- `docs/project-status.md` 검증 경계에 PRZ-022를 추가하고 검색 역사 근거 한계를 함께 적었다.
- PRZ-022의 이동된 `results/*.json` 7개와 모든 참조를 확인했으며 옛 JSON 경로는 없었다.
- PRZ-008·PRZ-016 `IN_PROGRESS`, PRZ-014 `REJECTED`, PRZ-022 historical search 경계를 바꾸지 않았다.
- Registry와 시각 자료를 PRZ-023까지 동기화했다.
- README, Quickstart와 Architecture는 현재 source·migration과 일치해 수정하지 않았다.

## 알려진 한계

다음은 현재 제품 범위 밖이거나 명시된 검증 한계이며 구현 누락으로 판정하지 않는다.

- 이미지 전용 PDF, 암호화 PDF
- 이메일 인증, 비밀번호 재설정, refresh token과 OIDC
- 공개 SaaS 운영 보호
- 전체 브라우저 E2E 자동화
- OpenHA multi-node failover
- 검색 일반화 성능 보장
- 일부 파일시스템에서 `SecureDirectoryStream`을 제공하지 않을 때의 fail-closed cleanup
- Spring AI/MCP SDK `2.0.0` stateless server의 정상 initialized 뒤 notification handler warning

## 최종 감사

- blocking correctness finding: 0
- blocking security finding: 0
- Agent 독립 감사: `PASS`
- GitHub review: `REVIEW_NOT_AVAILABLE_SOLO`
- 새 기능·Production source·migration 변경: 0
- commit·push·PR: 통합 단계에서 실행
- merge·tag·GitHub Release: `NOT_RUN`
