# PRIZM 개발 로드맵

> 기준일: 2026-08-01

이 문서는 제품이 발전하는 순서만 설명합니다. 현재 구현과 검증 결과는
[현재 구현 현황](project-status.md), 대회 일정과 P0~P10 실행 단계는
[티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md), 기능별 근거는
[Spec Registry](../specs/README.md)를 따릅니다.

## 현재

PRIZM은 Spring Boot 애플리케이션과 React Career Vault Reference App으로 문서
업로드, 버전 관리, 비동기 임베딩과 원문 근거 검색을 제공합니다. 재사용 가능한
독립 Engine 패키지는 아직 아닙니다.

소스 전용(source-only) 오픈소스 준비와 실제 OpenSQL single-node SQL Gate를
완료했습니다. 안전한 demo `USER`, 자동 검증과 두 독립 clean clone도 확인하고
PRZ-004 독립 감사와 GitHub 통합을 마쳤습니다.

## 다음

1. **OpenSQL 전체 사용자 흐름**
   - Spring Boot와 Ollama를 실제 OpenSQL에 연결합니다.
   - 업로드→임베딩→검색을 한 환경에서 검증하고 단일 SQL Gate와 구분해 기록합니다.
2. **DB 장애 전환**
   - 실제 다중 노드 구성을 확보한 뒤 장애 주입, 애플리케이션 재연결과 검색 복구를
     측정합니다.
   - OpenProxy·OpenHA는 실제 사용하고 검증한 경우에만 결과에 적습니다.
3. **변경 로그 동기화**
   - 문서와 버전 변경을 누락이나 중복 없이 검색 데이터에 반영하는 최소 흐름을
     구현합니다.
4. **MCP 검색**
   - 현재 Career Evidence 검색을 재사용하는 읽기 전용 MCP 도구를 만듭니다.
   - 사용자 격리, 원문 출처와 근거 없음 응답을 기존 REST 계약과 함께 검증합니다.

## 향후

CareerFact는 clean-clone, OpenSQL 전체 흐름, DB 장애 전환, 변경 로그 동기화와
MCP 검색의 필수 Gate를 통과한 뒤 시작합니다. 첫 범위는 원문 조각과 연결된 최소
후보·확인·거절 흐름입니다.

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
