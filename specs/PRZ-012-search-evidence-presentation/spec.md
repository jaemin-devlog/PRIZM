# PRZ-012 — 검색 근거 표현 품질 개선

> **상태:** `IMPLEMENTED_UNVERIFIED`
> **유형:** Feature
> **선행 문서:** [PRZ-008](../PRZ-008-search-evidence-reliability/spec.md)
> **기준 소스:** `0a3b5853b9f6f114e9c47d4dd7b0b2db8b8f8641`
> **최종 확인:** 2026-08-13

## 목적

PRZ-008이 선택한 검색 결과의 ID·순서·score·distance는 그대로 두고, 각 결과의
전체 원문에서 질문에 직접 답하는 문장 1–3개를 선택해 사용자가 결과의 이유를 먼저
이해할 수 있게 한다.

현재 검색은 결과 선택 뒤 `SearchSnippetGenerator`가 질문 핵심어와 인접 문장을
기준으로 snippet을 만들고 frontend가 전체 원문과 함께 표시한다. 다만 수행 경험이나
문제·행동·결과 문맥의 우선순위가 제한적이고, 카드의 시각적 중심도 문서명과 score에
있어 근거가 먼저 읽히지 않는 경우가 있다.

## 기능 구성과 동작 흐름

```text
사용자 질문
→ PRZ-008 검색 결과 확정
→ 결과별 전체 content 문장 분리
→ 질문 관련성과 수행 문맥으로 원문 1–3문장 선택
→ 기존 snippet 응답 필드에 저장
→ frontend 근거 카드에 핵심 근거 우선 표시
→ 요청 시 기존 전체 content 펼침
```

snippet 선택은 검색 결과가 확정된 뒤에만 실행한다. 검색 후보, 판정, 중복 축약,
순위와 score를 다시 계산하지 않는다.

## 범위

### 포함

- 선택된 각 결과의 `content`에서 질문에 직접 답하는 원문 문장 1–3개를 추출한다.
- 질문 핵심어 일치와 함께 실제 수행 문맥을 우선한다.
- 의미가 끊길 때 핵심 문장의 앞뒤 문장을 포함한다.
- 기존 `snippet` 응답 필드와 전체 `content`를 함께 유지한다.
- frontend 검색 결과 카드의 시각적 우선순위를 핵심 근거, 출처, 기타 metadata
  순서로 조정한다.
- 기존 전체 원문 펼치기를 유지한다.
- 완전히 동일한 full-body 중복만 기존 방식대로 제거한다.

### 제외

- threshold `0.50`, top 20, 최종 최대 5개와 ranking weight 변경
- P4 soft ranking, P18 exact-token rescue, intent와 Claim Gate 변경
- 검색 SQL, candidate 수, distance, score, 결과 ID·순서 변경
- chunking, embedding model, FTS·RRF·BGE Sparse·reranker 변경
- 의미 유사도 기반 dedup, 같은 경험의 서로 다른 문서 근거 제거
- 외부 AI API, 로컬 LLM, 생성형 요약과 원문에 없는 문장 생성
- migration, dependency, OpenHA, Ollama GPU와 Docker 구조 변경
- 검색 화면 밖의 frontend 리팩터링
- 기존 PRZ-008 문서 변경

## 근거 문장 선택 계약

- 선택 단위는 검색 결과의 기존 `content` 안에 실제로 존재하는 문장 또는 줄이다.
- 질문의 비일반 핵심어를 여러 개 포함하는 문장을 우선한다.
- 핵심어 관련성이 있는 후보 안에서 구현·개선·설계·적용·해결·통합·운영·검증·도입·
  분리·전환·최적화 같은 실제 수행 문맥을 보조 신호로 사용한다.
- 특정 수행 단어만 있다는 이유로 질문과 무관한 문장을 우선하지 않는다.
- 문제와 수행, 수행과 결과 또는 문제·수행·결과가 이어지는 인접 문맥은 최대 3문장
  안에서 함께 선택할 수 있다.
- 표시를 위한 줄바꿈과 가장자리 공백 정리 외에는 선택한 문장을 다시 쓰지 않는다.
- 선택 실패나 예상하지 못한 오류에서는 기존 전체 `content`를 보존하고 fallback으로
  사용한다.

## UI 계약

- 검색 카드에서 `핵심 근거`가 첫 번째 의미 블록이다.
- 출처는 문서명과 `sourceLabel`을 함께 보여준다.
- version과 score는 근거·출처보다 낮은 시각적 우선순위로 표시한다.
- 전체 원문은 기본적으로 접혀 있고 `전체 원문 보기`로 기존 `content`를 그대로
  확인할 수 있다.
- score는 기존과 같이 검색 유사도 표시일 뿐 정답 확률로 표현하지 않는다.

## 중복 근거 계약

- 줄바꿈과 가장자리 공백만 정규화했을 때 full-body가 완전히 동일한 결과는 기존처럼
  첫 결과만 표시할 수 있다.
- 같은 경험을 다루더라도 content가 다른 이력서 요약과 포트폴리오 상세 근거는 각각
  유지한다.
- 의미 유사도 threshold를 새로 도입하거나 검색 결과를 추가로 제거하지 않는다.

## 보존 계약

- Dense search threshold `0.50`, top 20 후보와 최종 최대 5개를 유지한다.
- P4 soft ranking, P18 exact-token rescue, `GENERAL`과
  `COMPLETED_RELEASE_EVIDENCE`, Claim Gate를 유지한다.
- 검색 SQL과 exact cosine distance, score, 결과 ID·순서를 유지한다.
- owner isolation과 `ACTIVE` version만 검색하는 조건을 유지한다.
- 기존 완전 동일 full-body 중복 축약과 전체 content 응답을 유지한다.
- PRZ-008의 spec, plan, tasks, evidence는 수정하지 않는다.

## 요구사항 및 완료 조건

### `PRZ-012-R1` — 추출형 핵심 근거

각 검색 결과는 기존 content에서 선택한 질문 관련 원문 1–3문장을 snippet으로
제공하며 생성형 문장을 포함하지 않는다.

### `PRZ-012-R2` — 관련성과 수행 문맥

핵심어가 같은 단순 기술 목록과 실제 수행 설명이 함께 있을 때 질문 관련성이 있는
수행 설명을 우선하고, 필요한 앞뒤 문맥을 최대 3문장 안에서 포함한다.

### `PRZ-012-R3` — 원문 보존과 안전한 fallback

기존 전체 content를 응답과 펼치기 UI에 그대로 유지하고 empty·short content와
선택 오류를 안전하게 처리한다.

### `PRZ-012-R4` — 근거 중심 카드

frontend는 핵심 근거를 문서명·페이지와 기타 metadata보다 먼저 읽히게 표시하고
전체 원문 보기를 유지한다.

### `PRZ-012-R5` — 중복 경계

완전히 동일한 full-body 중복은 기존처럼 축약하되 content가 다른 중요한 근거는
제거하지 않는다.

### `PRZ-012-R6` — 검색 결과 불변

대표 질의와 기존 검색 평가에서 PRZ-012 전후 result ID, 순서, score와 distance가
같다.

### `PRZ-012-R7` — 검색 계약 보존

threshold, 후보 수, 최종 결과 수, 검색 SQL, P4, P18, intent, Claim Gate,
owner·ACTIVE 경계가 바뀌지 않는다.

### `PRZ-012-R8` — 대표 질의 검증

`동시성`, `알림`, `Springboot 활용 경험`, `FOR UPDATE SKIP LOCKED`,
`이메일 로그인과 카카오 로그인을 통합한 경험`, `TourAPI 병렬 처리 경험`,
`2,329행 중 675건 갱신`의 Before/After와 결과 불변 여부를 실제 이용 가능한
문서 환경에서 기록한다.

### `PRZ-012-R9` — 회귀 검증

핵심어·수행 문맥·인접 문장·최대 3문장·원문 보존·기술 목록 비우선·empty·short
content·duplicate 회귀 unit test와 기존 PRZ-008 검색 평가, backend unit·integration,
frontend lint·build와 `git diff --check`가 통과한다.

## 보안·migration·dependency 영향

- 인증과 owner-scoped 검색 경계를 변경하지 않는다.
- 새 migration과 dependency를 추가하지 않는다.
- 원문과 평가 결과는 기존 gitignore 경계를 따르며 개인 문서나 credential을
  저장소에 추가하지 않는다.

## SPEC Gate

- 표현 계층 변경과 검색 결과 불변 조건이 분리됐다.
- 추출형 원문, 최대 3문장, 전체 content와 중복 경계가 판정 가능하다.
- 검색·owner·ACTIVE·migration·dependency 보존 계약이 명시됐다.

판정: `PASS`
