# 2026 오픈소스 개발자대회 티맥스티베로 과제 대응 계획

> 기준일: 2026-07-23
> 목표: 구현하지 않은 기능과 검증하지 않은 환경을 주장하지 않으면서, 2026-08-27 제출물에서 PRIZM의 실행 가능성·재사용성·검증 근거를 보여준다.

## 결론

첨부 피드백의 큰 방향은 적합하다.

- 화려한 UI보다 재현 가능한 설치·실행·검증과 오픈소스 협업 근거를 우선한다.
- PostgreSQL·pgvector·BGE-M3 기반을 버리지 않고 OpenSQL 실제 환경에서 먼저 증명한다.
- 모든 결과를 원문 source와 연결하고 찾지 못한 근거를 만들지 않는다.
- 거대한 제품 개편보다 작은 수직 슬라이스를 spec, code, test, evidence로 완결한다.

다만 현재 구현보다 앞선 표현과 공개 근거가 확인되지 않은 수치는 수정해야 한다.

## 피드백 판정

| 제안 | 판정 | PRIZM 적용 |
|---|---|---|
| Career Intelligence Engine과 Reference App으로 설명 | 채택 | 현재 공식 제품 경계를 유지한다. |
| OpenSQL+pgvector를 과제 핵심으로 사용 | 채택, 검증 조건부 | OpenSQL 실환경 Gate가 PASS한 뒤에만 “OpenSQL에서 동작”이라고 표현한다. |
| 업로드→구조화→검색→portfolio 전체를 현재 데모로 제시 | 수정 | 업로드·색인·검색·근거 확인은 구현됨. CareerFact와 portfolio는 작은 신규 수직 슬라이스가 완료될 때만 추가한다. |
| 1차 30점·2차 70점과 세부 배점 | 공식 가이드 확인 필요 | 공개 대회 페이지는 일정·제출물·기능/라이선스 검증은 확인되지만 세부 배점은 7월 23일 오리엔테이션 자료를 기준으로 다시 확인한다. |
| Recall@5 80%, 출처 정확도 95% 같은 목표 | 측정 후 결정 | 사전 목표를 성과처럼 쓰지 않는다. 고정 데이터·계산식·실제 결과·한계를 함께 공개한다. |
| Apache-2.0이 가장 적합 | 라이선스 감사 후 결정 | 현재 root LICENSE가 없다. 의존성·모델·fixture·OpenSQL 재배포 조건을 먼저 확인한다. |
| 여러 DB adapter 동시 개발 | 제외 | OpenSQL 단일 환경 Gate와 현재 PostgreSQL 경로 보존에 집중한다. |
| 과거 Issue·PR을 새로 생성 | 제외 | 과거는 commit·개발 기록으로, 이후 작업만 실제 Issue·PR로 남긴다. |

## 지금 사용할 제품 설명

> PRIZM은 커리어 문서의 원문과 버전을 보존하고, 사용자별 비동기 처리와 출처가 연결된 검색을 제공하는 오픈소스 Career Intelligence Engine 및 Reference App이다. 현재 PostgreSQL·pgvector 기반 플랫폼과 Career Vault를 구현했으며, OpenSQL은 실제 환경 검증 Gate를 준비한 상태다. CareerFact 구조화와 근거 기반 portfolio 생성은 후속 수직 슬라이스다.

OpenSQL Gate가 실제 PASS하고 evidence가 저장된 뒤에만 “OpenSQL 기반” 또는 “OpenSQL에서 검증됨”으로 문구를 강화한다.

## 확인된 대회 사실

[오픈소스 포털 대회 페이지](https://www.oss.kr/pages/2)는 다음을 공개한다.

- 출품작 마감: 2026-08-27
- 제출물: 결과보고서, 3분 시연영상, 소스코드
- 1차 서면평가: 2026-09-03
- 멘토링: 2026-09-18~10-09
- 기능·라이선스 검증: 2026-10-12~10-28
- 발표평가: 2026-11-04

[티맥스티베로 공식 안내](https://www.linkedin.com/company/tmaxtibero)는 지정과제를 PostgreSQL 기반 OpenSQL을 활용한 AI 검색·벡터 데이터 시스템으로 설명한다. [티맥스티베로 기술 글](https://blog.tibero.com/ai-era-dbms-strategy/)은 OpenSQL의 pgvector와 cosine distance(`<=>`) 사용을 안내한다.

정확한 평가 배점, 지정과제 추가 필수조건과 제출 형식은 오리엔테이션 자료와 운영사무국 안내를 최종 기준으로 삼는다.

## Spec Kit과 robo-architect에서 가져올 것

[GitHub Spec Kit](https://github.github.com/spec-kit/)의 `Spec → Plan → Tasks → Implement` 흐름은 선택 도입한다.

- `spec.md`: 사용자 시나리오, 범위·비범위, Given/When/Then, 기능 요구사항과 측정 가능한 성공조건
- `plan.md`: 변경 경계, migration·API·보안 영향, PRIZM 불변식과 검증 환경
- `tasks.md`: 요구사항 ID, 정확한 파일 경로, 테스트·문서 작업과 의존성
- `evidence.md`: 요구사항→source/migration/test/commit/environment/result 연결

Spec Kit의 Git branch 확장과 auto-commit은 사용하지 않는다. 기본 템플릿에서 선택 사항인 테스트는 PRIZM에서는 필수다. `AGENTS.md`를 규범 원본으로 유지하고 별도 constitution에 같은 규칙을 중복하지 않는다. 기존 저장소에 `specify init --here --force`를 바로 실행하지 않고, 수직 슬라이스 하나로 유지비를 먼저 평가한다.

[robo-architect specs](https://github.com/uengine-oss/robo-architect/tree/main/specs)에서는 P1/P2 독립 시나리오, 리스크·비목표, 단계별 검증 Gate와 자동/수동/미실행 evidence 분리를 참고한다. 대규모 DDD·bounded context 문서와 수백 개 파일 구조는 가져오지 않는다. spec checkbox를 구현 증거로 취급하지 않고 source·Flyway·실행 가능한 test를 최종 진실로 유지한다.

## PRIZM용 최소 spec 구조

```text
specs/
├── README.md
├── PRZ-000-platform-baseline/
│   ├── spec.md
│   └── evidence.md
├── PRZ-001-opensql-vector-gate/
│   ├── spec.md
│   ├── plan.md
│   ├── tasks.md
│   └── evidence.md
├── PRZ-002-clean-clone-demo/
├── PRZ-003-career-fact-slice/
├── PRZ-004-grounded-portfolio-slice/
└── PRZ-005-submission-audit/
```

`PRZ-000`은 과거 작업을 사전 명세로 꾸미지 않고 `AS_BUILT_BASELINE`임을 표시한다. 이 상태는 현재 구현의 사후 기준선이며, 존재하지 않았던 과거 Issue·PR을 만들지 않는다. 각 spec은 `Status`, `Source commit`, `Last verified`와 PostgreSQL·OpenSQL·Ollama·Docker별 `PASS | FAIL | NOT_RUN | HISTORICAL_PASS_NOT_RERUN`을 기록한다. branch 이름은 식별자로 사용하지 않는다.

## 제출 전 우선순위

### P0. 사실과 저장소 기반선 — 7월 23~27일

- `main`만 남기는 브랜치 정리와 채택·비채택 근거 보존
- 오리엔테이션 평가기준·지정과제 원문 확보와 요구사항 traceability 작성
- LICENSE, NOTICE/third-party, CONTRIBUTING, SECURITY, Issue·PR template의 실제 라이선스 검토
- `PRZ-000`, `PRZ-001`, `PRZ-002` 작성

### P1. OpenSQL와 clean-clone 증명 — 7월 28일~8월 3일

- 전용 OpenSQL DB/schema에서 V1~V13 migration 실행
- `vector(1024)`, 실제 cosine 검색, owner·ACTIVE 조건, claim·lease·fencing·`SKIP LOCKED` 검증
- 결과를 PostgreSQL 결과와 분리한 `evidence.md`에 기록
- 안전한 demo `USER`, 합성 TXT/PDF와 로그인→업로드→ACTIVE→검색 재현 절차 완성
- 두 번째 깨끗한 환경에서 Quickstart 재현

OpenProxy와 OpenHA는 OpenSQL 단일 Gate를 통과한 뒤 별도 범위로 판단한다.

### P2. PRIZM 차별성의 최소 수직 슬라이스 — 8월 4~10일

- `PROJECT`와 `SKILL` 두 종류만 다루는 CareerFact 후보·확인·거절 흐름
- 모든 fact에 source chunk와 문서·version·페이지/구간 연결
- 근거 부족은 `INSUFFICIENT_EVIDENCE`로 반환
- 다른 사용자의 source, 실패·비활성 version이 fact 후보에 들어오지 않는 통합 테스트

전체 이력서 자동 분석, 복잡한 taxonomy와 기관 workflow는 제외한다.

### P3. 근거 기반 출력과 평가 — 8월 11~17일

- 확인된 CareerFact만 사용하는 최소 JSON·Markdown portfolio와 source manifest
- 문서에 없는 기술·성과·수치를 만들지 않는 negative test
- 합성 평가 데이터, 계산식, 결과와 한계 공개
- Dense 검색 기준선 재실행; Reranker는 현재 비채택 결정 유지

### P4. 제출 증거 — 8월 18~23일

- README 첫 화면, architecture/data-flow, API, OpenSQL 설정, troubleshooting 정리
- SBOM·dependency/model/data license 감사와 비밀정보 검사
- 요구사항→code→test→demo 장면 traceability 완성
- 문제→해결→검증→확장 순서의 결과보고서와 3분 영상 제작

### P5. 동결 — 8월 24~27일

- 기능 추가 중단
- clean clone에서 전체 필수 검증
- 보고서·영상·README·실제 화면의 기능 범위 일치 확인
- 치명적 finding이 없을 때만 `v0.1.0-contest` tag와 Release 생성

## 중단 기준

- OpenSQL 실환경을 확보하지 못하면 결과를 `NOT_RUN`으로 남기고 PostgreSQL 성공으로 대체하지 않는다.
- 모델·데이터·OpenSQL 구성요소의 재배포 조건이 불명확하면 저장소에 포함하지 않고 공식 다운로드 절차만 제공한다.
- 8월 10일까지 CareerFact 수직 슬라이스가 검증되지 않으면 현재 업로드·검색·근거 확인만 데모하고 portfolio 생성을 구현 기능처럼 보여주지 않는다.
- 모바일, 소셜, 통계 dashboard, 여러 DB adapter, 전체 관리자 화면과 자체 모델 학습은 대회 핵심 범위에서 제외한다.
