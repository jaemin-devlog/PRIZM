# PRZ-012 — 검색 근거 표현 품질 개선 Tasks

> **현재 상태:** `IMPLEMENTED_UNVERIFIED`
>
> 계약은 [Spec](spec.md), 계획 단계는 [Plan](plan.md), 실제 검증 결과는
> [Evidence](evidence.md)를 따른다.

## P1. 기준선과 검색 결과 불변 장치

- [x] `T1` 현재 검색→snippet→응답→frontend 흐름을 확인했다.
- [x] `T2` PRZ-008 보존 계약과 변경 금지 파일을 확정했다.
- [x] `T3` PRZ-012 Spec과 Registry를 등록했다.
- [x] 구현 전 focused backend 검색 테스트를 통과했다.

## P2. 추출형 근거 문장 선택

- [x] `T4` 질문 관련 원문 1–3문장 선택기를 구현했다.
- [x] 핵심어·수행·문제·결과 문맥과 인접 문장 선택을 unit test했다.
- [x] 원문 보존, 최대 3문장과 empty·short content를 검증했다.

## P3. 근거 중심 검색 카드

- [x] `T5` 핵심 근거가 먼저 읽히는 카드 구조를 구현했다.
- [x] 출처와 기타 metadata의 시각적 우선순위를 분리했다.
- [x] 기존 전체 원문 펼치기와 score 표시를 보존했다.

## P4. 중복 및 검색 계약 회귀

- [x] `T6` 완전 동일 본문 축약과 서로 다른 근거 보존을 검증했다.
- [x] 전후 result ID·순서·score·distance 불변을 확인했다.
- [x] PRZ-008 검색 평가와 backend unit·integration을 실행했다.

## P5. 대표 질의와 VERIFY 기록

- [ ] `T7` 대표 질의 7개의 Before/After를 실제 개인 문서에서 확인한다. (`NOT_RUN`)
- [x] `T8` frontend lint·build와 격리 합성 문서 browser 흐름을 검증했다.
- [x] `T9` Evidence와 관련 현재 상태 문서를 실제 결과에 맞췄다.
- [x] Markdown 링크와 `git diff --check`를 통과했다.
