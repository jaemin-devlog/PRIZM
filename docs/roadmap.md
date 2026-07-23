# PRIZM 개발 로드맵

> 기준일: 2026-07-23
>
> 현재 단계: 기존 구현 기준선 완료, 대회 대응 P0 진행 중

이 문서는 앞으로의 개발 순서만 짧게 관리한다. 현재 구현은
[현재 구현 현황](project-status.md), 세부 대회 일정은
[티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md), 요구사항별
상태와 증거는 [Spec Registry](../specs/README.md)를 기준으로 한다.

## 현재 위치

| 단계 | 상태 | 완료 기준 |
|---|---|---|
| 기존 구현 기준선 | `COMPLETE` | `PRZ-000 AS_BUILT_BASELINE`, `main` 단일 브랜치, 핵심 문서 동기화 |
| P0 공식 요구사항·오픈소스 준비 | `IN_PROGRESS` | 공식 요구사항 추적표와 라이선스 감사, 거버넌스 문서 |
| P1 OpenSQL·clean-clone 증명 | `NOT_STARTED` | 실제 OpenSQL Gate와 새 환경 재현 |
| P2 CareerFact 최소 수직 슬라이스 | `NOT_STARTED` | 근거가 연결된 후보·확인·거절 흐름 |
| P3 근거 기반 portfolio | `NOT_STARTED` | 확인된 CareerFact만 사용하는 최소 출력 |
| P4 제출 증거·동결 | `NOT_STARTED` | 추적표, 보고서, 영상, clean-clone 최종 검증 |

## 다음 작업 순서

1. 공식 지정과제 원문과 오리엔테이션 평가기준을 확보한다.
2. 요구사항을 구현·test·실행환경·제출 증거에 연결하는 추적표를 만든다.
3. 의존성, `bge-m3`, 합성 데이터와 OpenSQL 구성요소의 라이선스를 감사한다.
4. 감사 결과에 따라 LICENSE와 기여·보안 문서를 추가한다.
5. 실제 착수 시점에 `PRZ-001-opensql-vector-gate`를 만들고 OpenSQL 실환경을 검증한다.
6. `PRZ-002-clean-clone-demo`에서 안전한 demo `USER`와 재현 절차를 완성한다.
7. 두 Gate가 통과한 뒤 `PRZ-003-career-fact-slice`를 시작한다.
8. 검증된 CareerFact가 생긴 뒤에만 `PRZ-004-grounded-portfolio-slice`를 시작한다.
9. 마지막으로 `PRZ-005-submission-audit`에서 제출 범위와 증거를 고정한다.

## 대회 이후 확장

다음 항목은 위 수직 슬라이스와 제출 검증을 완료한 뒤 판단한다.

- 교체 가능한 parser, chunker, embedding, vector DB와 storage adapter
- canonical source와 처리 provenance
- `/api/v1`, OpenAPI, webhook/outbox와 MCP
- 독립 Engine artifact와 멀티모듈 패키징
- 기관용 workspace, profile, membership와 권한
- OpenProxy·OpenHA 장애전환

## 진행 규칙

- 한 번에 하나의 작은 `PRZ-###` spec만 착수한다.
- 구현 전 `spec.md`, 변경 전 `plan.md`, 실행 단위는 `tasks.md`에 기록한다.
- 완료 판단은 checkbox가 아니라 source, migration, test와 실행환경 evidence로 한다.
- 검증하지 않은 OpenSQL·OpenProxy·OpenHA 호환성을 주장하지 않는다.
- CareerFact와 portfolio가 검증되지 않으면 현재 업로드·검색·근거 확인만 데모한다.

과거의 0~10단계 상세 계획과 실행 프롬프트는
[보관된 오픈소스 전환 계획](archive/oss-transition-execution-plan.md)에 남겨 둔다.
