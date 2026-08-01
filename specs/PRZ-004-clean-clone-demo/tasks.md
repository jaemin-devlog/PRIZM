# PRZ-004 Tasks

| ID | 작업 | 상태 | 결과 |
|---|---|---|---|
| T1 | 원격 main·환경·ZIP 후보를 독립 확인 | `PASS` | [Evidence](evidence.md)의 기준선·후보 확인 기록 |
| T2 | 초기 구현 후보를 Spec·Plan과 사후 대조하고 conformance baseline 기록 | `PASS` | 사전 고정 기록이 아님; [Spec](spec.md), [Plan](plan.md), [Evidence](evidence.md) |
| T3 | demo USER·충돌·BCrypt 보안 계약 구현 | `PASS` | [Evidence](evidence.md) |
| T4 | 안전한 env·fixture·smoke 도구 구현 | `PASS` | [Evidence](evidence.md) |
| T5 | npm audit 교정과 SBOM·license 동기화 | `PASS` | vulnerability 0, SBOM·OSS readiness 통과; [Evidence](evidence.md) |
| T6 | 문서와 상태 경계 갱신 | `VERIFY_PASS_AUDIT_PENDING` | 공개 main·local 검증·GitHub 미통합을 분리하고 문서 검증 통과; [Evidence](evidence.md) |
| T7 | 최종 source commit 자동 검증 | `PASS` | `339 PASS / 18 SKIP / 0 FAIL`; [Evidence](evidence.md) |
| T8 | 두 fresh clone API·browser 검증 | `PASS` | 두 번째 빈 목록 UI 관찰은 `NOT_RUN` 비차단 finding; [Evidence](evidence.md) |
| T9 | 독립 최종 보안·ownership·license 감사 | `PENDING_AUDIT` | 문서 교정 뒤 재실행; [Evidence](evidence.md) |
| T10 | GitHub 통합 | `NOT_RUN` | 이번 승인 범위에서 제외 |
