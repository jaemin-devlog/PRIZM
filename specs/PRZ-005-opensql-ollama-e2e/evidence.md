# PRZ-005 — OpenSQL·Ollama E2E Evidence

> **최종 판정:** `VERIFIED`
> **검증 기준 소스:** `eab32c870f06237d37048b6b8de1287e5e18ae66`
> **검증일:** 2026-08-02
> **상세 부록:** [implementation-report.md](implementation-report.md)

## 판정 요약

OpenSQL single-node의 direct `5432` 경로와 호스트 Ollama `bge-m3`를 사용해 합성
TXT·PDF의 API·브라우저 흐름과 두 사용자 격리를 검증했다. 실제 증거 DB와 분리한
OpenSQL opt-in 통합 테스트, 전체 backend·frontend 회귀와 OSS·SBOM·문서 감사도
통과했다.

OpenProxy는 Windows 호스트에서 `6432` TCP 연결까지만 `VERIFIED`다. SQL routing은
`NOT_VERIFIED`, 인증은 `AUTH_BLOCKED`, 애플리케이션 적용은 `DEFERRED`다. 이 결과를
OpenHA, DB failover 또는 서비스 연속성 검증으로 확대하지 않는다.

## 검증 기준 소스

- 검증 source commit: `eab32c870f06237d37048b6b8de1287e5e18ae66`
- `main` merge commit: `6dc982227bafe94f0879c22bf4381a6e47adf925`
- 상세 명령, 실행 시점별 실패·복구와 인프라 정보:
  [implementation-report.md](implementation-report.md)

## 검증한 수직 흐름

```text
OpenSQL direct 5432 연결
↓
Flyway V1–V13 적용과 최소 권한 분리
↓
Spring Boot 기동과 demo USER 로그인
↓
합성 TXT·PDF 업로드
↓
Ollama bge-m3 1024차원 embedding
↓
chunk 저장과 active version 원자적 전환
↓
API·브라우저 Career Evidence 검색
↓
두 USER의 문서·검색 결과 격리 확인
```

## 요구사항별 근거

### `PRZ-005-R01` — 환경 기준선과 시간 동기화

- Rocky Linux 9.7 single-node VM, 시간 동기화와 서비스 상태를 확인했다.
- 마지막 검증 시 etcd는 `active`·자동 시작, Patroni와 OpenProxy는
  `active`·`disabled`였다. 실행 상태와 재부팅 자동 시작 설정을 구분해 기록했다.

### `PRZ-005-R02` — 네트워크와 endpoint 경계

- OpenSQL direct `5432`, Patroni API `8008`, OpenProxy `6432` LISTEN을 확인했다.
- Windows Host-only에서 OpenProxy `6432` TCP 연결은 확인했지만 SQL 연결 성공으로
  해석하지 않았다.

### `PRZ-005-R03` — 전용 데이터베이스와 최소 권한 역할

- Flyway는 `prizm_owner`, runtime은 `prizm_app`으로 분리했다.
- 관리자 역할을 OpenProxy에 노출하지 않았고 실제 증거 DB에 파괴적 명령을 실행하지
  않았다.

### `PRZ-005-R04` — 실제 애플리케이션 전체 흐름

- 실제 OpenSQL direct `5432`와 Ollama에서 로그인, 합성 TXT·PDF 업로드, 처리,
  API·브라우저 검색을 확인했다.
- 사용자 두 명의 문서와 검색 결과가 서로 섞이지 않았고 owner 불일치는 0건이었다.

### `PRZ-005-R05` — 구현 계약 보존

- Flyway V1–V13, ACTIVE version, 1024차원 embedding과 기존 owner 경계를 보존했다.
- 실패·미완료 version을 검색 후보로 확대하거나 제품 migration·dependency를 바꾸지
  않았다.

### `PRZ-005-R06` — 증거와 공개 경계

- 비밀정보와 실제 사용자 문서를 저장소에 기록하지 않았다.
- OpenSQL direct 결과, OpenProxy 결과와 PostgreSQL 회귀 결과를 구분했다.

## 자동 검증

- 격리 DB `prizm_integration_test`의 `OpenSqlInfrastructureTest`: `PASS` — 1개 성공,
  실패·오류·skip 0건
- backend 단위 테스트: `PASS` — 262개
- backend 통합 테스트: `PASS` — 69개
- frontend lint·typecheck·production build: `PASS`
- OSS readiness·SBOM·문서·민감정보 감사: `PASS`
- frontend unit test: `NOT_RUN` — 공식 실행 명령이 없었다.
- 기본 회귀의 OpenSQL opt-in test: 환경 승인이 없어 정상 `SKIPPED`; 실제 OpenSQL
  검증은 위의 격리 실행 결과로 판정했다.

## 실제 환경 검증

- OpenSQL direct `5432` synthetic TXT/PDF API 흐름: `PASS`
- OpenSQL direct `5432` browser 흐름: `PASS`
- 두 USER 문서·검색 격리: `PASS`
- OpenProxy `6432` TCP: `VERIFIED`
- OpenProxy SQL routing: `NOT_VERIFIED`
- OpenProxy 인증: `AUTH_BLOCKED`
- OpenProxy 애플리케이션 적용: `DEFERRED`
- OpenHA·DB failover: `DEFERRED`
- 영구 journal: `DEFERRED`

## Audit 및 GitHub 통합

- [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26)으로 검증 source를
  `main`에 통합했다.
- backend 2건, frontend 2건, License·Markdown·SBOM 2건 등 GitHub check 6건이
  `SUCCESS`였다.
- 등록된 review는 없어 `REVIEW_NOT_AVAILABLE_SOLO`다.

## 남은 제한

- OpenProxy의 안전한 외부 비밀정보 주입 방식이 확인되기 전에는 SQL routing과
  애플리케이션 적용을 진행하지 않는다.
- single-node 결과는 OpenHA나 DB failover 증거가 아니다.
- 커널 원인은 `NOT_VERIFIED`, 파일시스템은 `PARTIALLY_VERIFIED`, 오프라인 전체
  검사는 `NOT_RUN`으로 남는다.

## 주요 검증 이력

실행 순서, 당시의 `NOT_RUN`, 실패 원인, rollback과 복구 판단은
[상세 구현·검증 보고서](implementation-report.md)에 보존한다. 이 문서는 최종 판정과
검증한 수직 흐름만 요약한다.
