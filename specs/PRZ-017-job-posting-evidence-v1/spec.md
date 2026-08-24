# PRZ-017 — 채용공고 항목별 Career Evidence V1

## 상태

- 상태: `IN_PROGRESS`
- 기준선: `d44f30eb4346353c4363d559be478024f191a878`
- 현재 source: `de98bcf`
- 검증: 자동 검증·독립 감사 `PASS`, 인증 browser `BLOCKED_BY_AUTH_ENVIRONMENT`

이 문서는 채용공고를 붙여넣고 필요한 항목을 사용자가 직접 선택한 뒤, 기존
PRZ-016 Career Evidence Search로 관련 원문 기록을 확인하는 대회 제출용 V1 계약을
정의한다. Spec과 화면 문구는 구현·검증 증거가 아니며 실제 상태는 source와 실행
가능한 test, [Evidence](evidence.md)를 따른다.

## 사용자 시나리오

```text
로그인
→ 채용공고 붙여넣기
→ 항목 나누기
→ modal에서 필요한 항목 선택
→ 관련 경력 찾기
→ 결과 전용 화면에서 선택 항목별 Career Evidence 확인
→ PDF 해당 페이지 또는 TXT 문서 상세로 이동
```

사용자가 체크한 항목만 검색 질의가 된다. 자동 분석기가 지원 적합성이나 경험의
진위를 판단하지 않으며, 검색 결과도 채용 요구사항의 충족 판정으로 해석하지 않는다.

## `PRZ-017-R1` — 결정적 항목 분리

- Production 분리는 Qwen, 다른 LLM, 외부 NLP API, prompt와 model response parser 없이
  같은 입력에 같은 결과를 반환한다.
- 줄바꿈, `-`, `*`, `•` 등의 bullet, `1.`, `2.`, `1)` 등의 numbered list와 명확한
  문장 종결 경계를 사용한다.
- `-`, `*`, `•`, `·`, `▪`, `○` 계열 bullet·번호 prefix는 공백 유무와 관계없이 제거하고
  연속 공백을 한 칸으로 정규화한다. 단 `-10ms`, `--enable-preview`, `*.java`처럼 본문인
  선행 기호는 보존한다.
- 행이 `:`로 끝나거나, 알려진 직무·자격·우대/채용 metadata section 성격이거나, 40 code
  point 이하의 compact 독립 행 뒤에 list 항목이 이어지면 section heading으로 취급한다.
  heading은 Search 항목이 아니라 각 child 항목의 nullable `section` 보조 정보로 유지한다.
- 직무·역할·자격·우대·기술요건 section의 child 항목은 선택 가능하게 유지한다. 복지,
  전형, 근무조건, 접수, 제출서류, 유의사항, 회사 소개 등 명백한 채용 metadata section의
  child 항목과 보수적으로 식별 가능한 인원·전형 단계·metadata `label: value` 행은 Search
  항목에서 제외한다.
- 알 수 없는 section은 heading만 구조 정보로 유지하고, 명백한 metadata가 아닌 child
  항목은 선택 가능하게 보존해 false negative를 제한한다.
- 정규화한 본문이 완전히 같은 항목은 첫 항목과 첫 section만 남긴다.
- 빈 항목과 정규화 뒤 문자·숫자가 2개 미만인 항목은 제거한다.
- Search query 제한에 맞춰 각 항목은 500자 이하로 무손실 분할하고, 분할된 각 항목도
  문자·숫자 2개 이상을 유지한다.
- 한 입력에서 반환하는 항목은 최대 100개다. 101개 이상이 되면 일부를 조용히
  누락하지 않고 `400 JOB_POSTING_ITEM_LIMIT_EXCEEDED`로 거부해 Search fan-out을 제한한다.
- 최종 항목은 원문 순서를 유지한다.
- 회사명, 기술명 또는 benchmark 문자열을 사전에 넣어 분기하지 않는다.

측정 기준은 bullet, numbered list, 일반 줄바꿈, section grouping, career/metadata 분류,
unknown section 보존, 독립 metadata 제거, 중복·빈 항목 제거, 원래 순서, 한글·영문 혼합과
임의 기술 문자열을 다루는 backend unit test다.

## `PRZ-017-R2` — 사용자 선택

- 분리 결과는 입력 화면 아래에 이어 붙이지 않고 modal dialog에서 원문 순서의 section
  그룹으로 표시한다. section heading은 일반 그룹 제목이며 checkbox가 아니고, 실제 선택
  가능한 child 항목에만 checkbox를 표시한다.
- 전체 선택, 전체 해제와 개별 선택을 제공하고 현재 선택 개수를 표시한다.
- 선택 항목이 0개면 검색 action을 disabled 상태로 두고 Search 요청을 보내지 않는다.
- dialog는 접근 가능한 이름, `aria-modal`, 최초 focus, Tab focus containment, Escape·backdrop·닫기
  action과 trigger focus 복귀를 제공한다.
- `관련 경력 찾기`를 실행하면 dialog를 닫고 `/career-vault/job-evidence/results` 전용 결과
  route로 이동한다. 검색 중에도 결과 route에서 loading state를 표시한다.
- 결과 화면의 `항목 다시 선택`은 입력문과 현재 선택을 유지한 dialog를 다시 연다. 결과
  route에서 브라우저 뒤로 가 입력 화면으로 돌아와도 같은 page controller가 유지되는 동안
  입력문과 선택을 보존한다.
- 사용자는 원래 채용공고를 다시 수정하고 재분리할 수 있다. 재분리하면 이전 선택과
  검색 결과를 새 항목으로 잘못 이어 붙이지 않는다.

사용자 선택이 V1의 유일한 semantic gate다.

## `PRZ-017-R3` — 기존 Search 소비

- 선택된 각 항목의 정규화된 본문은 원문 query로 항상 유지한다. `A / B`, 명확한 3항
  compact slash 목록, `또는`, 독립된 영문 `or`처럼 대안 구조가 분명한 항목만 PRZ-017
  consumer 계층에서 결정적으로 분해하고, 원문과 최대 5개의 variant를 기존
  `POST /api/career-evidence/search`에 순서대로 전달한다. 2항 결합 식별자와 파일 경로는
  compact slash 대안으로 취급하지 않는다.
- 쉼표는 그 자체로 분해하지 않는다. `A, B 또는 C`나 `A, B or C`처럼 명시적인 대안
  연결어 앞의 목록에 포함될 때만 variant 경계로 사용한다. 빈 값·중복·문자나 숫자가 거의
  없는 조각은 제거하며 회사명·기술명 사전, LLM과 동의어 확장은 사용하지 않는다.
- 원문 결과를 먼저 두고 variant 순서와 각 Search 응답 순서를 유지한다. 동일
  `documentId + documentVersionId + chunkId`는 먼저 나온 결과만 남기고, 서로 다른 문서
  버전이나 청크는 보존한 채 최종 결과를 기존 Top 5 범위로 제한한다. query별 score를
  합산·평균하거나 서로 비교해 새 순위를 만들지 않는다.
- 분해와 병합은 PRZ-017 frontend orchestration에만 둔다. PRZ-017 전용 batch 검색 backend,
  embedding model, ranking, relevance floor, fallback, rescue 또는 localization을 추가하지
  않는다.
- 기존 Search가 적용하는 현재 인증 사용자와 `ACTIVE` version 범위를 그대로 사용한다.
- tag와 document type을 검색 filter나 ranking boost로 전달하지 않는다.
- 하나의 항목 검색이 실패해도 이미 성공한 다른 그룹의 결과를 판정 값으로 바꾸거나
  삭제하지 않고 실패한 그룹에 중립적인 재시도 상태를 표시한다.

## `PRZ-017-R4` — 항목별 Evidence 표시

- 결과는 다른 Career Vault 목록과 같은 flat card 반복이 아니라 전용 결과 route의
  requirement 탐색 workspace로 표시한다. requirement rail은 `기록 있음`과 `기록 없음`
  상태 탭으로 나누고 각 탭에 해당하는 requirement 개수를 표시한다. loading 또는 error는
  Evidence 부재로 오인하지 않도록 해당 항목이 있을 때만 `확인 필요` 탭으로 분리한다.
- 각 상태 탭 안에서는 선택 항목의 원래 순서와 원래 번호를 유지한다. 탭과 requirement
  전환은 presentation state만 바꾸며 추가 Search 요청을 보내지 않는다. 기본 탭은 기록이
  있으면 `기록 있음`, 없으면 `기록 없음`, 두 상태가 아직 확정되지 않았으면 `확인 필요`다.
  오른쪽에는 현재 탭에서 사용자가 선택한 requirement 하나의 상태와 Evidence만 표시한다.
- 오른쪽 결과는 `requirement → document/version → Evidence row` 계층을 사용한다. 동일
  `documentId + documentVersionId`의 문서 제목·종류는 한 번만 표시하고, 각 Evidence row는
  PDF page 또는 TXT 위치, extractive 원문과 접을 수 있는 주변 문맥을 유지한다.
- Search 병합의 chunk identity와 Top 5 결과는 바꾸지 않는다. 다만 같은 문서·버전,
  `evidenceSourceType + evidenceSourceIndex`, 공백을 정규화한 표시 원문이 모두 같은 결과는
  화면에서 한 행으로 표시한다. 같은 page의 서로 다른 원문과 같은 원문의 다른 page는
  별도 Evidence row로 보존하며 표시 건수는 실제로 보이는 고유 행을 센다.
- 기존 Search response의 `documentId`, `documentVersionId`, `documentTitle`, `versionNo`,
  `snippet`, `content`, source/evidence source type·index·label을 재사용한다.
- 문서 종류는 필요할 때 기존 owner-scoped 문서 API에서 보조 metadata로 읽으며 Search
  response를 확장하지 않는다.
- `관련 기록 01`처럼 의미 없는 반복 번호는 표시하지 않는다. loading, empty와 error
  requirement도 rail에서 숨기지 않고, 활성 requirement의 오른쪽 영역에 중립적인 상태와
  필요한 재시도 action을 표시한다.
- `score`, `distance`, 적합도 %, 합격 가능성, 충족·불충족, 경험 있음·없음, PASS·FAIL,
  지원 추천·비추천을 표시하지 않는다.
- 결과가 0건이면 `관련 경력 기록을 찾지 못했습니다.`라는 중립적 empty state를 표시한다.

## `PRZ-017-R5` — 원문 이동

- PDF `PAGE` Evidence는 표시 근거의 `evidenceSourceIndex`를 1-based page로 사용하고,
  기존 인증된 original endpoint와 Blob PDF viewer를 재사용해 해당 page로 이동한다.
- TXT `TEXT_CHUNK` Evidence는 기존 문서 상세 route로 이동한다.
- 새 PDF renderer, OCR, 좌표 highlight 또는 별도 원문 저장소를 만들지 않는다.
- 원문 endpoint의 owner-scoped document/version 확인과 기존 보안 header를 유지한다.

## `PRZ-017-R6` — 인증과 데이터 격리

- segmentation과 화면 진입은 활성 `ROLE_USER`의 JWT 경계 안에 둔다.
- 기존 DB 사용자 재검증, 401 인증 실패와 403 권한 실패 정책을 유지한다.
- Evidence는 기존 Search SQL의 document·version·chunk owner 일치와
  `documents.active_version_id`, `ACTIVE` status 제한을 우회하지 않는다.
- `SYSTEM_ADMIN`은 개인 USER 문서나 Evidence 범위를 우회하지 않는다.

## 데이터·dependency 영향

- 채용공고 원문, 분리 항목, 선택과 검색 결과는 V1에서 영구 저장하지 않는다.
- requirement/evidence-link/fit entity, repository, table과 worker를 만들지 않는다.
- Flyway migration은 없다.
- 새 runtime dependency, model 또는 asset을 추가하지 않는다.
- Ollama `bge-m3`는 기존 PRZ-016 query embedding에만 사용하며 채용공고 분리·판정에
  사용하지 않는다. 과거 PRZ-016 Qwen·judge 평가 자료는 연구 기록으로 보존한다.

## 명시적 비범위

- Qwen 또는 다른 LLM 기반 분리·요약·판정
- fit scoring, Career Truth, CareerFact와 요구사항 충족 판정
- 태그 기반 Search filter와 자동 tag 연결
- 채용공고·항목·선택·Evidence의 DB 저장
- PRZ-016 검색 품질 조정, 신규 embedding과 새 PDF viewer
- 지원서 생성, 추천·비추천과 합격 가능성

## 완료 조건

| ID | 측정 가능한 완료 조건 |
|---|---|
| AC1 | backend segmentation 구조·순서·중복·긴 항목·최대 항목 수 계약 test가 모두 통과한다. |
| AC2 | blank/invalid/100개 초과 입력과 인증되지 않은 segmentation 요청이 기존 API 정책대로 거부된다. |
| AC3 | 입력·분리·개별 선택·전체 선택/해제·선택 수·0개 실행 방지 frontend test가 통과한다. |
| AC4 | 단순 항목은 원문으로 한 번만 검색하고, 명확한 compound 항목은 원문과 제한된 variant를 기존 Search로 순차 검색해 원문 항목 하나로 병합하며 원래 그룹 순서를 유지한다. |
| AC5 | 여러 Evidence, 0건과 Search error가 항목별로 렌더링된다. |
| AC6 | PDF는 표시 Evidence page, TXT는 기존 문서 상세로 이동한다. |
| AC7 | 결과 화면과 source에 금지된 판정·점수 표현이 없다. |
| AC8 | PostgreSQL integration에서 Search owner/ACTIVE isolation과 인증 회귀가 통과한다. |
| AC9 | PRZ-009 Tag, 문서 upload/detail과 기존 Search 회귀가 통과한다. |
| AC10 | PRZ-016 Production Search source diff가 0이고 migration·새 dependency가 없다. |
| AC11 | frontend unit·typecheck·lint·build와 backend unit·integration이 통과한다. |
| AC12 | 가능한 환경에서 로그인부터 Evidence 원문/page 이동까지 실제 브라우저 Gate를 통과하거나, 불가능하면 `NOT_VERIFIED` 원인을 코드 문제와 분리해 기록한다. |
| AC13 | segmentation 결과가 접근 가능한 modal로 열리고 section heading·checkbox 구분, 선택·전체 선택/해제·선택 수·0개 실행 방지·닫기와 focus 계약이 검증된다. |
| AC14 | 검색 시작 시 결과 전용 route로 이동해 loading을 표시하고, requirement rail 전환은 추가 Search 없이 활성 항목 하나의 result·empty·error를 표시한다. |
| AC15 | 같은 문서·버전의 metadata는 한 번만 표시하고, 화면상 정확 중복은 한 행으로 정리하되 같은 page의 서로 다른 Evidence와 다른 page의 Evidence를 보존한다. |
| AC16 | `항목 다시 선택`과 결과 route에서 입력 route로 돌아갈 때 현재 page session의 입력·선택을 보존하며, 결과 state 없는 직접 결과 route 진입은 입력 route로 안전하게 복귀한다. |
| AC17 | 넓은 화면은 requirement rail과 Evidence의 2열 workspace, 좁은 화면은 읽을 수 있는 단일 열로 표시하고 각 Evidence의 PDF page/TXT 이동을 유지한다. |
| AC18 | requirement rail은 `기록 있음`·`기록 없음`을 요구사항 개수와 함께 분리하고, loading/error는 필요할 때만 `확인 필요`로 표시한다. 탭 전환은 추가 Search 없이 원래 항목 순서·번호와 활성 Evidence 상태를 유지한다. |
