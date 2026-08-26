# PRZ-018 — 문서 상세 미리보기 페이지 Evidence

> **현재 판정:** `PASS`
> **기준 소스:** 작업 트리
> **검증일:** 2026-08-27

## 구현 근거

- 문서 카드의 `상세 보기`는 현재 폴더 query를 보존한 `documentId` URL로 이동한다.
- 선택한 PDF 버전은 기존 owner-scoped thumbnail API의 응답을 object URL로 변환해
  실제 첫 페이지 이미지를 표시한다. Production 코드에 고정 미리보기 이미지나 합성
  이력서 asset은 추가하지 않았다.
- 오른쪽 버전 목록에서 버전을 고르면 파일명, 버전 번호, 상태와 미리보기가 함께
  바뀐다.
- 문서 정보·태그·버전 등록·이전 버전 삭제·문서 삭제·PDF 원문 열기는 기존 API와
  제한을 그대로 사용한다.
- `목록으로`와 브라우저 뒤로가기는 문서 폴더 query를 보존한 목록으로 복귀한다.

## 자동 검사

| 검사 | 결과 |
|---|---|
| `npm --prefix frontend run typecheck` | `PASS` |
| `npm --prefix frontend run test:unit` | `PASS` — 80 tests, 실패·skip·todo 0 |
| `npm --prefix frontend run lint` | `PASS` |
| `npm --prefix frontend run build` | `PASS` |
| `git diff --check` | `PASS` |

## 브라우저 확인

- 데스크톱에서 목록 → 상세 → v2 선택 → 목록 복귀와 브라우저 뒤로가기를 확인했다.
- 모바일 390×844에서 한 열 배치와 가로 overflow가 없음을 확인했다.
- 브라우저 console error와 warning은 0건이었다.
- 실제 사용자 계정이나 개인 이력서를 사용하지 않기 위해 브라우저 레이아웃 검증에는
  임시 로컬 mock API와 합성 thumbnail만 사용했다. 검증 뒤 mock 파일과 서버를
  제거했다. 실제 사용자 PDF 내용은 열람하거나 저장하지 않았다.

## 감사 결과

- backend Java, SQL, migration, Gradle/npm dependency와 Docker 설정 변경: 0건
- 검색, JWT, owner isolation, `ACTIVE` 전환과 MCP 계약 변경: 0건
- Production 코드의 thumbnail 요청은 기존 인증·owner-scoped API를 그대로 사용한다.
- commit, push, PR과 merge는 수행하지 않았다.

## 2026-08-27 상세 화면 밀도 보정

- 기본 정보와 태그를 하나의 전체 너비 카드로 합치고, 태그를 기본 정보 아래에
  배치했다.
- 데스크톱 thumbnail 상한은 기존 `78% / 560px / 610px`에서
  `52% / 374px / 407px`로 줄였다. 모바일 상한도 `94% / 520px`에서
  `63% / 347px`로 조정했다.
- 미리보기 캔버스 높이도 thumbnail 비율에 맞춰 줄여 과도한 빈 공간을 남기지
  않았다.
- typecheck, frontend unit test 80개, lint, production build와
  `git diff --check`가 모두 통과했다.
- Docker frontend 이미지를 다시 빌드하고 frontend 컨테이너만 교체했다. 배포된
  HTML, CSS와 JavaScript는 모두 HTTP 200을 반환했으며 새 layout selector와
  thumbnail 크기 제한이 포함됐다.
- 자동 브라우저에서 Docker 로그인 화면과 console error·warning 0건을 확인했다.
  실제 사용자 계정으로 문서 상세 화면을 다시 여는 검사는 계정을 임의로 만들거나
  사용자 문서를 열람하지 않기 위해 `NOT_RUN`으로 남겼다.
- backend, DB, Docker volume과 API 계약은 변경하지 않았다.
