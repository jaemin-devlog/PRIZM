# PRIZM 기능별 검증 기록

`specs/`는 기능 의도, 구현 전 계획과 실행 결과를 연결하는 추적성 문서입니다. 일반 사용자는 [프로젝트 README](../README.md)와 [현재 구현 현황](../docs/project-status.md)을 먼저 읽고, 특정 판정의 근거가 필요할 때 이 목록을 확인하면 됩니다.

Spec은 구현 증거가 아닙니다. 실제 상태는 소스 코드, 적용된 Flyway migration과 실행 가능한 test로 판단합니다.

## 읽는 방법

1. `spec.md`에서 범위와 반드시 지켜야 할 동작을 확인합니다.
2. `evidence.md`에서 최종 판정, 소스, 환경과 실행 결과를 확인합니다.
3. 구현 전 선택과 단계가 필요할 때만 `plan.md`와 `tasks.md`를 읽습니다.
4. 단계별 문서와 원시 benchmark 결과물은 해당 판정의 상세 근거가 필요할 때만 확인합니다.

현재 진행 중인 기능 개발 단계는 없습니다. PRZ-018과 PRZ-019는 로컬 작업 트리에서
검증됐지만 아직 GitHub에 통합되지 않았습니다. PRZ-008과 PRZ-016의 `IN_PROGRESS`는
필수 검증 항목이 모두 끝나지 않았다는 형식 상태이며, 현재 진행 중인 개발을 뜻하지
않습니다.

## 현재 상태 요약

| 구분 | PRZ | 의미 |
|---|---|---|
| 현재 제품 기준에 통합 | 001–007, 009–013, 015–017 | 016의 현재 적용 검색을 포함하며 형식 상태는 아래 목록에서 별도 표시 |
| 목록 도입 전 기준 | 000 | Spec 목록 도입 전 구현을 `AS_BUILT_BASELINE`으로 기록 |
| 미완료 검증 보존 | 008, 016 | 현재 제품 기준과 별개로 완료하지 않거나 채택하지 않은 평가가 있음 |
| 검토 후 거절 | 014 | 검증 범위를 단일 서버로 확정해 OpenHA 다중 노드 구성을 채택하지 않음 |

## 전체 목록

| ID | 이름 | 상태 | 기준 소스 / 통합 |
|---|---|---|---|
| [PRZ-000](PRZ-000-platform-baseline/spec.md) | 플랫폼·문서 보관함 기준 | `AS_BUILT_BASELINE` | `e995a5f` |
| [PRZ-001](PRZ-001-search-evaluation-integrity/spec.md) | 검색 평가 정합성 | `VERIFIED` | `36c8610` |
| [PRZ-002](PRZ-002-open-source-readiness/spec.md) | 오픈소스 준비 | `VERIFIED` | `f54e3d9` |
| [PRZ-003](PRZ-003-opensql-single-node-gate/spec.md) | OpenSQL 단일 서버 검증 | `VERIFIED` | `777e184` |
| [PRZ-004](PRZ-004-clean-clone-demo/spec.md) | 새 설치 환경 데모 | `VERIFIED` | `aff3e87`, PR #25 |
| [PRZ-005](PRZ-005-opensql-ollama-e2e/spec.md) | OpenSQL·Ollama E2E | `VERIFIED` | `eab32c8`, PR #26 |
| [PRZ-006](PRZ-006-local-single-user-demo/spec.md) | 로컬 빠른 시작 | `VERIFIED` | `bfd8600` |
| [PRZ-007](PRZ-007-self-hosted-signup/spec.md) | 자체 호스팅 회원가입 | `VERIFIED` | `2b8b600`, PR #33 |
| [PRZ-008](PRZ-008-search-evidence-reliability/spec.md) | 검색 근거 신뢰성 | `IN_PROGRESS` | `2190d47`, PR #40. 일부 최적화 검증 미완료 |
| [PRZ-009](PRZ-009-career-keyword-map/spec.md) | 사용자가 관리하는 문서 태그 | `VERIFIED` | P4 `1c1d8d2`, [PR #51](https://github.com/jaemin-devlog/PRIZM/pull/51), 병합 `d44f30e` |
| [PRZ-010](PRZ-010-change-log-sync/spec.md) | ChangeLog 동기화 | `VERIFIED` | `26c546b`, PR #39 |
| [PRZ-011](PRZ-011-document-processing-status-ux/spec.md) | 처리 상태 UX | `VERIFIED` | `fbb3481`, PR #41 |
| [PRZ-012](PRZ-012-search-evidence-presentation/spec.md) | 검색 근거 표현 | `VERIFIED` | 2026-08-13 VERIFY `PASS` |
| [PRZ-013](PRZ-013-openproxy-single-primary-gate/spec.md) | OpenProxy 단일 Primary 검증 | `VERIFIED` | `a65f91d` |
| [PRZ-014](PRZ-014-openha-topology-gate/spec.md) | OpenHA topology 검토 | `REJECTED` | Single-only 범위로 확정 |
| [PRZ-015](PRZ-015-mcp-career-evidence-search/spec.md) | 읽기 전용 MCP 검색 | `VERIFIED` | `97c01cb`, [PR #46](https://github.com/jaemin-devlog/PRIZM/pull/46), 병합 `23166e7` |
| [PRZ-016](PRZ-016-search-performance-v2/spec.md) | Search Performance V2 | `IN_PROGRESS` | 현재 적용 검색 [PR #50](https://github.com/jaemin-devlog/PRIZM/pull/50), 병합 `3cfe9dc`; P15 `NOT_VERIFIED`, P16 미채택 |
| [PRZ-017](PRZ-017-job-posting-evidence-v1/spec.md) | 채용공고 항목별 근거 검색 V1 | `VERIFIED` | `94715cf`, [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53), 병합 `b78ec42` |
| [PRZ-018](PRZ-018-document-detail-page/spec.md) | 문서 상세 미리보기 페이지 | `VERIFIED` | 로컬 작업 트리 · 통합 전 |
| [PRZ-019](PRZ-019-job-evidence-action-consistency/spec.md) | 채용공고 근거 보기 동작 일관성 | `VERIFIED` | 로컬 작업 트리 · 통합 전 |

## 상태와 결과

| 표기 | 의미 |
|---|---|
| `AS_BUILT_BASELINE` | Registry 도입 전에 존재하던 구현의 역사적 기준선 |
| `IN_PROGRESS` | 구현 또는 필수 검증 일부가 끝나지 않은 상태 |
| `VERIFIED` | 해당 Spec의 요구사항과 필수 검증을 충족한 상태 |
| `DEFERRED` | 이유와 재개 조건을 남기고 미룬 상태 |
| `REJECTED` | 검토 또는 실험 뒤 채택하지 않은 상태 |
| `PASS` / `FAIL` | 표시한 소스와 환경에서 실행한 검사의 결과 |
| `NOT_RUN` | 해당 검사를 실행하지 않음 |
| `NOT_VERIFIED` | 목표 동작을 입증할 근거가 부족함 |

`PASS`는 다른 환경의 증거로 확대하지 않습니다. PostgreSQL·pgvector 결과와 OpenSQL·OpenProxy 결과도 구분합니다. 과거 중간 상태 기록의 실패나 `NEEDS_ADJUSTMENT`는 당시 판단이므로 그대로 보존합니다.

## 검증 기록 보존 원칙

- 최종 판정은 각 PRZ의 상위 `evidence.md`에서 먼저 확인합니다.
- 긴 실행 로그, benchmark 결과와 실패 분석은 상세 검증 기록에 둡니다.
- README와 제품 문서에서는 현재 기능을 이해하는 데 필요한 결론만 연결합니다.
- Issue, PR, commit, review와 CI는 실제 이력이 있을 때만 기록합니다.
- 현재 상태와 당시 중간 기록을 섞어 과거 결과를 소급해 바꾸지 않습니다.

일반 기여 절차는 [기여 안내](../CONTRIBUTING.md)를 따릅니다. 복잡한 핵심 동작과
저장소 운영 절차는 유지관리자용 [프로젝트 규칙](../AGENTS.md)을 함께 확인합니다.
