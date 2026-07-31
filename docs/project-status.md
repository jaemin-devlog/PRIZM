# PRIZM 현재 구현 현황

> 현재 검증 기준일: 2026-07-30
>
> 구현 기준 source commit: `91949f2cabff8e37c6a6210b3641e4a7c37d2910`
>
> 기존 구현 기준선: `PRZ-000 AS_BUILT_BASELINE`
>
> 최종 판단 기준: 소스 코드(source code), Flyway 마이그레이션(migration),
> 실행 가능한 테스트(test)

## 한눈에 보는 현재 상태

| 구분 | 현재 상태 |
|---|---|
| 현재 제품 | Spring Boot 애플리케이션과 React 기반 Career Vault Reference App |
| 구현됨 | 로그인, 사용자별 문서 격리, TXT/PDF 업로드, 변경 불가능한 버전 관리, 비동기 색인·복구, pgvector 검색, Career Vault 문서 관리 |
| 현재 단계 | P0 소스 전용(source-only) 준비 완료, P1 진행 중 — OpenSQL 단일 SQL Gate 검증 완료, demo `USER` clean-clone 전체 흐름은 `NOT_RUN` |
| 미구현 | CareerFact, 근거 기반 portfolio, `/api/v1`, MCP, 독립 Engine 패키지, OpenProxy·OpenHA와 DB 장애 전환 |

PRIZM의 장기 목표는 재사용 가능한 Career Intelligence Engine과 Reference App을
제공하는 것입니다. 현재 저장소는 아직 독립 Engine 패키지가 아니며, 하나의
Spring Boot 애플리케이션에 주요 기능이 모여 있습니다.

## 현재 사용자 흐름

```text
로그인
→ 내 문서 목록 확인
→ UTF-8 TXT 또는 텍스트가 포함된 PDF 업로드
→ 원본과 새 문서 버전 저장
→ Worker가 텍스트 추출·분할·임베딩 수행
→ 처리가 끝난 버전을 검색 대상으로 전환
→ 내 문서에서 원문 위치와 함께 검색 결과 확인
```

새 버전 처리가 실패하면 이전 검색 대상 버전을 유지합니다. 다른 사용자의 문서와
검색 결과는 이 흐름에 포함하지 않습니다. 신규 사용자가 회원가입부터 검색까지
완주할 수 있는 안전한 demo `USER` 절차는 아직 없습니다.

## 구현된 기능

### 로그인과 사용자 격리

- 이메일·비밀번호 로그인과 JWT 인증
- 요청마다 DB에서 사용자 활성 상태·이메일·역할 재확인
- 사용자별 문서·버전·처리 작업·검색 결과 격리
- 일반 `USER`와 관리 역할인 `SYSTEM_ADMIN`의 API 권한 분리

### 문서와 버전 관리

- UTF-8 TXT와 비암호화 텍스트 PDF 업로드
- 문서 목록·필터·상세·수정·삭제와 PDF 열람
- 원본 파일, SHA-256 해시와 변경 불가능한 버전(immutable version) 보존
- 새 버전 등록과 처리 완료 뒤 검색 대상 버전(active version) 전환

### 색인과 검색

- Ollama `bge-m3`를 이용한 1024차원 임베딩
- PostgreSQL pgvector 기반 원문 근거 검색
- TXT 텍스트 구간과 PDF 페이지 위치 반환
- 단일 검색 결과와 최대 5개의 Career Evidence 결과 제공
- 근거가 없을 때 등록 문서에서 찾지 못했다고 안내

### 비동기 처리와 파일 정리

Worker가 중단돼도 만료된 작업을 다시 처리할 수 있습니다. 오래된 Worker가 최신
결과를 덮어쓰지 못하도록 보호하며, DB 처리와 원본 파일 정리가 어긋난 경우에는
별도 정리 작업으로 복구를 시도합니다.

구성 요소와 내부 보호 방식은 [Architecture](architecture.md), 설계 선택의 배경과
트레이드오프는 [대표 문제 해결 사례](showcase/problem-solving-case-studies.md)에서
확인할 수 있습니다.

## 부분 검증과 환경별 상태

| 대상 | 상태 | 최근 기록 |
|---|---|---|
| Backend `test` task | `PASS` | 2026-07-30 실제 245건 재실행, 환경 조건 14건 skip, 실패·오류 0건 |
| Frontend lint·build | `PASS` | ESLint와 production build 통과 |
| PostgreSQL·pgvector integration | `PASS` | 2026-07-30 Windows에서 68건 재실행, 환경 조건 3건 skip, 실패·오류 0건. OpenSQL 결과가 아님 |
| Dense 검색 평가 | `HISTORICAL_PASS_NOT_RERUN` | 2026-07-14 합성 기준선 보존 |
| Docker Compose | `PASS` | 2026-07-29 clean-clone에서 구성·빌드·기동과 backend·frontend 상태 확인. demo `USER` 전체 흐름은 `NOT_RUN` |
| Ollama `bge-m3` | `PASS` — PostgreSQL 회귀 범위 | 2026-07-30 Windows PostgreSQL·pgvector 회귀에서 실제 사용. OpenSQL+Ollama 전체 사용자 흐름은 `NOT_RUN` |
| OpenSQL 단일 SQL Gate | `PASS` | 2026-07-30 Rocky Linux 9.7 single-node OpenSQL에서 Flyway·vector·검색·소유권·Worker SQL 통과 |
| OpenProxy·OpenHA | `NOT_RUN` 또는 `NOT_VERIFIED` | 애플리케이션 연결과 DB 장애 전환 검증 없음 |

세부 실행 환경과 명령은 [PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md),
[PRZ-002 Evidence](../specs/PRZ-002-open-source-readiness/evidence.md),
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md)에서
확인합니다. PostgreSQL·pgvector 결과를 OpenSQL 결과로 바꾸어 표현하지 않습니다.

## 미구현 기능

- 안전한 demo `USER`와 clean-clone 로그인→업로드→ACTIVE→검색 전체 재현
- OpenSQL과 Ollama를 함께 사용하는 전체 사용자 흐름
- OpenProxy 애플리케이션 연결, OpenHA와 DB 장애 전환
- 변경 로그 기반 동기화와 MCP 검색 API
- CareerFact 후보·확인·거절과 `INSUFFICIENT_EVIDENCE`
- 검증된 CareerFact를 이용한 JSON·Markdown portfolio와 source manifest
- `/api/v1`, OpenAPI, webhook/outbox
- 독립 Engine artifact와 기관용 workspace·권한

## 알려진 한계

- README 절차만으로 로그인할 수 있는 안전한 demo `USER`가 없습니다.
- 전체 처리 시간과 버전당 최대 chunk 수를 제한하지 않습니다.
- 프런트엔드 자동 UI 테스트가 없습니다.
- V13의 일부 제약과 기존 데이터 보정 전용 회귀 테스트가 없습니다.
- 일부 JavaDoc이 TXT/PDF 공통 동작을 TXT 전용으로 설명합니다.
- 일부 파일시스템에서는 안전 조건을 충족하지 못해 자동 파일 정리를 중단합니다.
- OpenSQL 단일 SQL Gate는 통과했지만 OpenSQL+Ollama·브라우저 전체 흐름과
  OpenProxy·OpenHA·DB 장애 전환은 검증하지 않았습니다.

## 다음 우선순위

제품 개발 순서는 [개발 로드맵](roadmap.md), 대회 일정과 P0~P10 세부 단계는
[티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md)을 따릅니다.
가장 가까운 작업은 안전한 demo `USER`를 포함한 clean-clone 전체 흐름입니다.
