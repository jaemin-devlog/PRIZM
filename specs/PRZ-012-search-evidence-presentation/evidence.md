# PRZ-012 — 검색 근거 표현 품질 개선 Evidence

## 최종 판정

- 상태: `VERIFIED`
- 기준 source: `0a3b5853b9f6f114e9c47d4dd7b0b2db8b8f8641` 기반 미커밋 작업 트리
- 검증일: 2026-08-13
- VERIFY Gate: `PASS`
- AUDIT·INTEGRATE: 요청 범위 밖

실제 이력서와 포트폴리오를 정상 `USER` 계정으로 업로드하고 처리 완료된 ACTIVE
version에서 대표 7개 질의를 실행했다. 검색 결과의 ID·순서·score·distance와 전체
content는 유지하면서, 선택된 chunk에 직접 근거가 부족한 두 질의만 동일 owner·동일
document·동일 ACTIVE version의 다른 chunk에서 원문 근거를 확장했다.

## Evidence Expansion 경계

```text
기존 PRZ-008 검색 결과 확정
→ 현재 chunk의 selector 결과가 직접 근거인지 판정
→ 충분하면 기존 snippet·출처 유지
→ 부족하면 동일 owner + 동일 document + 동일 ACTIVE version chunk만 조회
→ 가장 직접적인 원문 1~3문장과 실제 evidence chunk·출처 반환
```

- `VectorSearchRepository`, 검색 SQL, threshold `0.50`, Top20, max5, P4, P18,
  SearchIntent, Claim Gate, candidate 선택·순서·score·distance는 변경하지 않았다.
- 확장 조회 SQL은 document·version·chunk owner가 모두 로그인 사용자와 일치하고,
  `documents.active_version_id = document_versions.id`, version `ACTIVE`, 검색 결과의
  document ID와 version ID가 모두 일치할 때만 원문 chunk를 반환한다.
- API는 기존 검색 출처를 보존하고 실제 근거 위치인 `evidenceChunkId`,
  `evidenceSourceType`, `evidenceSourceIndex`, `evidenceSourceLabel`을 추가했다.
- UI 출처는 실제 `evidenceSourceLabel`을 표시하며 기존 전체 원문 보기는 검색 결과의
  전체 `content`를 그대로 유지한다.

## Focused 3개 질의

| 질의 | 검색 chunk | evidence chunk·출처 | 판정 |
|---|---|---|---|
| `Springboot 활용 경험` | 57, 이력서 1페이지 | 61, 이력서 2페이지 — Spring Boot·MySQL·Redis 운영 부담과 Docker Compose 구성 수행 원문 2문장 | PASS |
| `FOR UPDATE SKIP LOCKED` | 51, 포트폴리오 3페이지 | 51, 포트폴리오 3페이지 — identifier가 포함된 완전한 원문 문장 유지 | PASS |
| 이메일·Kakao 로그인 통합 | 58, 이력서 1페이지 | 60, 이력서 2페이지 — 분리 문제, OAuth2/JWT 통합, 계정 연결 구현 원문 3문장 | PASS |

두 확장 출처는 원본 이력서 PDF 2페이지의 렌더와 추출 텍스트를 함께 대조했다.
페이지 위치와 반환된 `evidenceSourceLabel`이 일치했다.

## 대표 7개 질의

| 질의 | 반환 chunk ID | 핵심 근거·출처 | 판정 |
|---|---|---|---|
| `동시성` | 58 | 4,400회 검증과 중복 저장 0건, 이력서 1페이지 | PASS |
| `알림` | 50 | 내부 알림과 FCM 실패 전파 분리, 포트폴리오 3페이지 | PASS |
| `Springboot 활용 경험` | 57, 45 | 실제 GCP/Docker 배포 수행, 이력서 2페이지가 첫 근거 | PASS |
| `FOR UPDATE SKIP LOCKED` | 51 | 직접 identifier와 중복 처리 방지 원문, 포트폴리오 3페이지 | PASS |
| 이메일·Kakao 로그인 통합 | 58 | 인증 통합과 계정 연결 구현, 이력서 2페이지 | PASS |
| `TourAPI 병렬 처리 경험` | 46, 60 | 병렬화·통합과 처리 시간 단축, 각 실제 검색 chunk 출처 | PASS |
| `2,329행 중 675건 갱신` | 56 | 2,329행·성공 675·제외 1,654 원문, 포트폴리오 5페이지 | PASS |

모든 응답에서 전체 content가 유지됐고, 기술 스택이나 일반 설명이 더 적절한 직접
수행 근거보다 먼저 표시되지 않았다. 기존 PASS 4건도 회귀하지 않았다.

## 검색 결과 불변성

- 실제 대표 7개 질의의 반환 ID·순서·score·distance는 PRZ-012 selector 검증 시
  기록한 값과 모두 동일했다.
- PRZ-008 `current-product` TUNING 15개 질의를 다시 실행해 이전 PRZ-012 평가
  `dense-baseline-20260813-063725-5517b589.json`과 비교했다.
- 비교 항목: search state, 반환 chunk ID·순서, top-1 score·distance, 전체 candidate의
  rank·chunk ID·score·distance.
- 차이: `0건`.

## 자동 검증

- Focused backend: `SearchSnippetGeneratorTest`, `EvidenceExpansionServiceTest`,
  `SearchServiceTest` — PASS.
- Backend unit: `476`건, failure `0`, error `0`, 조건부 skip `15` — PASS.
- Backend integration: `112`건, failure `0`, error `0`, 조건부 skip `7` — PASS.
- PRZ-008 search evaluation: 15개 질의 실행 성공, 불변 차이 `0건` — PASS.
- Frontend unit: `6`건 PASS.
- Frontend lint: PASS.
- Frontend TypeScript·Vite production build: PASS.
- 현재 source의 backend·frontend Compose build와 실제 USER API 흐름: PASS.
- `git diff --check`: PASS.
- 검색 profile·repository SQL·migration·compose 변경: `0건`.

## VERIFY Gate

판정: `PASS`

실제 개인 문서 대표 7개 질의, focused 실패 3건, 실제 evidence 출처, PRZ-008 검색
불변성, backend/frontend 전체 회귀 검증을 모두 통과했다.
