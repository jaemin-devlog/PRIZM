# PRIZM에 기여하기

버그 제보, 문서 개선과 재현 가능한 검증 보완을 환영합니다. 새로운 제품 기능은 먼저 Issue에서 범위와 필요성을 논의해 주세요.

## 시작하기

1. [README](README.md)에서 현재 제품 범위를 확인합니다.
2. [Quick Start](docs/quickstart.md)로 로컬 실행 환경을 준비합니다.
3. [Architecture](docs/architecture.md)와 [Current Status](docs/project-status.md)에서 관련 계약과 알려진 한계를 확인합니다.
4. 기존 Issue와 [Spec Registry](specs/README.md)에 같은 문제가 있는지 살펴봅니다.

## 기본 기여 흐름

1. 기존 Issue를 확인하고, 새 문제라면 재현 방법이나 제안 배경을 적어 Issue를 엽니다.
2. 유지관리자와 변경 범위와 검증 방법을 확인합니다.
3. 합의한 범위만 구현하고 관련 문서와 테스트를 함께 수정합니다.
4. 변경에 맞는 검사를 실행하고, 실행하지 못한 검사는 이유와 함께 남깁니다.
5. Pull Request에 변경 이유, 범위, 검증 결과와 남은 제한을 적습니다.

AI 도구를 사용할 필요는 없습니다. 일반 기여자는 위 흐름만 따르면 됩니다.

## 변경 원칙

- 소스 코드, 적용된 Flyway migration과 실행 가능한 테스트를 구현의 기준으로 삼습니다.
- 등록 문서에 없는 경력, 기술, 성과나 수치를 만들지 않습니다.
- 문서와 검색 결과의 owner isolation, `ACTIVE` version 안전 계약을 지킵니다.
- PostgreSQL 결과를 OpenSQL 검증으로 표현하지 않습니다.
- 적용된 Flyway migration은 수정하지 않고 새 migration을 추가합니다.
- `.env`, token, private key, 업로드 원본, DB volume, 모델 파일과 개인 경로를 제출하지 않습니다.
- 관련 없는 파일이나 다른 작업자의 변경을 정리하거나 되돌리지 않습니다.

## 변경 제안과 검증

- 버그는 재현 절차, 기대 결과, 실제 결과와 환경을 Issue에 적습니다.
- 기능이나 관찰 가능한 계약을 바꾸려면 구현 전에 작은 Spec과 검증 계획을 작성합니다.
- 문서 전용 변경은 수정한 파일, 확인한 근거와 링크 검사 결과를 남깁니다.
- 코드 변경은 관련 단위·통합 검사와 저장소의 OSS readiness 검사를 통과해야 합니다.
- 실행하지 못한 검사는 `NOT_RUN` 또는 `NOT_VERIFIED`로 구분합니다.

Pull Request에는 변경 이유, 범위, 검증 결과, 남은 제한을 짧게 적어 주세요. 큰 변경은 한 PR에 섞지 않습니다.

핵심 계약이나 저장소 운영 절차를 바꾸는 유지관리자는 [프로젝트 규칙](AGENTS.md)과
[상세 유지관리 Workflow](docs/ai-agent-workflow.md)를 참고할 수 있습니다. 이는 일반
기여에 AI 도구 사용을 요구하는 절차가 아닙니다.

## 행동 기준과 보안

[Code of Conduct](CODE_OF_CONDUCT.md)를 지켜 주세요. 취약점이나 민감한 정보는 공개하지 말고 [Security Policy](SECURITY.md)의 연락 절차를 따라 주세요.
