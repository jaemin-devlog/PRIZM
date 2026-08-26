# PRZ-018 — 문서 상세 미리보기 페이지 Plan

> **문서 상태:** `COMPLETED`
> **계획 기준선:** `d3b0096385366d0c3f617dd688d5b22d77f20c6f`

## P1. 페이지 전환과 상태

- `DocumentsPage`의 상세 모달을 인페이지 상세 화면으로 바꾼다.
- 기존 `documentId` query를 유지하고 목록·브라우저 history 전환을 동기화한다.
- rollback은 새 상세 JSX와 경로 동기화를 제거해 기존 목록 흐름으로 되돌리는 방식이다.

## P2. 미리보기와 버전 탐색

- 선택한 버전 ID를 화면 상태로 관리한다.
- 기존 thumbnail API로 PDF 첫 페이지를 표시하고 TXT·오류 대체 화면을 제공한다.
- 버전 목록을 오른쪽에 배치하고 선택 상태, 처리 상태, 등록·삭제 기능을 유지한다.

## P3. 문서 관리와 반응형 UI

- 정보·태그 수정과 문서 삭제를 미리보기 아래 관리 영역으로 재배치한다.
- 데스크톱은 미리보기/버전 2열, 좁은 화면은 1열로 배치한다.
- 기존 PDF viewer와 Tag modal을 유지한다.

## P4. 검증과 감사

- path helper unit test와 필요한 presentation test를 추가한다.
- `npm --prefix frontend run test:unit`, `lint`, `build`를 실행한다.
- 로컬 브라우저에서 목록→상세→버전 선택→목록 복귀와 반응형 화면을 확인한다.
- 최종 diff에서 backend, SQL, migration, dependency 변경이 없는지 감사한다.

## P5. 상세 화면 밀도 보정

- 기본 정보와 태그를 하나의 전체 너비 카드에 세로로 배치한다.
- PDF thumbnail과 미리보기 캔버스의 최대 크기를 기존 대비 약 2/3로 줄인다.
- 데스크톱과 모바일 배치를 다시 확인한 뒤 frontend 검사와 Docker 반영을 수행한다.

## 영향과 비영향

- 수정 예상: `frontend/src/App.tsx`, `frontend/src/styles.css`, frontend test, PRZ-018 문서와 Registry
- 비영향: backend Java, migration, SQL, Gradle/npm dependency, Docker, 검색과 MCP

## Branch와 통합 경계

- 임시 branch: `PRZ-018-document-detail-page`
- commit, push, PR과 merge는 별도 사용자 요청 전에는 수행하지 않는다.

## PLAN Gate

- 모든 acceptance criterion이 구현 위치와 검증 명령에 연결됐다.
- 실패 시 기존 API나 데이터를 바꾸지 않고 UI 변경만 되돌릴 수 있다.
- security, ownership, migration, dependency 영향이 없다.

판정: `PASS`
