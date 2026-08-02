# PRZ-005 작업 목록

> **현재 상태:** PRZ-005 핵심 범위 `VERIFIED` — 완료 15개, 차단 1개, 보류 3개

세부 실행 과정과 실패·복구 이력은 [작업 보고서](implementation-report.md)에 보존한다.

## 완료

| ID | 작업 | 상태 | 한 줄 결과 | 증거 |
|---|---|---|---|---|
| `T-01` | OpenSQL 기동 구조 조사 | `DONE` | etcd → Patroni/OpenSQL → OpenProxy 기동 순서를 확인했다. | [환경·서비스 준비](implementation-report.md#vm-장애-대응과-opensql-서비스-준비) |
| `T-02` | Patroni·OpenProxy unit 등록 | `DONE` | 두 systemd unit을 등록하고 정적 검증을 통과했다. | [환경·서비스 준비](implementation-report.md#vm-장애-대응과-opensql-서비스-준비) |
| `T-03` | 단일 노드 etcd·Patroni·OpenSQL·OpenProxy 기동 | `DONE` | Leader·running과 `5432`·`8008`·`6432` LISTEN을 확인했다. | [환경·서비스 준비](implementation-report.md#vm-장애-대응과-opensql-서비스-준비) |
| `T-04` | Windows → OpenProxy `6432` 네트워크 연결 | `DONE` | Host-only Windows 주소에서 `6432` TCP 연결을 확인했다. | [환경·서비스 준비](implementation-report.md#vm-장애-대응과-opensql-서비스-준비) |
| `T-05` | `prizm` DB와 migration/runtime 역할 생성 | `DONE` | `prizm`, `prizm_owner`, `prizm_app`을 분리해 구성했다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-06` | 역할별 최소 권한과 거부 동작 확인 | `DONE` | 객체별 DML·시퀀스 USAGE와 허용·거부 probe를 확인했으며 잔여 객체는 0건이었다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-07` | vector `0.8.1` 확장 생성 | `DONE` | `prizm` DB에 vector `0.8.1`을 생성하고 소유자 `postgres`를 확인했다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-08` | OpenProxy 설정을 변경 전 상태로 복원 | `DONE` | 백업본과 SHA-256이 일치하는 원래 설정으로 복원했다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-12A` | Spring Context 없는 Flyway migration 전용 테스트 경로 | `DONE` | 사전 `validate()` 순서를 교정하고 컴파일·기본 `SKIPPED`를 확인했다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-12` | Flyway `V1`–`V13` 실행 | `DONE` | 13개를 적용해 현재 V13, pending·실패 0, 두 번째 migrate 신규 적용 0을 확인했다. | [DB·권한 구성](implementation-report.md#db역할flyway최소-권한-구성) |
| `T-13` | Spring Boot와 Ollama `bge-m3` 연결 | `DONE` | OpenSQL `5432` 연결과 Ollama `0.32.3`·1024차원 embedding을 확인했다. | [애플리케이션 E2E](implementation-report.md#spring-bootollamaapi브라우저-e2e) |
| `T-14` | 업로드·임베딩·검색 OpenSQL E2E | `DONE` | demo 로그인부터 TXT/PDF `ACTIVE`, embedding과 원문 검색까지 통과했다. | [애플리케이션 E2E](implementation-report.md#spring-bootollamaapi브라우저-e2e) |
| `T-16` | 두 USER API 격리와 브라우저 UI | `DONE` | 목록·상세·검색 격리와 UI 업로드·검색·로그아웃 차단을 확인했다. | [애플리케이션 E2E](implementation-report.md#spring-bootollamaapi브라우저-e2e) |
| `T-17` | 현재 source의 OpenSQL opt-in integration test | `DONE` | 격리 DB에서 테스트 1개가 성공했고 실패·오류·skip은 0건이었다. | [자동 검증·감사](implementation-report.md#자동-통합-테스트전체-회귀병합-감사) |
| `T-18` | 전체 회귀·OSS·SBOM·최종 감사 | `DONE` | 백엔드 단위 테스트 262개·통합 테스트 69개와 프런트엔드·OSS·SBOM·문서 감사를 통과했다. | [자동 검증·감사](implementation-report.md#자동-통합-테스트전체-회귀병합-감사) |

## 차단·보류

| ID | 작업 | 상태 | 한 줄 결과 | 증거 |
|---|---|---|---|---|
| `T-09` | OpenProxy SQL 인증 | `BLOCKED` | 안전한 비밀정보 주입 방식을 확인하지 못해 `AUTH_BLOCKED`로 중단했다. | [OpenProxy 판단](implementation-report.md#현재-보류-범위) |
| `T-10` | OpenProxy SQL routing과 안전한 인증 | `DEFERRED` | 공급사의 공식 안전 구성 답변 뒤 재개한다. | [OpenProxy 판단](implementation-report.md#현재-보류-범위) |
| `T-11` | 영구 journal 적용 | `DEFERRED` | 적용 필요성과 로그 보존 정책을 별도로 결정한다. | [보류 범위](implementation-report.md#현재-보류-범위) |
| `T-15` | OpenHA 장애 전환 | `DEFERRED` | single-node인 PRZ-005의 명시적 제외 범위다. | [보류 범위](implementation-report.md#현재-보류-범위) |

## 다음 후보 작업

PRZ-005의 핵심 범위는 완료됐다. 다음 후보는 P2 DB 장애복구 Gate이며, 여기에서 `T-10`
OpenProxy 안전 인증·SQL routing, `T-11` 영구 journal, `T-15` OpenHA·DB failover의 착수
여부와 범위를 결정한다.
