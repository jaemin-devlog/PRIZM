# PRIZM 문서 안내

이 페이지는 제품 소개에서 실행, 구조, 기여와 상세 검증 기록으로 이어지는 길잡이입니다. 문서와 구현이 다르면 소스 코드, 적용된 Flyway migration과 실행 가능한 test를 우선합니다.

## 가장 빠른 경로

| 목적 | 문서 |
|---|---|
| PRIZM을 처음 살펴보기 | [프로젝트 README](../README.md) |
| 로컬에서 실행하기 | [빠른 시작](quickstart.md) |
| 구성 요소와 데이터 흐름 이해하기 | [아키텍처](architecture.md) |
| 구현 범위와 한계 확인하기 | [현재 구현 현황](project-status.md) |
| 기여 시작하기 | [기여 안내](../CONTRIBUTING.md) |
| 보안 문제 신고하기 | [보안 정책](../SECURITY.md) |

## 주제별 문서

| 주제 | 문서 | 용도 |
|---|---|---|
| 실행 | [빠른 시작](quickstart.md) | PostgreSQL·pgvector 기반 로컬 실행과 MCP 연결 |
| 구조 | [아키텍처](architecture.md) | 인증, 문서 버전, 자동 문서 처리, 검색, MCP와 복구 방식 |
| 현재 상태 | [현재 구현 현황](project-status.md) | 구현된 기능, 검증된 환경과 지원 경계 |
| OpenSQL | [OpenSQL 검증 기록](opensql-gate.md) | 단일 서버 OpenSQL·OpenProxy 검증 범위 |
| 검색 품질 | [검색 평가](evaluation/search-evaluation.md) | 검색 평가 방법과 지표 해석 |
| 설계 사례 | [문제 해결 사례](showcase/problem-solving-case-studies.md) | 주요 설계 판단과 장단점 |
| 제품 범위 | [PRIZM 제품 범위](roadmap.md) | 현재 제품 정의와 새 변경을 시작하는 원칙 |
| 상세 근거 | [기능별 검증 기록](../specs/README.md) | PRZ별 Spec, 계획과 검증 기록 안내 |
| 라이선스 | [SBOM 안내](../sbom/README.md) | 구성 요소, 모델, checksum과 재배포 범위 |

## 유지관리자용 문서와 역사 기록

다음 문서는 일반 사용자가 제품을 이해하거나 실행하는 데 필요하지 않습니다. 운영 또는 추적성이 필요할 때만 확인합니다.

- [유지관리자와 AI 에이전트 작업 절차](ai-agent-workflow.md) — 복잡한 변경을 위한 선택적 상세 절차. 일반 기여의 필수 문서가 아님
- [Spec Registry 도입 전 구현 이력](../specs/000-pre-spec-implementation-history.md) —
  2026-07-11부터 Registry 기준선까지 source로 확인한 구현 순서
- [전체 개발 기록](archive/development-log-full-history.md) — 당시 계획·실험·실패와
  판정을 포함한 날짜별 원문 기록
- [OpenSQL VM 재현 절차](lab-opensql-vm-runbook.md) — OpenSQL 검증 환경을 재현하는 유지관리자용 절차
- [보관 문서 안내](archive/README.md) — 종료된 실험, 초기 검증과 전체 개발 기록

## 상태 표기

`VERIFIED`는 Spec의 필수 검증을 충족했다는 뜻입니다. `PASS`는 기록된 소스와 환경에서 특정 검사를 실제로 통과했다는 뜻이며, 다른 환경으로 확대해 해석하지 않습니다. `NOT_RUN`과 `NOT_VERIFIED`는 통과 증거가 아닙니다. 전체 정의와 현재 목록은 [기능별 검증 기록](../specs/README.md)을 따릅니다.
