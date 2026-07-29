# PRIZM 개발 로드맵

> 기준일: 2026-07-28
>
> 현재 단계: 기존 구현 기준선 완료, 대회 대응 P0 진행 중

이 문서는 앞으로의 개발 순서만 관리한다. 현재 구현은
[현재 구현 현황](project-status.md), 대회 세부 계획은
[티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md), 공식 항목별
상태는 [요구사항·평가기준 추적표](contest/2026-requirements-traceability.md),
기능별 증거는 [Spec Registry](../specs/README.md)를 기준으로 한다.

## 현재 위치

| 단계 | 상태 | 완료 기준 |
|---|---|---|
| 기존 구현 기준선 | `COMPLETE` | `PRZ-000 AS_BUILT_BASELINE`, `main` 단일 장기 브랜치, 핵심 문서 동기화 |
| P0 공식 기준·오픈소스 준비 | `IN_PROGRESS` | source register·Apache-2.0·NOTICE·SBOM·AI 명세 완료; T-08의 문서화된 Compose·health 범위 `VERIFY` 완료, PRZ-002 최종 독립 감사·검증 CI 남음. demo `USER` 기반 전체 흐름은 `NOT_RUN` |
| P1 OpenSQL·clean-clone | `IN_PROGRESS_OPENSQL_GATE_VERIFIED` | PRZ-003 Rocky Linux 9.7 Single 설치·라이선스 적용과 실제 PRIZM OpenSQL 단일 SQL Gate·독립 AUDIT `PASS`; demo `USER` 기반 clean-clone 전체 사용자 흐름과 OpenProxy·OpenHA는 `NOT_RUN` 또는 `NOT_VERIFIED` |
| P2 DB 장애복구 Gate | `NOT_STARTED` | 실제 다중 노드 장애전환과 서비스 연속성 evidence |
| P3 변경 로그 동기화·MCP | `NOT_STARTED` | 멱등 동기화와 owner-scoped 읽기 전용 MCP 검색 |
| P4 PRIZM 차별 slice | `NOT_STARTED` | 앞선 Gate 통과 뒤 source가 연결된 최소 CareerFact |
| P5 제출 증거 | `NOT_STARTED` | 라이선스, 기능시험, 보고서, 3분 영상, source archive |
| P6 동결 | `NOT_STARTED` | clean-clone 최종 검증과 제출 범위 일치 |
| P7 1차 평가·멘토링 준비 | `NOT_STARTED` | 제출 버전 보존, 서면평가 대응, 멘토링 수요조사 |
| P8 기업 멘토링·보완 | `NOT_STARTED` | feedback 추적과 전체 재검증 |
| P9 외부 기능·라이선스 검증 | `NOT_STARTED` | finding 수정·재검증과 검증 패키지 확정 |
| P10 2차 발표 | `NOT_STARTED` | OSS 표기 PPT, demo, 10분 발표·5분 Q&A |

## 다음 작업 순서

1. PRZ-002 T-08에서 README·Quickstart·문서 색인을 현재 source-only
   Apache-2.0·NOTICE·SBOM·AI 모델 명세와 맞춘다.
2. T-09에서 라이선스·SBOM 재생성·구조·drift 검증을 CI로 고정하고,
   T-10에서 최종 독립 감사를 통과한다.
3. 설치가 완료된 PRZ-003 OpenSQL 단일 환경에서 전용 credential을 준비하고
   migration·vector·검색·Worker SQL Gate를 실행한다.
4. Clean Clone Demo spec에서 안전한 demo `USER`와 재현 절차를 완성한다.
5. 실제 다중 노드 구성을 확보해 DB 장애전환·검색 복구 Gate를 수행한다.
6. 변경 로그 기반 동기화의 최소 수직 슬라이스를 구현·검증한다.
7. 기존 검색을 재사용하는 owner-scoped 읽기 전용 MCP 도구를 구현·검증한다.
8. 위 Gate가 통과한 뒤에만 최소 CareerFact를 시작한다.
9. 제출 감사와 동결에서 기능·라이선스·보고서·영상·source 범위를 고정한다.
10. 제출 뒤 1차 평가, 기업 멘토링, 외부 기능·라이선스 검증과 2차 발표까지
    같은 evidence chain을 유지한다.

Portfolio는 검증된 CareerFact 이후의 제품 계획이다. 대회 핵심 Gate가
미완료인 상태에서는 먼저 개발하지 않는다.

CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, SUPPORT와 maintainer 정책은
미구현 상태로 `DEFERRED`한다. 외부 기여 접수 또는 첫 지원 release·외부 배포를
준비하는 시점 중 먼저 도래하는 때 실제 비공개 보안 신고 경로부터 확정한 뒤
재개한다. GitHub Issue Form·PR Template은 외부 Issue·PR 접수를 공식
지원하기 전에 재개한다.

## 2차 발표 이후 제품 확장 후보

- 근거 기반 portfolio와 source manifest
- 교체 가능한 parser, chunker, embedding, vector DB와 storage adapter
- canonical source와 처리 provenance
- `/api/v1`, OpenAPI, webhook/outbox
- 독립 Engine artifact와 멀티모듈 패키징
- 기관용 workspace, profile, membership와 권한

단계별 실행·중단·문서·branch 규칙은 [AGENTS.md](../AGENTS.md)만 따른다.

과거의 0~10단계 상세 계획과 실행 프롬프트는
[보관된 오픈소스 전환 계획](archive/oss-transition-execution-plan.md)에 남겨
둔다.
