# PRZ-005 — OpenSQL·Ollama E2E Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** PRZ-005 구현 전 OpenSQL single-node 환경
>
> 구현 전 계획을 보존한다. 실제 결과는 [Tasks](tasks.md),
> [Evidence](evidence.md)와 [상세 작업 보고서](implementation-report.md)를 따른다.

## P1. VM과 서비스 준비

- 목표: single-node OpenSQL 환경의 건강 상태와 기동 구조를 확정한다.
- 변경 범위: 시간 동기화, etcd·Patroni·OpenSQL·OpenProxy unit과 Host-only network.
- 검증: Leader·running, `5432`·`8008`·`6432` LISTEN과 service 재시작을 확인한다.
- Rollback: 설정 변경은 백업과 SHA-256으로 원복한다.
- 중단 조건: 시간·journal·filesystem 신뢰성이 부족하거나 공급 asset이 노출되면
  중단한다.

## P2. DB와 최소 권한

- 목표: migration owner와 runtime role을 분리해 direct `5432`를 준비한다.
- 변경 범위: `prizm` DB, `prizm_owner`, `prizm_app`, vector와 Flyway.
- 검증: 역할별 허용·거부, JPA validation, Flyway V1–V13과 재실행을 확인한다.
- Rollback: 전용 DB·role만 정확히 정리하고 기존 환경은 건드리지 않는다.
- 중단 조건: 관리자 password 추측이나 runtime 권한 확대가 필요하면 중단한다.

## P3. Direct `5432` 전체 흐름

- 목표: Spring Boot·Ollama·OpenSQL에서 전체 사용자 흐름을 검증한다.
- 변경 범위: 원칙적으로 제품 source·migration·dependency 변경 없음.
- 검증: login, TXT·PDF upload, `bge-m3` embedding, pgvector 저장, `ACTIVE`, API·browser
  검색과 두 사용자 격리를 확인한다.
- Rollback: 임시 database·role·runner를 제거한다.
- 중단 조건: PostgreSQL 결과를 OpenSQL 결과로 대체하거나 개인정보가 필요하면
  중단한다.

## P4. 자동 검증과 통합

- 목표: OpenSQL opt-in test, 전체 회귀와 OSS·SBOM 감사를 완료한다.
- 변경 범위: Evidence, Registry·status·roadmap과 GitHub 통합 기록.
- 검증: backend·integration, frontend lint·typecheck·build, OSS readiness, SBOM,
  문서·민감정보와 diff를 확인한다.
- Rollback: 필수 Gate 실패 시 `VERIFIED`로 판정하지 않는다.
- 중단 조건: OpenProxy·OpenHA·failover를 핵심 완료 조건으로 섞으면 중단한다.

## 공통 위험과 대응

- OpenProxy `6432` TCP와 SQL routing·인증을 분리한다.
- 안전한 인증을 확인하지 못하면 설정을 복원하고 direct `5432`만 사용한다.
- credential은 대화형·비공개 경로로 전달하며 값은 기록하지 않는다.

## Dependency 및 license 영향

- 제품 dependency, Flyway migration과 공개 SBOM identity를 바꾸지 않는다.
- OpenSQL 공급 asset은 저장소에 포함하지 않는다.

## Branch와 통합 경계

- 핵심 direct 경로와 보류 중인 OpenProxy·OpenHA를 다른 Gate로 기록한다.

## 계획 대비 주요 변경

- OpenProxy 인증이 `AUTH_BLOCKED`여서 설정을 원복하고 direct `5432`로 E2E를
  완료했다.
- 영구 journal, OpenProxy SQL routing, OpenHA와 failover는 `DEFERRED`로 남겼다.
