# PRZ-005 — OpenSQL·Ollama E2E Tasks

> **현재 상태:** 핵심 범위 `VERIFIED`
>
> 상세 실행·실패·복구 이력은 [작업 보고서](implementation-report.md)에 보존한다.

## P1. VM과 서비스 준비

- [x] `T-01` etcd → Patroni·OpenSQL → OpenProxy 기동 구조를 확인했다.
- [x] `T-02` Patroni·OpenProxy systemd unit을 등록·검증했다.
- [x] `T-03` single-node Leader와 `5432`·`8008`·`6432` LISTEN을 확인했다.
- [x] `T-04` Windows Host-only → OpenProxy `6432` TCP 연결을 확인했다.

상세 근거: [환경·서비스 준비](implementation-report.md#vm-장애-대응과-opensql-서비스-준비)

## P2. DB와 최소 권한

- [x] `T-05` `prizm`, `prizm_owner`, `prizm_app`을 분리했다.
- [x] `T-06` 최소 runtime 권한과 거부 동작을 확인했다.
- [x] `T-07` vector `0.8.1`과 소유자를 확인했다.
- [x] `T-08` OpenProxy 설정을 원래 SHA-256과 같은 상태로 복원했다.
- [x] `T-12A` Spring Context 없는 Flyway test 경로를 교정했다.
- [x] `T-12` Flyway V1–V13과 재실행 신규 적용 0건을 확인했다.

상세 근거: [DB·역할·Flyway·최소 권한 구성](implementation-report.md#db역할flyway최소-권한-구성)

## P3. Direct `5432` 전체 흐름

- [x] `T-13` Spring Boot·OpenSQL과 Ollama `bge-m3` 1024차원 embedding을 연결했다.
- [x] `T-14` demo login → TXT·PDF 업로드 → `ACTIVE` → 원문 검색을 통과했다.
- [x] `T-16` 두 `USER`의 목록·상세·검색 격리와 browser 흐름을 확인했다.

상세 근거: [Spring Boot·Ollama·API·브라우저 E2E](implementation-report.md#spring-bootollamaapi브라우저-e2e)

## P4. 자동 검증과 통합

- [x] `T-17` 격리 DB의 OpenSQL opt-in integration test를 통과했다.
- [x] `T-18` backend·frontend·OSS·SBOM·문서 감사를 통과했다.
- [x] source `eab32c8`, PR #26과 merge `6dc9822`를 Evidence에 기록했다.

상세 근거: [자동 통합 테스트·전체 회귀·병합 감사](implementation-report.md#자동-통합-테스트전체-회귀병합-감사)

## 후속 또는 제외 범위

- [ ] `T-09` OpenProxy 인증은 `AUTH_BLOCKED`다.
- [ ] `T-10` OpenProxy SQL routing과 안전한 인증은 `DEFERRED`다.
- [ ] `T-11` 영구 journal은 `DEFERRED`다.
- [ ] `T-15` OpenHA와 DB failover는 `DEFERRED`다.

상세 근거: [현재 보류 범위](implementation-report.md#현재-보류-범위)
