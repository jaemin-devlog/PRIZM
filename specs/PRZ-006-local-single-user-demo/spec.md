# PRZ-006 — 로컬 보관함 빠른 시작

> **상태:** `VERIFIED`
> **유형:** Feature
> **선행 문서:** [PRZ-004](../PRZ-004-clean-clone-demo/spec.md)
> **기준 소스:** `bfd86005862aa15927c707250330c70ebf81c133`
> **최종 확인:** 2026-08-04

시작 기준 source commit은 `b370cd91f93bd617abebd7afce56fc495eb7b161`이며,
별도 GitHub Issue는 만들지 않았다.

## 목적

기존 Docker Compose를 유지하면서, 로컬 Docker 실행에서는 로그인 입력 대신
`PRIZM 시작하기`를 눌러 바로 Career Vault를 체험할 수 있게 한다. 일반 실행에서는
기존 이메일·비밀번호 로그인을 유지한다.

이 작업은 현재의 `.env` 기반 Docker 실행, 호스트 Ollama, 일반 JWT 로그인을 대체하지
않는다. Compose 비밀값 자동 생성, Ollama 컨테이너·모델 자동 준비, 다중 사용자 배포
구성과 회원가입은 후속 기능으로 분리한다.

## 기능 구성과 동작 흐름

```text
Compose에서 local demo opt-in 확인
↓
PRIZM 시작하기 선택
↓
고정 local USER 생성 또는 재사용
↓
기존 JWT 발급과 DB 사용자 재확인
↓
owner-scoped Career Vault 진입
```

일반 실행이나 opt-in 비활성 상태에서는 기존 이메일·비밀번호 로그인만 노출한다.
local demo와 `SYSTEM_ADMIN` bootstrap이 함께 활성화되면 계정 쓰기 전에 기동을
중단한다.

## 평가 근거

- 주 근거: `EVAL-R1-02` — 기존 사용 방법을 해치지 않으면서 처음 체험하는 개발자의
  진입 절차를 줄인다.
- 보조 근거: `EVAL-R1-01` — 빠른 시작에서도 JWT와 사용자별 소유권 계약을 보존한다.
- 보조 근거: `EVAL-R1-03` — 빠른 시작의 범위와 제외 항목을 실제 문서와 테스트로
  확인할 수 있게 한다.

점수 상승을 약속하지 않는다. source, 실행 가능한 test와 실제 환경 기록만 근거로 남긴다.

## 사용자 시나리오

### PRZ-006-R01 — 기존 Docker 실행은 그대로 유지

사용자는 현재처럼 `.env`를 준비하고 `docker compose up -d --build`를 실행한다.
PostgreSQL·pgvector와 backend·frontend, 호스트 Ollama의 기존 연결 방식과 일반
이메일·비밀번호 로그인은 바뀌지 않는다.

### PRZ-006-R02 — 로컬 보관함을 바로 시작

기본 Compose로 실행한 화면은 로그인 폼을 숨기고 `PRIZM 시작하기`를 제공한다.
누르면 `local@prizm.local` 로컬 `USER` 계정을
생성 또는 재사용하고 기존 JWT를 발급한다. 원문 비밀번호는 생성·저장·출력·응답하지
않는다.

### PRZ-006-R03 — 보호 계약을 우회하지 않음

빠른 시작으로 발급한 access token도 기존 `DatabaseJwtAuthenticationConverter`의
사용자 활성 상태·email·role DB 재검증과 owner-scoped query를 통과한다. service 코드에
사용자 ID를 하드코딩하거나 Spring Security를 끄지 않는다.

### PRZ-006-R04 — 일반 실행에는 기능을 노출하지 않음

`PRIZM_LOCAL_DEMO_ENABLED`가 `true`일 때만 local-session endpoint와 시작 버튼을
활성화한다. 일반 Spring Boot 실행은 기본값 `false`를 유지하며 기존 로그인만 제공한다.

## 보존 계약

- 기존 JWT 인증, DB 사용자 재검증과 사용자별 문서·검색 결과 격리
- immutable version, `active_version_id`, worker·cleanup·Flyway V1–V13 계약
- TXT/PDF 처리, PDF 열람, 최대 5개 Career Evidence 검색
- 문서 목록·경력 근거 검색의 빈 상태와 로그인 문구·스타일 정리. 기능과 API 계약은 변경하지 않는다.
- PostgreSQL 결과와 OpenSQL·OpenProxy·OpenHA 검증 결과의 분리

## 제외 범위

- `.env` 제거, Compose 비밀값 자동 생성, Docker 내부 Ollama·`bge-m3` 설치
- 별도 multi-user Compose, OpenSQL/OpenProxy/OpenHA, DB failover 또는 MCP
- 회원가입, 이메일 인증, 비밀번호 재설정, refresh token, OIDC
- Flyway migration, 검색 알고리즘, 모델 cache·가중치와 DB volume의 Git 추가

## 완료 조건

1. 기본 Compose는 기존 `.env`, DB, host Ollama와 일반 로그인 흐름을 유지한다.
2. 기본 Compose에서만 로컬 보관함 시작이 활성화되고, 버튼은 local `USER`에 대한
   정상 JWT를 발급한 뒤 보관함으로 이동한다.
3. 일반 Spring Boot 실행에서는 local-session endpoint와 버튼이 활성화되지 않는다.
4. 빠른 시작이 기존 JWT DB 재검증과 owner-scoped API를 우회하지 않는다.
5. backend unit·PostgreSQL integration, frontend lint/build와 Docker Compose를 실제로
   검증하고 결과를 `evidence.md`에 기록한다. OpenSQL·OpenProxy·OpenHA는 이번
   결과로 `PASS`라고 표시하지 않는다.
6. 독립 읽기 전용 AUDIT에서 CRITICAL/HIGH/MEDIUM finding이 없고 실제 PR·CI와
   사용자 승인에 따른 solo review 예외를 기록한 뒤에만 통합한다.
