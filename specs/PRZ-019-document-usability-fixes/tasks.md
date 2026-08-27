# PRZ-019 — 태그 문서 수 명확화와 TXT 원문 미리보기 Tasks

> **현재 상태:** `COMPLETED`

## P1. 태그 표시

- [x] `T1` tag usage가 문서 본문 출현이 아닌 tag-document 연결 수임을 확인한다.
- [x] `T2` 목록 문구와 설명을 연결 수 기준으로 명확히 한다.

## P2. TXT 미리보기와 원문

- [x] `T3` 기존 owner-scoped TXT/PDF original API와 보안 header를 확인한다.
- [x] `T4` 제한된 TXT 내용 preview와 상태 처리를 구현한다.
- [x] `T5` 문서 상세에서 TXT/PDF 원문 viewer를 제공한다.

## P3. 검증과 감사

- [x] `T6` frontend unit test를 추가하고 전체 frontend 검사를 통과한다.
- [x] `T7` 로컬 Docker 브라우저에서 태그 문구, TXT preview와 TXT/PDF viewer를
  사용자가 직접 확인했다.
- [x] `T8` 최종 diff와 비영향 범위를 감사하고 Evidence를 기록한다.
