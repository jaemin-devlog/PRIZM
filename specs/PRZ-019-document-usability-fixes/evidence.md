# PRZ-019 — Evidence

> **상태:** `VERIFIED`
> **기준 소스:** `5b2c50fbe0d6fac0a9ee5c9d51f5365392c37a69`

## 원인 확인

- `GET /api/tags/usage`와 `DocumentTagRepository.findUsage`의 `document_count`는
  `document_tags`에 연결된 서로 다른 문서 수다. 문서 본문 속 문자열 출현 수가 아니다.
- TXT/PDF original endpoint는 이미 JWT와 owner 범위 확인, private no-store,
  `nosniff`, sandbox header를 적용해 두 형식을 반환한다.
- frontend는 original helper를 갖고 있었지만 문서 카드·상세 화면은 PDF thumbnail과
  PDF 원문만 연결하고 TXT에는 정적인 대체 화면만 표시했다.

## 구현 결과

- 경력 키워드 수를 `N개 연결 문서`로 바꾸고 본문 출현 수가 아니라는 설명을 추가했다.
- TXT 원문을 260자 카드 preview와 2,000자 상세 preview로 제한해 표시한다.
- 상세 `원문 열기`는 선택한 TXT/PDF 모두 제공한다. PDF는 기존 object URL iframe,
  TXT는 전체 원문의 줄바꿈을 보존하는 읽기 전용 viewer를 사용한다.
- 요청 취소, object URL 해제, 인증 만료, 빈 TXT와 읽기 실패 상태를 유지했다.

## VERIFY

| 검사 | 결과 |
|---|---|
| `npm --prefix frontend run test:unit` | `PASS` — 85/85 |
| `npm --prefix frontend run typecheck` | `PASS` |
| `npm --prefix frontend run lint` | `PASS` |
| `npm --prefix frontend run build` | `PASS` |
| `.\gradlew.bat test --no-daemon` | `PASS` — `BUILD SUCCESSFUL` |
| `git diff --check` | `PASS` |
| 로그인된 브라우저 TXT/PDF 화면 | `PASS` — 사용자가 로컬 Docker 화면에서 태그 문구, TXT preview와 TXT/PDF 원문 viewer를 직접 확인 |

## AUDIT

- backend production source, SQL/migration, Gradle/npm dependency 변경: 0
- 검색, MCP, tag 집계 SQL과 owner-scoped original API 계약 변경: 0
- 기존 미추적 `tmp/` 영상 작업: 보존, 변경 없음
- commit, push, PR: `NOT_RUN`

## 판정

`VERIFIED`

자동 검사와 source 감사가 통과했고, 로그인된 로컬 Docker 화면의 TXT 카드·상세
preview와 TXT/PDF 원문 viewer는 사용자가 직접 확인했다.
