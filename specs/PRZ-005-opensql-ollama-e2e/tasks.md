# PRZ-005 작업 목록

| ID | 작업 | 현재 상태 | 결과 |
|---|---|---|---|
| `T-01` | OpenSQL 기동 구조 조사 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-02` | Patroni·OpenProxy unit 등록 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-03` | 단일 노드 etcd·Patroni·OpenSQL·OpenProxy 기동 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-04` | Windows → OpenProxy `6432` 네트워크 연결 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-05` | `prizm` DB와 migration/runtime 역할 생성 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-06` | 역할별 최소 권한과 거부 동작 확인 | `DONE` | 객체별 DML·시퀀스 USAGE 적용, 허용·거부 probe와 잔여 0건 확인; [작업 보고서](implementation-report.md) |
| `T-07` | vector `0.8.1` 확장 생성 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-08` | OpenProxy 설정을 변경 전 상태로 복원 | `DONE` | [작업 보고서](implementation-report.md) |
| `T-09` | OpenProxy SQL 인증 | `BLOCKED` | `AUTH_BLOCKED`; [작업 보고서](implementation-report.md) |
| `T-10` | OpenProxy SQL routing과 안전한 인증 | `DEFERRED` | 공급사 답변 후 재개 |
| `T-11` | 영구 journal 적용 | `DEFERRED` | 적용 필요성 별도 결정 |
| `T-12A` | Spring Context 없는 Flyway migration 전용 테스트 경로 | `DONE` | 첫 D2B의 사전 `validate()` 실패를 교정하고 컴파일·기본 `SKIPPED` 재검증 |
| `T-12` | Flyway V1~V13 실행 | `DONE` | 13개 적용, 현재 V13, pending·실패 0, 두 번째 migrate 신규 적용 0; [작업 보고서](implementation-report.md) |
| `T-13` | Spring Boot와 Ollama `bge-m3` 연결 | `DONE` | Ollama `0.32.3`, `bge-m3:latest` 모델 identity와 1024차원 embedding, Spring Boot → OpenSQL `5432` 연결 확인 |
| `T-14` | 업로드·임베딩·검색 OpenSQL E2E | `DONE` | demo 로그인, 합성 TXT/PDF `ACTIVE`, embedding 저장, `TEXT_CHUNK`·`PAGE` 원문 검색 확인 |
| `T-15` | OpenHA 장애 전환 | `DEFERRED` | PRZ-005의 명시적 제외 범위 |
| `T-16` | 두 USER API 격리와 브라우저 UI | `DONE` | 두 사용자 문서 목록·상세·검색 격리, UI 로그인·상세·PDF 원문·TXT/PDF 업로드·검색·로그아웃 차단 확인; [작업 보고서](implementation-report.md) |
| `T-17` | 현재 source의 OpenSQL opt-in integration test | `DONE` | 격리된 `prizm_integration_test`에서 V1~V13, 최소 권한, vector 검색과 Worker SQL 검증 통과; 테스트 1개 성공, 실패·오류·skip 0건; [작업 보고서](implementation-report.md) |
| `T-18` | 전체 회귀·OSS·SBOM·최종 감사 | `DONE` | backend 단위 262개·통합 69개, frontend lint·typecheck·build, OSS readiness·SBOM·문서·민감정보 감사 통과; frontend unit test는 공식 명령이 없어 `NOT_RUN`; T-18A 문서 상태 재감사 통과 |
