# PRZ-003 작업 목록

| ID | 작업 | 최종 상태 | 결과 |
|---|---|---|---|
| `T-01` | Rocky Linux 9.7 single-node VM과 비공개 네트워크 구성 | `PASS_PRIVATE_EVIDENCE` | [요구사항 판정](evidence.md#요구사항별-판정) |
| `T-02` | 라이선스 귀속값·시간 동기화 확인 | `PASS_PRIVATE_EVIDENCE` | [실제 환경](evidence.md#실제-환경) |
| `T-03` | OpenSQL `single` 설치·라이선스 적용·기본 연결 | `PASS_INSTALLATION_ONLY` | [설치와 Gate 구분](evidence.md#요구사항별-판정) |
| `T-04` | Windows UTF-8 통합 테스트 교정·재검증 | `PASS` | [교정 이력](evidence.md#windows-utf-8과-플랫폼-재검증) |
| `T-05` | Linux `SecureDirectoryStream` 경로 재검증 | `PASS` | [플랫폼 재검증](evidence.md#windows-utf-8과-플랫폼-재검증) |
| `T-06` | 전용 DB와 Flyway/runtime 최소 권한 구성 | `PASS_PRIVATE_EVIDENCE` | [OpenSQL Gate](evidence.md#실제-opensql-single-node-gate) |
| `T-07` | 실제 OpenSQL single-node SQL Gate 실행 | `PASS` | [OpenSQL Gate](evidence.md#실제-opensql-single-node-gate) |
| `T-08` | 전용 DB·role·SSH key·helper 정리 확인 | `PASS_PRIVATE_EVIDENCE` | [공개·비공개 경계](evidence.md#공개와-비공개-경계) |
| `T-09` | 실제 결과와 공개 경계 독립 감사 | `PASS` | [최종 상태](evidence.md#최종-상태) |
| `T-10` | PR·CI·solo review 예외·merge 기록 | `PASS` | [GitHub 기록](evidence.md#github-통합과-review) |
