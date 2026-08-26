# PRIZM 개발 로드맵

> 기준일: 2026-08-27

이 문서는 제품이 발전하는 순서만 설명합니다. 현재 구현과 검증 결과는
[현재 구현 현황](project-status.md), 기능별 근거는
[Spec Registry](../specs/README.md)를 따릅니다.

## 현재

PRIZM은 Spring Boot 애플리케이션과 React Career Vault Reference App으로 문서
업로드, 버전 관리, 비동기 임베딩과 원문 근거 검색을 제공합니다. 재사용 가능한
독립 Engine 패키지는 아직 아닙니다.

현재 대회 제품 초점은 문서를 넣으면 ChangeLog 기반 색인과 자동 임베딩을 거쳐
최신 `ACTIVE` 버전의 사용자별 근거를 찾아주는 자동화된 AI 문서 관리
플랫폼입니다.

소스 전용(source-only) 오픈소스 준비와 실제 OpenSQL single-node SQL Gate를
완료했습니다. 안전한 demo `USER`, 자동 검증과 두 독립 clean clone도 확인하고
PRZ-004 독립 감사와 GitHub 통합을 마쳤습니다.

PRZ-005에서는 Spring Boot와 Ollama `bge-m3`를 실제 OpenSQL `5432`에 직접
연결해 API·브라우저 E2E와 두 사용자 격리를 검증했습니다. 격리된 OpenSQL
integration test와 전체 회귀를 통과하고 PR #26으로 `main`에 통합했습니다.

PRZ-010에서는 문서 버전 생성 사실을 owner-scoped ChangeLog에 기록하고,
Dispatcher가 기존 `INDEXING` ProcessingJob으로 멱등 전달하는 최소 동기화 흐름을
완료했습니다. PostgreSQL, 실제 OpenSQL direct `5432` V14 SQL Gate와 실제
OpenSQL+Ollama V1→V2 E2E를 통과해 `VERIFIED`입니다.

PRZ-011에서는 문서 처리의 실제 단계·청크 진행 수·재시도 정보와 안전한 실패 원인을
owner-scoped API에 연결하고, 비종료 상태 polling과 종료 시 중지를 검증했습니다.
AUDIT 수정과 재-AUDIT 뒤 PR #41로 `main`에 통합해 `VERIFIED`입니다.

PRZ-013에서는 애플리케이션 실행 연결(runtime)은 OpenProxy `:6432`로 보내고,
Flyway는 OpenSQL Primary `:5432`에 직접 연결하도록 분리했습니다. `prizm_app`의 SQL
조회·쓰기와 TXT/PDF, 실제 Ollama `bge-m3`, 사용자별 격리의 필수 전체 흐름(E2E)을
통과해 단일 Primary 검증은 `VERIFIED`입니다.

PRZ-015에서는 기존 Career Evidence Search를 재사용하는 읽기 전용 MCP 도구를
추가했습니다. 공식 Java MCP Client와 실제 USER JWT를 사용했고, 단일 서버
OpenSQL/OpenProxy와 Ollama `bge-m3`에서 전체 흐름을 검증했습니다. REST와 MCP의 결과
일치, 사용자별 격리와 `ACTIVE` 버전 격리를 통과해 `VERIFIED`입니다.

## 다음

1. **검색 근거 신뢰성**
   - dense 검색 평가와 세 상태를 구분하는 개선 제품 profile을 기본값으로 승격했습니다.
     고정 TEST와 실제 OpenSQL direct `5432` API·UI Gate를 통과한 결과입니다.
   - UI와 청킹·색인 최적화는 같은 변경에 섞지 않고, 평가 Gate를 통과한 단계만
     별도 PR로 진행합니다.
2. **사용자 관리형 문서 태그**
   - PRZ-009 P4는 하드코딩 기술 사전 기반 자동 추출을 제거하고 SYSTEM 추천 tag와
     owner-scoped USER tag를 문서 metadata로 연결합니다.
   - upload·document detail은 같은 Tag Modal을 사용하고, 경력 키워드 화면은 실제 문서에
     연결된 tag와 문서 수를 집계합니다. 상세는 선택한 이름으로 기존 PRZ-016 Search를
     호출해 owner ACTIVE 전체 문서 evidence를 보여 주며 tag 연결 문서로 범위를 제한하지 않습니다.
    - [PRZ-009](../specs/PRZ-009-career-keyword-map/spec.md)는 현재 `VERIFIED`이고
      AUDIT Gate는 `PASS`입니다. 구현 commit `1c1d8d2`와 PR #51을 생성했고 merge 전입니다.
     PostgreSQL integration·frontend 검증은 통과했고 인증된 브라우저 흐름은
     `USER_CONFIRMED`입니다. 독립 재감사 blocking finding은 0건입니다.
3. **채용공고 항목별 Career Evidence V1 — `VERIFIED`**
   - 붙여넣은 채용공고를 LLM 없이 줄·목록·문장 경계로 분리하고 사용자가 section별
     modal에서 필요한 child 항목을 직접 선택합니다.
   - 단순 선택 항목은 원문으로, 명확한 alternative 항목은 원문과 제한된 deterministic
     variant로 기존 PRZ-016 Career Evidence Search를 호출합니다. owner의 `ACTIVE` 문서
     Evidence는 원래 항목 하나로 병합합니다. 결과 전용 route의 requirement rail에서
     검색 후보 있음·검색된 후보 없음을 나눠 항목을 전환하고 문서별 Evidence row에서 PDF page 또는 TXT 문서
     상세로 이동합니다.
   - 적합도·충족 여부·합격 가능성을 판정하지 않으며 채용공고 persistence, Tag filter,
     migration과 Search algorithm 변경은 V1 범위가 아닙니다.
   - [PRZ-017](../specs/PRZ-017-job-posting-evidence-v1/spec.md)의 구현 기준은 source commit
     `94715cf`와 [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53)입니다. 2026-08-26 frontend focused 33/33·전체 80/80,
     typecheck·lint·build와 backend 전체 89 suites·627 tests가 실패·오류 0, 기존 조건부 test
     20건 skip으로 통과했습니다. integration은 실행 중 중단되어 `ABORTED`입니다. 2026-08-27
     최종 segmentation focused 50/50·compile과 인증 desktop browser의 mixed bullet 10→11,
     Evidence 2개 항목, PDF 2페이지 target을 확인해 V1을 `VERIFIED`로 판정했습니다. TXT 이동,
     PayPay India·Lean In 재평가와 mobile viewport는 최종 범위 밖이어서 `NOT_RUN`입니다.

다중 OpenSQL DB node, DB 장애전환, OpenProxy 이중화·VIP와 서비스 연속성 보장은
로드맵에 포함하지 않습니다.

## 향후

CareerFact는 완료된 clean-clone·OpenSQL 전체 흐름, 변경 로그 동기화와 MCP 검색
Gate에 이어 검색 근거 신뢰성의 남은 Gate를 통과한 뒤 시작합니다.
첫 범위는 원문 조각과 연결된 최소 후보·확인·거절 흐름입니다.

Portfolio 생성은 검증된 CareerFact 이후에 진행합니다. 확인되지 않은 경력이나
수치를 만들지 않고, 결과에서 원문 출처를 다시 확인할 수 있어야 합니다.

그다음 제품 확장 후보는 다음과 같습니다.

- 교체 가능한 parser, chunker, embedding, vector DB와 storage adapter
- canonical source와 처리 provenance
- `/api/v1`, OpenAPI와 webhook/outbox
- 독립 Engine artifact와 멀티모듈 패키징
- 기관용 workspace, profile, membership와 권한

각 기능은 실제 착수할 때 작은 Spec으로 정의합니다. 단계별 작업·중단·검증 규칙은
[AGENTS.md](../AGENTS.md)를 따릅니다.
