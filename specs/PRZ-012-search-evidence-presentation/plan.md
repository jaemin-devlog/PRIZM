# PRZ-012 — 검색 근거 표현 품질 개선 Plan

> **문서 상태:** `IMPLEMENTED_UNVERIFIED`
> **계획 기준선:** `origin/main` `0a3b5853b9f6f114e9c47d4dd7b0b2db8b8f8641`
>
> 이 문서는 구현 전에 선택한 접근과 단계별 계획을 보존한다. 실제 실행 결과는
> [Tasks](tasks.md)와 [Evidence](evidence.md)를 따른다.

## P1. 기준선과 검색 결과 불변 장치

- 목표: PRZ-008 검색 결과 선택과 표현 단계를 분리하고 전후 비교 기준을 남긴다.
- 변경 범위:
  - 현재 검색 흐름과 `SearchSnippetGenerator`의 Before 출력을 고정한다.
  - result ID·순서·score·distance를 비교하는 회귀 검증을 준비한다.
  - PRZ-012 Spec과 Registry를 등록한다.
- 검증:
  - `SearchService`가 선택·완전 동일 본문 중복 축약 뒤에만 snippet을 만드는지
    확인한다.
  - 검색 SQL, profile, threshold·후보·최종 개수와 평가 dataset의 diff가 0인지
    확인한다.
- Rollback: 새 문서와 표현 계층 변경만 제거하며 PRZ-008 source·문서는 건드리지
  않는다.
- 중단 조건: 결과 불변을 검증할 안정적인 비교 지점을 만들 수 없으면 구현을
  중단한다.

## P2. 추출형 근거 문장 선택

- 목표: 선택된 content에서 질문에 직접 답하는 원문 1–3문장을 고른다.
- 변경 범위:
  - `SearchSnippetGenerator`의 문장별 핵심어·수행·문제·결과 신호를 조합한다.
  - 질문과 관련된 수행 문맥을 단순 기술 목록보다 우선한다.
  - 필요한 인접 문장만 포함하고 문장을 자르거나 생성하지 않는다.
- 검증:
  - 핵심어, 수행 문맥, 문제→행동→결과, 인접 문장, 최대 3문장을 unit test한다.
  - 선택한 각 줄이 원래 content의 연속 부분 문자열인지 확인한다.
  - empty·blank·한 문장·긴 문장을 안전하게 처리한다.
- Rollback: 기존 snippet 생성기로 되돌려도 전체 content와 검색 결과는 유지된다.
- 중단 조건: 원문 문장을 다시 쓰거나 검색 결과 선택에 개입해야 하면 중단한다.

## P3. 근거 중심 검색 카드

- 목표: 핵심 근거, 출처, 기타 metadata 순서로 카드의 시각적 계층을 바꾼다.
- 변경 범위:
  - `frontend/src/App.tsx`의 검색 결과 카드 구조를 최소 수정한다.
  - `frontend/src/styles.css`에 기존 token 기반 근거 강조·출처 스타일을 추가한다.
  - 기존 전체 원문 펼치기와 score 표시를 유지한다.
- 검증:
  - TypeScript build와 lint를 통과한다.
  - 브라우저에서 핵심 근거와 전체 원문 펼치기를 확인한다.
- Rollback: 카드 markup과 관련 style만 기존 구조로 복원할 수 있다.
- 중단 조건: 디자인 시스템 밖의 대규모 재설계나 unrelated frontend 변경이
  필요하면 중단한다.

## P4. 중복 및 검색 계약 회귀

- 목표: 표현 개선이 PRZ-008 결과와 중복 경계를 바꾸지 않았음을 증명한다.
- 변경 범위: `SearchServiceTest`와 기존 검색 평가 실행 결과만 사용하며 ranking
  source는 수정하지 않는다.
- 검증:
  - 완전히 같은 full-body는 축약하고 내용이 다른 근거는 유지한다.
  - 전후 result ID·순서·score·distance가 같다.
  - PRZ-008 평가와 backend unit·integration을 실행한다.
- Rollback: 회귀가 생기면 UI와 selector 변경을 통합하지 않는다.
- 중단 조건: threshold, top 20, max 5, P4, P18, intent, Claim Gate, owner·ACTIVE
  조건 중 하나라도 바뀌면 중단한다.

## P5. 대표 질의와 VERIFY 기록

- 목표: 대표 질의 Before/After, 전체 회귀와 문서 정합성을 실제 결과로 남긴다.
- 변경 범위:
  - 실제 이용 가능한 등록 문서 환경에서 대표 질의 7개를 실행한다.
  - `evidence.md`, Registry와 현재 상태·아키텍처 문서를 실제 결과에 맞춘다.
- 검증:
  - backend unit·integration, PRZ-008 검색 평가를 실행한다.
  - frontend lint·build와 가능하면 browser를 확인한다.
  - Markdown 링크, `git diff --check`와 변경 범위를 확인한다.
- Rollback: 필수 검증 실패나 실제 문서 환경 `NOT_RUN`이면 `VERIFIED`로 올리지
  않고 정확한 상태와 재개 조건을 남긴다.
- 중단 조건: 합성 fixture를 실제 개인 문서 결과로 표현하거나 `NOT_RUN`을
  `PASS`로 바꿔야 하면 중단한다.

## 공통 위험과 대응

- 표현 선택은 검색 결과 확정 뒤 실행해 ID·순위·score·distance에 영향을 주지
  않는다.
- 수행 단어는 질문 핵심어와 함께 있을 때만 우선순위 신호로 사용한다.
- 문장 수 제한을 위해 원문을 잘라 새 문장처럼 만들지 않는다.
- snippet 생성 오류는 전체 content fallback으로 fail-safe 처리한다.

## Security 및 ownership 영향

- API 경로, JWT와 owner-scoped repository 조건을 변경하지 않는다.
- snippet은 이미 권한 검증을 거쳐 선택된 현재 사용자의 ACTIVE content에서만 만든다.

## Migration·dependency·license 영향

- migration과 dependency를 추가하지 않는다.
- LICENSE·NOTICE·SBOM 경계는 바뀌지 않는다.

## Branch와 통합 경계

- 작업 브랜치는 `PRZ-012-search-evidence-presentation`이다.
- VERIFY 뒤 멈추며 AUDIT, commit, push와 PR은 수행하지 않는다.
- `CompositeSearchProfile`, `ShortGeneralExactTokenRescueProfile`,
  `VectorSearchRepository`, PRZ-008 문서와 평가 dataset은 수정하지 않는다.

## PLAN Gate

- PRZ-012-R1–R9가 P1–P5 구현·검증 단계에 연결됐다.
- 검색 결과 불변과 실제 문서 환경의 중단 조건이 명시됐다.
- rollback, ownership, migration·dependency·license 영향이 빠지지 않았다.

판정: `PASS`
