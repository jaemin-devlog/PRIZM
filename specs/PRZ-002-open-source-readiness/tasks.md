# PRZ-002 — 오픈소스 준비 Tasks

> **현재 상태:** `VERIFIED`

## P1. 공개 경계와 license

- [x] `P-00` [Plan](plan.md)에서 범위·배포 경계·검증 계획을 확정했다.
- [x] `T-01` 외부 구성요소와 배포 경계를 기록하고
  [OR-001 판정](evidence.md#요구사항별-판정)을 남겼다.
- [x] `T-02` 전체 license·provenance를 `PASS_SOURCE_ONLY`로 검증했다.
- [x] `G-01` source-only 배포 경계와 outgoing license를 결정했다.
- [x] `T-03` MIT·Apache-2.0 비교 뒤 사용자 승인을 기록했다.
- [x] `T-04` `LICENSE`와 `NOTICE`를 적용했다. T-01–T-04의 판정은
  [요구사항별 Evidence](evidence.md#요구사항별-판정)를 따른다.

## P2. SBOM과 공개 문서

- [x] `T-05` Backend·Frontend SBOM과 AI model 명세를
  [생성·검증했다](evidence.md#실행한-검증).
- [x] `T-08` README·Quickstart와 문서 색인을 현행화했다.
- [x] `T-09` license·SBOM 검증 CI를 추가하고
  [GitHub 기록](evidence.md#github-통합과-review)을 남겼다.

## P3. 감사와 통합

- [x] `G-03A` 구현 전 GitHub 권한을 확인했으며 Issue는 만들지 않았다.
- [x] `T-10` 독립 읽기 전용 감사를 통과하고
  [최종 상태](evidence.md#최종-상태)를 확정했다.
- [x] `G-03B` PR, solo review 예외와
  [통합 기록](evidence.md#github-통합과-review)을 남겼다.

## 후속 또는 제외 범위

- [ ] `G-02` 실제 비공개 보안 신고 경로는 [제한 사항](evidence.md#남은-제한)에 따라
  `DEFERRED`다.
- [ ] `T-06` 기여·행동강령·보안·지원 정책은 외부 운영 전까지 `DEFERRED`다.
- [ ] `T-07` Issue Form과 PR Template은 외부 접수 전까지 `DEFERRED`다. 두 요구사항의
  판정은 [OR-005·OR-006 Evidence](evidence.md#요구사항별-판정)를 따른다.
