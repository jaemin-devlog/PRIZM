# PRIZM 개발 로드맵

> 기준일: 2026-08-13

이 문서는 제품이 발전하는 순서만 설명합니다. 현재 구현과 검증 결과는
[현재 구현 현황](project-status.md), 기능별 근거는
[Spec Registry](../specs/README.md)를 따릅니다.

## 현재

PRIZM은 Spring Boot 애플리케이션과 React Career Vault Reference App으로 문서
업로드, 버전 관리, 비동기 임베딩과 원문 근거 검색을 제공합니다. 재사용 가능한
독립 Engine 패키지는 아직 아닙니다.

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

## 다음

1. **검색 근거 신뢰성**
   - dense 검색 평가와 세 상태를 구분하는 개선 제품 profile을 기본값으로 승격했습니다.
     고정 TEST와 실제 OpenSQL direct `5432` API·UI Gate를 통과한 결과입니다.
   - UI와 청킹·색인 최적화는 같은 변경에 섞지 않고, 평가 Gate를 통과한 단계만
     별도 PR로 진행합니다.
2. **경력 키워드 맵**
   - 현재 사용자의 `ACTIVE` 이력서·포트폴리오 원문에서 실제로 확인한 기술을 정규화하고,
     category와 세 순위 기준으로 표시하며 문서별 근거와 TXT/PDF 원본 위치에 연결합니다.
   - [PRZ-009](../specs/PRZ-009-career-keyword-map/spec.md)는 소스 구현, 단위·정적 검증,
     전체 PostgreSQL integration, synthetic browser와 최종 감사를 마쳤습니다. OpenSQL
     opt-in target이 `NOT_RUN`이므로 상태는 `IMPLEMENTED_UNVERIFIED`이며, 결과를
     CareerFact나 검증된 숙련도 판정으로 사용하지 않습니다.
3. **DB 장애 전환**
   - 실제 다중 노드 구성을 확보한 뒤 장애 주입, 애플리케이션 재연결과 검색 복구를
     측정합니다.
   - OpenProxy·OpenHA는 실제 사용하고 검증한 경우에만 결과에 적습니다.
4. **MCP 검색**
   - 현재 Career Evidence 검색을 재사용하는 읽기 전용 MCP 도구를 만듭니다.
   - 사용자 격리, 원문 출처와 근거 없음 응답을 기존 REST 계약과 함께 검증합니다.

## 향후

CareerFact는 완료된 clean-clone·OpenSQL 전체 흐름과 변경 로그 동기화에 이어,
검색 근거 신뢰성, DB 장애 전환과 MCP 검색의 필수 Gate를 통과한 뒤 시작합니다.
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
