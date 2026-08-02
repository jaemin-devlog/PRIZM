# PRIZM 현재 구현 현황

> 현재 검증 기준일: 2026-08-02
>
> PRZ-005 검증 source commit: `eab32c870f06237d37048b6b8de1287e5e18ae66`
>
> PRZ-005 GitHub 통합: [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26),
> merge commit `6dc982227bafe94f0879c22bf4381a6e47adf925`
>
> PRZ-004 GitHub 통합 merge commit: `1f9a5ad964778a2e72de9949a0fadae042008392`
>
> 전체 clean-clone 검증 source commit: `25d09e9eee9837cf4a63d7461699825ff22743e2`
>
> 최종 Windows·Linux 경로 교정·CI source commit:
> `aff3e87a9a912e44fcf217291a45328cf451cfc9`
>
> 기존 구현 기준선: `PRZ-000 AS_BUILT_BASELINE`
>
> 최종 판단 기준: 소스 코드(source code), Flyway 마이그레이션(migration),
> 실행 가능한 테스트(test)
>
> PRZ-004는 자동 검증, 두 clean clone, 독립 감사와 GitHub PR #25 CI를 통과해
> `main`에 통합됐습니다.
>
> PRZ-005는 실제 OpenSQL 직접 `5432` API·브라우저 E2E, 두 사용자 격리,
> OpenSQL opt-in integration test와 전체 회귀를 통과했습니다. PR #26의 GitHub
> check 6건이 성공했고 review는 없어 `REVIEW_NOT_AVAILABLE_SOLO`로 기록합니다.

## 한눈에 보는 현재 상태

| 구분 | 현재 상태 |
|---|---|
| 현재 제품 | Spring Boot 애플리케이션과 React 기반 Career Vault Reference App |
| 구현됨 | 로그인, 사용자별 문서 격리, TXT/PDF 업로드, 변경 불가능한 버전 관리, 비동기 색인·복구, pgvector 검색, Career Vault 문서 관리 |
| 현재 단계 | 소스 전용 공개 준비, clean-clone과 실제 OpenSQL 전체 흐름 검증 완료. 다음 후보인 DB 장애복구는 아직 시작하지 않음 |
| 미구현·미검증 | CareerFact, 근거 기반 portfolio, `/api/v1`, MCP, 독립 Engine 패키지, OpenProxy SQL routing·안전한 인증, OpenHA와 DB 장애 전환 |

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
검색 결과는 이 흐름에 포함하지 않습니다. PRZ-004는 공개 회원가입을
추가하지 않고, 한 번만 켜는 demo `USER`와 합성 TXT/PDF로 이 흐름을 재현합니다.
검증 source commit의 두 fresh clone에서 이 흐름을 확인하고 독립 감사와
GitHub 통합을 완료했습니다.

PRZ-005에서는 Spring Boot와 Ollama `bge-m3`를 실제 OpenSQL `5432`에 직접
연결해 같은 흐름의 API와 브라우저 E2E를 검증했습니다. 두 사용자의 문서 목록,
상세와 검색 결과가 서로 노출되지 않는 것도 확인했습니다.

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
| Backend `test` task | `PASS` | 2026-08-02 source `eab32c8`: 전체 262개, 환경 조건 15개 skip, 실패·오류 0건 |
| Frontend lint·typecheck·build | `PASS` | 2026-08-02 source `eab32c8`: lint·typecheck·production build 통과. 공식 unit test 명령은 없어 `NOT_RUN` |
| 기본 integration 회귀 | `PASS` | 2026-08-02 source `eab32c8`: 전체 69개, 환경 조건 3개 skip, 실패·오류 0건. 기본 실행에서 OpenSQL opt-in test skip은 정상 |
| Dense 검색 평가 | `HISTORICAL_PASS_NOT_RERUN` | 2026-07-14 합성 기준선 보존 |
| Docker Compose | `PASS` — PRZ-004 | 2026-08-01 서로 다른 project·port·volume의 두 독립 clone에서 구성·빌드·기동과 demo `USER` 전체 흐름 확인 |
| Ollama `bge-m3` | `VERIFIED` — PostgreSQL·OpenSQL 범위 구분 | 2026-08-02 Ollama 0.32.3, `bge-m3:latest` digest와 1024차원·0이 아닌 임베딩을 확인. 실제 OpenSQL 직접 `5432` E2E에도 사용 |
| OpenSQL 단일 SQL Gate | `PASS` | 2026-07-30 Rocky Linux 9.7 single-node OpenSQL에서 Flyway·vector·검색·소유권·Worker SQL 통과 |
| PRZ-005 OpenSQL+Ollama E2E | `VERIFIED` | 실제 OpenSQL 직접 `5432`에서 API·브라우저 E2E, TXT/PDF 원문 검색, 두 사용자 격리와 격리 DB의 OpenSQL opt-in integration test 통과 |
| OpenProxy | TCP `VERIFIED`; SQL routing `NOT_VERIFIED`; 인증 `AUTH_BLOCKED`; 적용 `DEFERRED` | Windows Host-only `6432` 연결은 확인했지만 안전한 backend 인증 방식을 확인하지 못함 |
| OpenHA·DB failover·영구 journal | `DEFERRED` | PRZ-005 핵심 완료 범위와 분리한 후속 작업 |
| PRZ-004 demo `USER` clean-clone | `VERIFIED` | `25d09e9`에서 자동 검증 `339 PASS / 18 SKIP / 0 FAIL`과 두 독립 clone 통과. `aff3e87` 경로 교정 뒤 Windows·Linux Node test와 GitHub CI 6건 통과, PR #25 merge `1f9a5ad`. 두 번째 빈 목록 UI 직접 관찰은 `NOT_RUN` |

세부 실행 환경과 명령은 [PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md),
[PRZ-002 Evidence](../specs/PRZ-002-open-source-readiness/evidence.md),
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md),
[PRZ-004 Evidence](../specs/PRZ-004-clean-clone-demo/evidence.md),
[PRZ-005 실제 OpenSQL 통합 작업 보고서](../specs/PRZ-005-opensql-ollama-e2e/implementation-report.md)에서
확인합니다. PostgreSQL·pgvector 결과를 OpenSQL 결과로 바꾸어 표현하지 않습니다.

## 미구현 기능

- OpenProxy의 안전한 인증과 SQL routing, 애플리케이션 적용
- OpenHA와 DB 장애 전환, 영구 journal
- 변경 로그 기반 동기화와 MCP 검색 API
- CareerFact 후보·확인·거절과 `INSUFFICIENT_EVIDENCE`
- 검증된 CareerFact를 이용한 JSON·Markdown portfolio와 source manifest
- `/api/v1`, OpenAPI, webhook/outbox
- 독립 Engine artifact와 기관용 workspace·권한

## 알려진 한계

- PRZ-004 두 번째 환경의 빈 문서 목록은 API로 확인했으며 브라우저에서 직접
  관찰하지는 않았습니다.
- 전체 처리 시간과 버전당 최대 chunk 수를 제한하지 않습니다.
- 프런트엔드 자동 UI 테스트가 없습니다.
- V13의 일부 제약과 기존 데이터 보정 전용 회귀 테스트가 없습니다.
- 일부 JavaDoc이 TXT/PDF 공통 동작을 TXT 전용으로 설명합니다.
- 일부 파일시스템에서는 안전 조건을 충족하지 못해 자동 파일 정리를 중단합니다.
- 실제 OpenSQL 직접 `5432`의 API·브라우저 전체 흐름은 검증했지만 OpenProxy SQL
  routing과 안전한 인증은 검증하지 못했습니다. OpenHA·DB 장애 전환과 영구
  journal은 후속 범위입니다.

## 다음 우선순위

제품 개발 순서는 [개발 로드맵](roadmap.md)을 따릅니다. 다음 후보는 DB
장애복구입니다. 실제 다중 노드 환경과 공식 절차를 확보한 뒤 별도 Spec으로
착수하며, OpenProxy의 안전한 인증과 SQL routing도 공급사 지원 방식을 확인한
경우에만 검증합니다.
