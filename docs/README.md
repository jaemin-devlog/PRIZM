# PRIZM 문서 안내

이 디렉터리는 현재 상태, 실행 방법, 개발 계획, 검증 자료와 과거 기록을
구분합니다. 문서와 구현이 다르면 소스 코드(source code), Flyway migration과
실행 가능한 테스트(test)를 우선합니다.

현재 제품 문서의 중심은 문서 업로드부터 자동 임베딩, ChangeLog 동기화와
사용자별 근거 검색까지 연결하는 자동화된 AI 문서 관리 플랫폼입니다. MCP 검색은
로드맵에 있는 다음 인터페이스이며 현재 구현으로 표시하지 않습니다.

## 독자별 탐색 경로

| 독자 | 먼저 읽을 문서 | 이어서 볼 문서 |
|---|---|---|
| 처음 방문한 사용자 | [프로젝트 소개](../README.md) | [현재 구현 현황](project-status.md), [개발 로드맵](roadmap.md) |
| 로컬에서 실행하려는 개발자 | [로컬 Quickstart](quickstart.md) | [OpenSQL 기술 Gate](opensql-gate.md) |
| 프로젝트 구조를 이해하려는 개발자 | [Architecture](architecture.md) | [현재 구현 현황](project-status.md), [대표 문제 해결 사례](showcase/problem-solving-case-studies.md), [검색 품질 평가](evaluation/search-evaluation.md) |
| 주요 변경과 설계 판단의 흐름을 보려는 사람 | [개발 기록](development-log.md) | [Spec Registry](../specs/README.md), [현재 구현 현황](project-status.md) |
| 실제 기여자와 AI 에이전트 | [핵심 프로젝트 규칙](../AGENTS.md) | [상세 Contributor Workflow](ai-agent-workflow.md), [Spec Registry](../specs/README.md) |
| 검증 근거를 확인하는 사람 | [Spec Registry](../specs/README.md) | [현재 구현 현황](project-status.md), [SBOM 안내](../sbom/README.md) |
| 과거 기록을 찾는 사람 | [`archive/`](archive/) | 종료된 기술 실험과 초기 검증 기록 |

## 공통 상태 코드

상태 코드는 실제 의미를 먼저 확인한 뒤 사용합니다.

| 상태 | 쉬운 설명 |
|---|---|
| `PASS` | 기록된 commit과 환경에서 해당 명령이나 기능을 실제로 실행해 통과했습니다. |
| `SKIPPED` | 명시적인 환경 조건 때문에 검증이 실행되지 않았습니다. 통과 증거가 아닙니다. |
| `NOT_RUN` | 해당 환경이나 명령을 실행하지 않았습니다. |
| `NOT_VERIFIED` | 일부 상태는 확인했지만 목표 기능이 동작한다고 판정할 증거가 부족합니다. |
| `HISTORICAL_PASS_NOT_RERUN` | 과거에는 통과했지만 현재 기준선에서는 다시 실행하지 않았습니다. |
| `AS_BUILT_BASELINE` | Spec Registry를 만들기 전에 이미 구현돼 있던 기능을 현재 소스 기준으로 기록한 역사적 기준선입니다. |
| `IN_PROGRESS` | 구현 또는 검증이 진행 중이며 최종 Gate가 끝나지 않았습니다. |
| `IMPLEMENTED_UNVERIFIED` | 구현은 있지만 필수 환경 검증이 끝나지 않았습니다. |
| `VERIFIED` | Spec의 요구사항과 필수 자동·환경 검증을 모두 충족했습니다. |
| `DEFERRED` | 지금은 진행하지 않으며, 이유와 다시 시작할 조건을 기록했습니다. |

## 보관 문서

`archive/`는 종료된 실험과 초기 검증 기록입니다. 현재 구현이나
현재 개발 순서를 판단하는 기준으로 사용하지 않습니다.

- [BGE Reranker 비채택 실험](archive/experiments/2026-07-14-bge-reranker-evaluation.md)
- [초기 문서 등록 검증](archive/verification/2026-07-13-minimal-document-registration.md)
- [초기 벡터 검색 검증](archive/verification/2026-07-13-minimal-vector-search.md)
