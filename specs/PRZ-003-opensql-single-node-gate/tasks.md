# PRZ-003 — OpenSQL 단일 노드 Gate Tasks

> **현재 상태:** `VERIFIED`

## P1. 단일 노드 환경 준비

- [x] `T-01` Rocky Linux 9.7 single-node VM과 비공개 네트워크를 구성하고
  [요구사항 판정](evidence.md#요구사항별-판정)을 남겼다.
- [x] `T-02` 라이선스 귀속값과 시간 동기화를
  [비공개 근거](evidence.md#실제-환경)로 확인했다.
- [x] `T-03` OpenSQL `single` 설치·라이선스·기본 연결과 SQL Gate의 차이를
  [Evidence](evidence.md#요구사항별-판정)에 기록했다.

## P2. 전용 DB와 SQL Gate

- [x] `T-06` 전용 DB와 Flyway·runtime 최소 권한 역할을 구성했다.
- [x] `T-07` 실제 [OpenSQL single-node SQL Gate](evidence.md#실제-opensql-single-node-gate)를
  통과했다.
- [x] `T-08` 전용 DB·role·SSH key·helper 정리를
  [공개·비공개 경계](evidence.md#공개와-비공개-경계)에 맞춰 확인했다.

## P3. 플랫폼 회귀

- [x] `T-04` Windows UTF-8 통합 테스트를 교정·재검증했다.
- [x] `T-05` Linux `SecureDirectoryStream` 경로를 재검증했다. 두 결과는
  [플랫폼 재검증](evidence.md#windows-utf-8과-플랫폼-재검증)에 있다.

## P4. 감사와 통합

- [x] `T-09` 실제 결과와 공개·비공개 경계를 감사해
  [최종 상태](evidence.md#최종-상태)를 확정했다.
- [x] `T-10` PR·CI·solo review 예외와
  [merge 기록](evidence.md#github-통합과-review)을 남겼다.
