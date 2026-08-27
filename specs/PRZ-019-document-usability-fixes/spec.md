# PRZ-019 — 태그 문서 수 명확화와 TXT 원문 미리보기

> **상태:** `VERIFIED`
> **유형:** Bug fix / UX
> **기준 소스:** `5b2c50fbe0d6fac0a9ee5c9d51f5365392c37a69`
> **구현 소스:** `4932aa8`
> **작성일:** 2026-08-27

## 문제

- 경력 키워드 목록의 `N개 문서`는 태그가 연결된 문서 수인데, 문서 본문에서
  해당 문자열을 찾은 문서 수로 오해할 수 있다.
- TXT 원본은 owner-scoped original API로 제공되지만 문서 보관함은 정적인 TXT
  대체 화면만 표시하고 상세 화면에서 원문을 열 수 없다.

## 범위

### 포함

- 태그 사용 수를 `N개 연결 문서`로 표시하고 metadata 기준임을 설명한다.
- 문서 보관함 카드와 상세 화면에서 UTF-8 TXT 일부 내용을 미리보기로 표시한다.
- 문서 상세에서 TXT/PDF 모두 기존 original API로 원문을 연다.
- TXT 원문은 읽기 전용 텍스트 viewer로 표시하고 PDF viewer 동작은 유지한다.
- loading, empty, 오류, 재시도와 object URL 해제를 유지한다.

### 제외

- 태그 집계 SQL, 검색 알고리즘, Search 결과와 tag-document 관계 변경
- backend API, 인증, ownership, 저장소와 보안 header 변경
- DB schema, migration, dependency와 새 문서 형식
- TXT 편집, 문법 강조와 이미지 thumbnail 생성

## 요구사항 및 완료 조건

### `PRZ-019-R1` — 연결 수의 의미

경력 키워드 목록은 태그가 연결된 문서 수를 `N개 연결 문서`로 표시한다. 화면은
이 수가 문서 본문에서 자동으로 확인한 출현 수가 아니라 사용자가 연결한 태그
metadata 기준임을 설명한다.

### `PRZ-019-R2` — TXT 미리보기

owner-scoped original API에서 받은 UTF-8 TXT를 정규화하고 길이를 제한해 문서 카드와
선택 버전 미리보기에 표시한다. 공백 파일, 읽기 실패와 인증 만료를 구분하며 전체
원문을 목록 DOM에 그대로 노출하지 않는다.

### `PRZ-019-R3` — TXT 원문 열기

문서 상세의 `원문 열기`는 선택한 TXT/PDF 버전에 모두 제공한다. PDF는 기존 iframe,
TXT는 줄바꿈을 보존하는 읽기 전용 viewer로 표시한다. 요청 취소·viewer 닫기·컴포넌트
해제 때 진행 중 요청과 object URL을 정리한다.

### `PRZ-019-R4` — 보존 계약

기존 JWT와 owner-scoped original endpoint, PDF thumbnail, immutable version,
ACTIVE 전환, 검색·MCP와 tag metadata 계약을 변경하지 않는다.

### `PRZ-019-R5` — 검증

frontend unit test, typecheck, lint와 production build가 통과하고 브라우저에서 TXT 카드
미리보기와 TXT/PDF 원문 열기를 확인한다. backend·SQL·migration·dependency 변경은 0이어야 한다.

## SPEC Gate

- 두 증상의 실제 원인과 수정 경계가 구분됐다.
- 기존 API만으로 요구사항을 구현할 수 있다.
- 검색 결과나 tag 집계 의미를 바꾸지 않는다.

판정: `PASS`
