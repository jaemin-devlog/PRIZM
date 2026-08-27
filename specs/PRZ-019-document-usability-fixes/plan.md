# PRZ-019 — 태그 문서 수 명확화와 TXT 원문 미리보기 Plan

> **문서 상태:** `COMPLETED`
> **계획 기준선:** `5b2c50fbe0d6fac0a9ee5c9d51f5365392c37a69`

## P1. 태그 집계 표현

상태: `COMPLETED`

- 기존 tag usage API와 SQL이 tag-document metadata 관계를 세는지 재확인한다.
- 목록 수를 `연결 문서`로 표시하고 실제 본문 출현 수가 아님을 짧게 설명한다.
- 정렬과 tag 상세 Search query는 변경하지 않는다.

## P2. TXT 미리보기와 원문 viewer

상태: `COMPLETED`

- 기존 `getDocumentOriginal`을 문서 상세 흐름에 연결한다.
- TXT는 제어 문자를 정리하고 길이를 제한한 preview를 카드와 상세에 표시한다.
- 원문 viewer는 PDF object URL과 TXT text 상태를 분리하고 닫기·요청 취소를 정리한다.
- 기존 PDF thumbnail과 원문 열기를 회귀시키지 않는다.

## P3. 검증과 감사

상태: `COMPLETED`

- 표시 문구와 TXT preview 정규화 unit test를 추가한다.
- frontend unit test, typecheck, lint, build와 `git diff --check`를 실행한다.
- 로그인된 로컬 브라우저에서 TXT/PDF 상세 동작과 경력 키워드 문구를 확인한다.
- backend, migration, dependency와 미추적 `tmp/` 비변경을 감사한다.

## 영향과 비영향

- 수정 예상: `frontend/src/App.tsx`, `frontend/src/styles.css`, presentation helper,
  frontend test, PRZ-019 문서, Registry와 현재 상태 문서
- 비영향: backend Java, SQL/migration, Gradle/npm dependency, 검색·MCP

## Rollback

새 TXT preview/viewer 분기와 표시 문구만 제거하면 기존 PDF 전용 화면으로 돌아간다.
데이터와 API 계약을 바꾸지 않으므로 별도 데이터 복구는 필요 없다.

## Branch와 통합 경계

- 임시 branch: `PRZ-019-document-usability-fixes`
- commit, push, PR과 merge는 별도 사용자 요청 전에는 수행하지 않는다.

## PLAN Gate

- 모든 요구사항이 구현 위치와 검증 명령에 연결됐다.
- 비밀정보, ownership, migration과 dependency 영향이 없다.

판정: `PASS`
