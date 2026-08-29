# PRZ-021 — Fresh Clone 첫 사용자 경험 정합화

> **상태:** `VERIFIED`
> **유형:** Frontend UX / Documentation / Verification
> **기준 소스:** `fb8befe3fa876882f1c8b918097d3e7d4774d53d`
> **작성일:** 2026-08-29

## 문제

GitHub `main`을 별도 경로에 clone해 공개 Quickstart만 따라 수행한 첫 사용자 검증에서
signup, login, TXT/PDF 처리, 검색, owner isolation과 persistence는 동작했다. 그러나
일반 Career Evidence의 TXT 결과에는 원문 이동 action이 없었고, Quickstart 일부 문구는
실제 문서 보관함 탐색·상태 표시와 달랐다. 유지관리자 clean-clone 절차도 API verifier가
만든 임시 USER와 별도 browser USER의 문서를 같은 것으로 취급하는 모순이 있었다.

MCP의 `notifications/initialized` warning은 같은 기준 소스와 기존 PRZ-015 evidence에서
재현된다. 조사 결과 PRIZM 설정 누락이 아니라 MCP Java SDK 2.0.0 stateless server가
notification handler를 등록하지 않아 남기는 비차단 upstream warning이다. raw protocol
성공을 범용 client 성공으로 확대하지 않고 실제 범용 client를 별도 검증해야 한다.

## ORIENT 결과

- local `main`, `origin/main`, 원격 `main`은 모두 기준 소스와 일치했고 작업 트리는 clean이었다.
- Spec Registry, local/remote refs와 tags에는 PRZ-000∼020만 있어 다음 ID는 PRZ-021이다.
- 일반 검색 화면은 PDF viewer target이 있을 때만 `문서에서 보기` 버튼을 렌더링한다.
  `TEXT_CHUNK` 결과는 target이 `null`이라 link와 button이 모두 없다.
- 검색 응답에는 이미 `documentId`, `documentVersionId`, source metadata가 있다. 검색과
  문서 상세/original API는 현재 USER owner 범위를 적용하므로 backend API 변경은 필요 없다.
- 문서 상세 deep link는 현재 `documentId`만 보존한다. 검색 뒤 active version이 바뀌는
  경우에도 클릭한 결과의 version을 정확히 열려면 기존 route에 선택적 `versionId`가 필요하다.
- clean-clone helper는 한 임시 USER로 signup → login → empty owner → upload → ACTIVE →
  search를 일관되게 수행한다. 결함은 helper가 아니라 별도 browser USER에게 helper 소유
  문서를 기대하게 하는 Quickstart 설명이다.
- MCP protocol lifecycle은 initialize 응답 뒤 client의 initialized 알림을 요구한다.
  Java SDK 2.0.0 stateless server는 이 알림을 warning 뒤 소비하며 list/call을 계속 처리한다.

## 사용자 시나리오

```text
fresh clone → .env → Ollama → Docker Compose → signup → login
→ TXT upload → ACTIVE → TXT search → 결과에서 정확한 문서·version 상세/원문
→ PDF upload → ACTIVE → PDF search → 근거 page
→ USER2 격리 → down/up → persistence
```

유지관리자 자동 API verifier와 수동 browser 검증은 각각 자신이 만든 USER identity와
그 USER가 업로드한 문서만 확인한다.

## 범위

### 포함

- 일반 Career Evidence TXT 결과의 기존 Document Detail route 이동 action
- deep link의 선택적 version ID와 owner-scoped detail 응답 안에서의 version 선택
- PDF page viewer 분기의 기존 동작 보존
- 관련 frontend unit/component test
- 실제 UI의 유형 폴더와 처리/ACTIVE 표시를 반영한 최소 Quickstart 교정
- 기존 환경과 분리할 때만 쓰는 고유 Compose project 안내
- Docker/Ollama PATH, Docker Engine과 health `UP` troubleshooting
- clean-clone API USER와 browser USER의 owner 범위 분리
- Java SDK warning의 upstream 한계 기록과 범용 MCP client 실제 검증
- 새 Compose project·새 volume의 first-user browser E2E와 persistence 재검증
- PRZ-021 Spec/Plan/Tasks/Evidence와 현재 문서 현행화

### 제외

- 검색 algorithm, embedding model/dimension, threshold와 ranking 변경
- 인증, JWT, 역할, owner 정책과 공유/admin 문서 기능 변경
- 검색·문서 API 추가 또는 새 상세/PDF 화면
- DB schema와 Flyway migration 변경
- Spring AI/MCP dependency 강제 override, logger warning 숨김 또는 transport 교체
- clean-clone helper credential 출력·지속화
- 과거 PRZ-004/015/020 evidence와 archive 결과의 소급 수정
- commit, push와 PR

## 요구사항과 완료 조건

### `PRZ-021-R1` — TXT 검색 결과에서 정확한 원문 이동

`TEXT_CHUNK` 결과는 PDF 결과와 같은 action 위치에 `문서에서 보기`를 제공해야 한다.
클릭하면 기존 `/career-vault/documents` 상세 route에서 결과의 `documentId`와
`documentVersionId`에 해당하는 version을 선택해야 한다. 선택 version은 owner-scoped
상세 응답에 실제 포함된 경우에만 사용하고, 다른 USER의 상세·원문을 노출하지 않는다.

### `PRZ-021-R2` — PDF navigation 회귀 없음

`PAGE` 결과는 기존 original endpoint와 `evidenceSourceIndex`의 1-based page를 사용한다.
TXT action 추가가 PDF callback, blob viewer와 `#page=N&zoom=page-width`를 바꾸면 안 된다.

### `PRZ-021-R3` — 첫 사용자 Quickstart 정합성

일반 Quickstart는 다음을 짧고 실제 UI와 일치하게 설명해야 한다.

- 업로드한 문서는 문서 보관함의 해당 문서 유형 폴더에서 확인
- 목록 `검색 준비 완료`, 상세 `검색에 사용 중`, version `현재 · ACTIVE`의 관계
- 기존 PRIZM Docker state와 분리할 때만 고유 `COMPOSE_PROJECT_NAME` 사용
- Docker/Ollama command not found 시 설치·실행·PATH 확인
- Docker Engine 실행 요구
- health 응답의 `status`가 정확히 `UP`일 때 다음 단계 진행

### `PRZ-021-R4` — clean-clone owner identity 일치

자동 verifier는 자체 임시 USER의 데이터만 검증하고 credential을 출력·저장하지 않는다.
수동 browser USER는 verifier USER와 별개임을 명확히 하고, 빈 보관함·교차 marker 0건을
먼저 확인한 뒤 같은 browser USER로 합성 TXT/PDF를 직접 업로드해 상세·검색을 확인한다.
owner isolation을 우회하거나 공유/admin 기능을 추가하지 않는다.

### `PRZ-021-R5` — MCP warning과 범용 client 경계

`notifications/initialized` warning이 Java SDK 2.0.0 stateless server의 비차단 upstream
동작이라는 source 근거를 기록하고 PRIZM Production code를 억지로 수정하지 않는다.
가능하면 공식 MCP Inspector 같은 범용 client로 live fresh endpoint의 initialize,
tools/list와 `search_career_evidence`를 실행한다. 실행하지 못하면
`MCP_REAL_CLIENT_NOT_VERIFIED`로 기록하며 raw protocol 결과로 대체하지 않는다.

### `PRZ-021-R6` — 회귀·검증

- focused action/route frontend test, 전체 frontend unit/lint/typecheck/build
- clean-clone script test
- MCP Production code를 바꾸면 관련 backend test; 바꾸지 않아도 공식 Java client
  protocol baseline test
- 새 Compose project와 volume의 실제 USER1/USER2 TXT/PDF browser E2E, down/up persistence
- browser console `ERROR` 0
- Markdown local links, repository readiness와 `git diff --check`
- 검색/auth/owner/schema/dependency 변경 0과 민감정보 포함 0

## 보안·ownership·호환성 영향

- browser route의 ID는 권한 근거가 아니다. backend의 현재 USER owner 조회가 계속
  접근을 결정한다.
- requested version ID는 이미 owner-scoped document detail에 포함된 version만 선택한다.
- API, search result schema, auth, owner SQL, DB schema와 stored data는 바뀌지 않는다.
- MCP dependency와 server configuration은 이번 범위에서 유지한다.

## SPEC Gate

- 다섯 finding의 runtime/source/document 원인을 기준 소스에서 구분했다.
- backend·schema·검색 변경 없이 frontend route/action과 문서 교정으로 해결 가능하다.
- MCP warning은 upstream 한계로 확인돼 무리한 Production workaround를 제외했다.
- R1∼R6은 자동 test와 fresh browser/runtime 결과로 판정 가능하다.
- blocking ambiguity 없음.

판정: `PASS`
