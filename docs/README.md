# PRIZM 문서 안내

이 페이지는 제품 소개에서 실행, 구조, 기여와 상세 검증 근거로 이어지는 문서 허브입니다. 문서와 구현이 다르면 소스 코드, 적용된 Flyway migration과 실행 가능한 test를 우선합니다.

## 가장 빠른 경로

| 목적 | 문서 |
|---|---|
| PRIZM을 처음 살펴보기 | [프로젝트 README](../README.md) |
| 로컬에서 실행하기 | [Quick Start](quickstart.md) |
| 구성 요소와 데이터 흐름 이해하기 | [Architecture](architecture.md) |
| 구현 범위와 한계 확인하기 | [Current Status](project-status.md) |
| 기여 시작하기 | [Contributing](../CONTRIBUTING.md) |
| 보안 문제 신고하기 | [Security](../SECURITY.md) |

## 주제별 문서

| 주제 | 문서 | 용도 |
|---|---|---|
| 실행 | [Quick Start](quickstart.md) | PostgreSQL·pgvector 기반 로컬 실행과 MCP 연결 |
| 구조 | [Architecture](architecture.md) | 인증, 문서 버전, 색인, 검색, MCP와 복구 계약 |
| 현재 상태 | [Project Status](project-status.md) | 구현된 기능, 검증된 환경, 미구현과 범위 제외 |
| OpenSQL | [OpenSQL Gate](opensql-gate.md) | 단일 서버 OpenSQL·OpenProxy 검증 경계 |
| 검색 품질 | [Search Evaluation](evaluation/search-evaluation.md) | 검색 평가 방법과 지표 해석 |
| 설계 사례 | [Problem-solving Case Studies](showcase/problem-solving-case-studies.md) | 주요 설계 판단과 트레이드오프 |
| 제품 방향 | [Product Direction](roadmap.md) | 현재 구현 범위와 일정이 정해지지 않은 장기 방향 |
| 상세 근거 | [Spec Registry](../specs/README.md) | PRZ별 Spec, 계획과 Evidence 색인 |
| 라이선스 | [SBOM Guide](../sbom/README.md) | 구성 요소, 모델, checksum과 재배포 경계 |

## 유지관리자용 문서와 역사 기록

다음 문서는 일반 사용자가 제품을 이해하거나 실행하는 데 필요하지 않습니다. 운영 또는 추적성이 필요할 때만 확인합니다.

- [Maintainer and AI Agent Workflow](ai-agent-workflow.md) — 복잡한 변경을 위한 선택적 상세 절차. 일반 기여의 필수 문서가 아님
- [Development Log](development-log.md) — 주요 변경과 설계 판단 기록
- [OpenSQL VM Runbook](lab-opensql-vm-runbook.md) — OpenSQL 검증 환경을 재현하는 유지관리자용 절차
- [`archive/`](archive/) — 종료된 실험과 초기 검증 기록

## 상태 표기

`VERIFIED`는 Spec의 필수 검증을 충족했다는 뜻입니다. `PASS`는 기록된 source와 환경에서 특정 검사를 실제로 통과했다는 뜻이며, 다른 환경으로 확대해 해석하지 않습니다. `NOT_RUN`과 `NOT_VERIFIED`는 통과 증거가 아닙니다. 전체 정의와 현재 목록은 [Spec Registry](../specs/README.md)를 따릅니다.
