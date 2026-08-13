# PRZ-002 — 오픈소스 준비 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** PRZ-002 구현 전 `main`
>
> 구현 전 계획을 보존한다. 결과는 [Tasks](tasks.md)와 [Evidence](evidence.md)를
> 따른다.

## P1. 공개 경계와 license

- 목표: source-only 배포 범위와 재배포 경계를 확정한다.
- 변경 범위: `LICENSE`, `NOTICE`, provenance와 배포 범위 manifest.
- 검증: code·asset·fixture·dependency·model의 upstream, version, SPDX와 배포 여부를
  대조한다.
- Rollback: blocking unknown이나 license 충돌이 있으면 공개 통합을 중단한다.
- 중단 조건: 출처 불명, hash 충돌, 미승인 license 또는 민감정보가 발견되면
  중단한다.

## P2. SBOM과 공개 문서

- 목표: deterministic SBOM·checksum과 공개 진입 문서를 만든다.
- 변경 범위: `sbom/`, 생성·검증 script, README·Quickstart·docs index와 CI.
- 검증: Windows·Linux clean clone과 GitHub Actions에서 같은 생성·검증을 실행한다.
- Rollback: verifier 실패 시 generated SBOM과 checksum을 갱신하지 않는다.
- 중단 조건: model revision이나 배포 산출물 범위를 식별하지 못하면 중단한다.

## P3. 감사와 통합

- 목표: 독립 감사와 실제 GitHub 통합을 완료한다.
- 변경 범위: Evidence, Registry와 실제 PR·CI 기록.
- 검증: OSS readiness, SBOM, 문서·민감정보 검사와 review 경계를 확인한다.
- Rollback: 실패 기록을 지우지 않고 보완 결과와 함께 보존한다.
- 중단 조건: `REVIEW_NOT_AVAILABLE_SOLO`를 review evidence로 표현하면 중단한다.

## 공통 위험과 대응

- Java, npm, CI Action, container, model과 fixture를 다른 scope로 기록한다.
- Ollama source·binary·model weight와 PRIZM integration code를 분리한다.
- model cache, OpenSQL 공급 자산과 사용자 data는 저장소에 넣지 않는다.
- PostgreSQL과 OpenSQL 결과를 분리한다.

## Dependency 및 license 영향

- source-only 배포물은 Git source와 source ZIP이다.
- JAR, frontend `dist`, container image, Ollama binary와 model weight는 후속 배포
  Gate다.

## Branch와 통합 경계

- 실제 착수한 임시 branch만 사용하고 Issue·PR은 존재할 때만 기록한다.

## 계획 대비 주요 변경

- 외부 운영 경로가 필요한 governance 문서와 신고 channel은 `DEFERRED`로 남겼다.
