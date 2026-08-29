# PRZ-024 PRIZM v1.0.0 소스 릴리스 근거

## 현재 판정

`IMPLEMENTED_UNVERIFIED`

## 기준선

- baseline main: `8ba056b5c94228d1782e306a1310b84a8a063493`
- 기존 Git tag: 0개
- 기존 GitHub Release: 0개
- baseline main CI: [run 33266715507](https://github.com/jaemin-devlog/PRIZM/actions/runs/33266715507) `PASS`
- baseline main OSS Readiness: [run 33266715533](https://github.com/jaemin-devlog/PRIZM/actions/runs/33266715533) `PASS`
- 릴리스 형태: Apache-2.0 `source-only`

## 시작 시 확인한 릴리스 Gate

- backend 버전: `0.0.1-SNAPSHOT`
- frontend 버전: `0.0.0`
- backend·frontend SBOM root component: 위 개발 버전과 동일
- GitHub Private Vulnerability Reporting: `disabled`
- `SECURITY.md`: 비공개 신고 채널이 없다고 안내
- `SUPPORT.md`: 없음
- Issue Form·PR template: 없음
- PRZ-002 `OR-005`, `G-02`, `T-06`: 첫 외부 배포 전에 재개 필요
- PRZ-002 `OR-006`, `T-07`: 외부 Issue·PR 접수 전에 재개 필요

## 실행 근거

### 보안·지원 운영 Gate

- GitHub Private Vulnerability Reporting 활성화: API `204`, 최초 조회와 최종 Audit 전 재조회 모두 `enabled=true`
- `SECURITY.md`: 공개 Issue가 아닌 실제 Private Vulnerability Reporting URL 안내
- `SUPPORT.md`: 최신 소스 릴리스, 가능한 범위의 지원·무 SLA와 지원 제외 범위 명시
- `MAINTAINERS.md`: 단일 유지관리자의 변경·보안·릴리스 책임과 solo review 경계 명시
- `버그 보고`·`기능 제안`·`문서 개선` Issue Form과 blank issue 차단·보안 contact link 추가
- Pull Request template에 범위·검증·영향·비밀정보·migration·OpenSQL 경계 추가
- YAML parse: 6 files `PASS`

### 버전·SBOM

- Gradle project version: `1.0.0`
- frontend package·lock root version: `1.0.0`
- backend·frontend CycloneDX root component: `1.0.0`
- backend 릴리스 JAR 이름: `PRIZM-1.0.0.jar` 확인
- SBOM generator 재실행, `SHA256SUMS` 갱신과 구조·checksum 검사: `PASS`
- OSS readiness에 여섯 릴리스 메타데이터 값의 일치 검사를 추가하고 회귀 테스트를 보강
- `0.0.1-SNAPSHOT`, frontend root `0.0.0`, 옛 root purl 잔존: 0

### 현재 실행 검증

| 범위 | 결과 |
|---|---|
| `gradlew clean check bootJar` | `PASS` — 2분 22초 |
| Backend unit | 610건: 590 PASS, 20 SKIP, 0 FAIL, 0 ERROR |
| PostgreSQL·pgvector integration | 118건: 109 PASS, 9 SKIP, 0 FAIL, 0 ERROR |
| Frontend unit | 89 PASS, 0 SKIP, 0 FAIL |
| Frontend lint·typecheck·production build | 모두 `PASS` |
| npm 전체·production audit | 취약점 각각 0 |
| Docker Compose `config --quiet` | `PASS` |
| OSS Readiness | required 18, Markdown 176 files·760 local links, external 91 `PASS` |
| SBOM·license·tracked sensitive-data | `PASS` |
| YAML 6 files·Registry SVG XML | `PASS` |
| `git diff --check` | `PASS` |

첫 OSS Readiness 실행은 GitHub 행동 신고 문서의 잘못된 URL 1개를 실제 404로 찾아
`FAIL`했다. GitHub 공식 `Reporting abuse or spam` URL로 고친 뒤 같은 전체 검사를
재실행해 permanent·indeterminate failure 0으로 통과했다.

Backend와 PostgreSQL `SKIP`은 PRZ-023 Closeout과 같은 Windows·local fixture·OpenSQL
opt-in 조건이다. OpenSQL/OpenProxy는 릴리스 메타데이터와 source-only 운영 문서 변경의
검증 환경이 아니므로 재실행하지 않았고 PostgreSQL 결과로 대신하지 않는다.

생성한 JAR과 frontend `dist`는 검증 산출물일 뿐 Git으로 추적하거나 GitHub Release에
첨부하지 않는다. Release에는 GitHub가 제공하는 source archive만 사용한다.

## 감사와 통합

- Production Java·React source 변경: 0
- Flyway migration·Production config 변경: 0
- blocking correctness·security·license finding: 0 — 독립 diff 감사와 한국어 윤문 후 재감사 모두 `PASS`
- 릴리스 준비 PR·CI·merge: `NOT_RUN`
- annotated tag·GitHub Release: `NOT_RUN`

## 최종 판정

현재 구현·로컬 검증은 완료했지만 독립 감사, GitHub 통합, tag와 Release가 남아 있다.
모두 끝나기 전에는 `VERIFIED`로 전환하지 않는다.
