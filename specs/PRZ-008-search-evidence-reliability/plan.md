# PRZ-008 — 검색 근거 신뢰성 Plan

## 상태와 접근

`IN_PROGRESS` — Batch 2B의 opt-in 제품 구현과 계약 검증, 고정 TEST와 실제 OpenSQL
direct `5432` API·UI Gate를 마쳤다. S2C-03에서 검증된 개선 profile을 기본값으로
승격했고 legacy rollback 계약을 유지한다. S2C-04에서 전체 회귀 fixture를 직접 근거와
최대 5건·중복 축약 계약에 정렬하고 backend·frontend·OSS 회귀를 통과했다. PRZ-008의
이후 단계와 최종 통합은 별도 작업이다.

측정 계약, 근거 판정, UI, 청킹 실험과 색인 최적화를 순서대로 분리한다. 각
단계는 최신 `main`에서 시작하는 별도 branch·PR이며, 이전 Gate를 통과하지
못하면 다음 구현으로 넘어가지 않는다.

PRZ-001의 dense 평가 harness를 출발점으로 사용하되, 제품 순위나 threshold를
바꾸기 전에 누락된 거부·page·분리 지연 지표와 owner·version 사례부터 보완한다.

## 단계별 계획

### 0. 검색 개선 Spec 확정

| 구분 | 계획 |
|---|---|
| 입력 | clean `main`, 현재 source·test·migration, PRZ-001 평가 자료 |
| 산출물 | PRZ-008 Spec·Plan·Tasks와 Registry·상태·로드맵 최소 현행화 |
| 변경 가능 | 위 문서와 연결된 index·상태 표현 |
| 변경 금지 | 제품·test source, migration, dependency, API·UI·DB·runtime |
| Gate | 세 상태, 실패 사례, 지표, split 정책과 1~7단계 경계의 문서 검증 통과 |
| 중단 | 현재 동작을 source로 확정할 수 없거나 기존 Spec과 역할 충돌 발생 |

### 1. 검색 평가 기준선 교정

| 구분 | 계획 |
|---|---|
| 입력 | 승인된 0단계, 현재 dense 순위와 고정 dataset |
| 산출물 | 실패 fixture, 필수 지표·누출 검증, PostgreSQL 기준선과 TUNING 후보 profile Evidence |
| 변경 가능 | `src/searchEvaluation`, 관련 test·fixture와 평가 문서 |
| 변경 금지 | 제품 검색·API·UI·청킹·migration·dependency·threshold |
| Gate | split·evidence group 누출 0건, 지표 test와 현재 기준선 재현, TUNING 15문항의 top-1·오타·중복·거부 Gate 통과 |
| 중단 | 원문과 라벨을 연결할 수 없거나 측정에 제품 변경이 필요함 |

Threshold 후보와 수치 Gate는 TUNING으로만 정한다. TEST는 설정 고정 뒤 최종
비교에만 사용한다. OpenSQL은 실제 direct `5432` 환경에서 실행한 경우만 별도
결과로 기록한다.

score 단독 threshold가 분리되지 않았으므로, 같은 출처 위치·본문 overlap과 강한
식별자·수치로 연결된 반복 요약 근거를 먼저 축약한다. 각 후보에는 고유 식별자·수치·
핵심어·부정 표현을 별도 결정 신호로 사용하는 평가 profile을 TUNING에서만 검증한다.
이 profile의 제품 적용 계약은 2A에서 고정했고 2B에서 opt-in으로 구현했다. 고정 TEST는
기본적으로 계속 잠겨 있으며, 정확한 v2.3 Dataset·`TEST` split·명시 allow flag를 모두
제공한 최종 비교 run에서만 실행한다. 이 경로로 두 profile 비교를 완료했으며, TEST 결과는
기본 profile 변경이나 재튜닝 근거로 사용하지 않는다.

### 2A. 제품 적용 계약 확정

| 구분 | 계획 |
|---|---|
| 입력 | `source-dedup-evidence-signals-v1` TUNING Gate, 현재 API·frontend·설정·test 계약 |
| 산출물 | 호환 API, 세 상태 응답, versioned profile 설정, rollback과 TEST Gate |
| 변경 가능 | PRZ-008 Spec·Plan·Tasks |
| 변경 금지 | 제품·test source, Dataset·TEST, migration·dependency·DB·runtime |
| Gate | v1 호환, v2 응답, 기본 legacy profile, 검증·승격 조건의 모순 없음 |
| 중단 | 기존 client를 깨지 않고 세 상태를 표현할 방법이 없거나 TEST 전 수치 조정이 필요함 |

기존 `/api/search`와 Career Evidence 배열은 유지하고, 세 상태가 필요한 client에는
`/api/v2/career-evidence/search`를 추가한다. profile은 하나의 versioned 설정으로
선택하며, TEST와 OpenSQL Gate 전에는 legacy 기본값을 유지한다.

### 2B. 근거 없음 판정 제품 적용

| 구분 | 계획 |
|---|---|
| 입력 | 2A에서 고정한 API·설정·검증 계약과 1단계 TUNING profile |
| 산출물 | 세 상태 판정, v2 API·v1 adapter, profile 설정과 제품 test Evidence |
| 변경 가능 | 검색 service·repository·DTO·controller와 관련 test·문서 |
| 변경 금지 | 청킹·색인·PDF·migration·frontend design, owner·ACTIVE 경계 |
| Gate | 거부·오거부 Gate, owner·past-version test, 기존 장애 5xx 유지 |
| 중단 | 허용 오거부율을 지키는 판정 구간이 없거나 환경별 재현 실패 |

정상 질의 결과는 v2에서 `200`과 `state`·`results`로 표현한다. 기존 단일 검색의
404와 Career Evidence raw 배열은 호환 경로로 유지한다.

완료 표현의 질문 양태 교정은 명확한 완료 서술과 직접 질문·꼬리질문·인용·전언·
완료 여부·같은 문장 또는 바로 다음 문장의 부정·철회 사례를 한 단위 테스트 집합으로
먼저 고정하고 실패를 재현한 뒤, 완료 주장 판정기만 최소 수정한다. 애매한 문장은
fail-closed로 거절한다. 검증은 해당 단위 테스트와 동일 사례를 통과시키는 PostgreSQL
통합 테스트로 제한하며 TUNING·고정 TEST·전체 회귀는 실행하지 않는다.

재감사에서 확인한 양태 우회는 완료 신호를 핵심어 점수와 분리된 필수 Gate로 고정한다.
동일한 명확한 완료문을 질문·꼬리질문·전언·인용·부정·철회로 변환하고 핵심어 수를
달리해도 모두 거절되는지 단위 테스트로 검증한다. 문장 경계는 버전·소수점 내부의
마침표에서 끊지 않아 바로 다음 문장의 철회 판정을 보존한다.

후속 재감사에서 확인한 청크 전역 신호 합성은 claim unit 구조로 대체한다. 완료 질의는
하나의 unit 안에서 대상 anchor와 직접 완료 predicate를 함께 만족해야 하며, 줄바꿈·
CRLF·연속 종결부호·무공백 문장 경계는 unit 변환 불변식으로 고정한다. 인접 unit은
교정 양태와 앞 주장 참조 또는 같은 대상의 출시·배포 참조가 함께 있을 때만 연결한다.
일반 검색 profile 경로와 API·owner·Dataset 계약은 변경하지 않는다.

S2B-13 재감사 뒤에는 임의 한국어 문장 전체를 P1 0의 대상으로 삼지 않는다. 질의는
`SUPPORTED`·`UNSUPPORTED`·`NONE`으로 파싱하고, 비등록 출시·배포 활용형은 일반
검색으로 fallback하지 않는다. 직접 주장은 등록된 prefix·질의 대상구·선택 annotation·
완료 predicate·종결부호의 full match로만 승인한다. 대상구는 질의 token 순서와 완전히
일치시켜 같은 unit 안의 부정 대상과 다른 완료 대상을 합성하지 않는다. 지원 문법 밖의
오거절은 공개된 fail-closed 한계로 두고, 문법 밖 입력의 승인과 지원 문법 오판만 P1로
판정한다. 검증과 독립 감사도 이 유한 문법과 생성형 변환 집합만을 기준으로 한다.

### 2C. 직접 근거·정확 사실 문법 보정

| 구분 | 계획 |
|---|---|
| 입력 | 고정 TEST 비교에서 확인된 opt-in 오거부 유형과 기존 S2B-14 폐쇄 문법 |
| 변경 가능 | `CompositeSearchProfile`, 변환 기반 대상 단위·PostgreSQL test, PRZ-008 Spec·Plan·Tasks·Evidence |
| 변경 금지 | Dataset v2.2/v2.3·TEST 질문/라벨·제목 근거·API·세 상태·owner·`ACTIVE`·migration·dependency·설정 |
| 보존 | 완료 이력 질의의 동일 claim unit Gate, 질문·인용·전언·부정·철회 fail-closed, `Kafka랩` exact token 경계 |
| 지원 | 본문 프로젝트 이름·직접 참여 선언 뒤의 직접 완료 평서와 일반 직접 근거·정확 수치/날짜 질의를 제한적으로 연결 |
| TUNING Gate | 직접 근거 8/8, 오타 2/2, 중복 0, 무근거 거부 1.0, 근거 오거부 0 |
| 최종 Gate | TUNING 통과 후에만 고정 v2.3 TEST에서 legacy와 opt-in을 재비교; TEST 결과로 재튜닝하지 않음 |

구현은 변환 기반 RED로 시작한다. 계사 `이다` 경계와 직접 근거·정확 수치/날짜 문법의
양성 변환, `Kafka랩`, 제목 전용, 다른 이름 선언, 질문·인용·전언·부정·철회, 완료 이력의
절간 합성 음성 변환을 함께 추가한다. API나 평가 Dataset은 변경하지 않는다.

2026-08-08 S2C-02는 위 범위로 완료했다. 변환 RED를 재현한 뒤 대상 단위 33/33,
PostgreSQL 1/1, v2.3 TUNING 15문항과 고정 TEST의 legacy·opt-in 각 1/1을 통과했다.
opt-in TEST는 모든 품질 Gate를 충족했으며, TEST 결과 뒤 구현·설정·threshold를 다시
조정하지 않았다. 2026-08-11 실제 OpenSQL direct `5432` API·UI Gate도 통과했고,
사용자 승인으로 S2C-03 기본 profile 승격을 진행한다. rollback은 명시적
`PRIZM_SEARCH_PROFILE=legacy-dense-v1` override 또는 기본값 복원이다.

### 3. 검색 UI 신뢰성 개선

| 구분 | 계획 |
|---|---|
| 입력 | 2단계 API 계약과 client 이행 방식 확정 |
| 산출물 | 세 상태 안내, 오류 분리, 원문·page 표시와 frontend 검증 |
| 변경 가능 | Career Evidence UI·search API adapter와 관련 test·문서 |
| 변경 금지 | backend 순위·threshold, 청킹, DB·migration, 새 design system |
| Gate | 상태별 화면, TXT·PDF 근거, 접근성·lint·typecheck·build 통과 |
| 중단 | 호환 전략이 없거나 score를 확률로 오인시키는 표현이 남음 |

### 4A. 의미 단위 청킹 비교 실험

| 구분 | 계획 |
|---|---|
| 입력 | 고정 평가 profile, 기존 800/120 기준선, 합성 corpus |
| 산출물 | 품질·중복·chunk 수·색인 시간·page 정확도 비교 Evidence |
| 변경 가능 | 평가·실험 전용 chunker, fixture와 test |
| 변경 금지 | 제품 `TextChunker`, migration, 사용자 문서 재색인, API·UI |
| Gate | 사전 고정한 품질·중복·latency와 보존 계약 Gate 충족 |
| 중단 | 의미 있는 개선이 없거나 중복·비용·page 정확도가 악화됨 |

4A가 실패하면 4B를 실행하지 않는다. “기존 청킹 유지”도 유효한 결론이다.

### 4B. 검증된 청킹 제품 적용

| 구분 | 계획 |
|---|---|
| 입력 | 4A Gate, 알고리즘·rollback·재색인 정책 승인 |
| 산출물 | 제품 chunker·test와 source·activation 보존 Evidence |
| 변경 가능 | `TextChunker` 중심의 최소 ingestion source·test·설정 |
| 변경 금지 | Worker 병렬화·부분 저장·checkpoint, page-crossing chunk, 기존 migration |
| Gate | unit·integration, ownership·activation·TXT/PDF source와 TEST Gate 통과 |
| 중단 | 기존 active 보존 또는 page 출처를 보장하지 못함 |

기존 문서 재색인은 자동으로 수행하지 않으며 필요성·비용·rollback을 별도
승인받는다.

### 5. Ollama batch embedding

| 구분 | 계획 |
|---|---|
| 입력 | 고정 corpus·순차 호출 기준선, 실제 Ollama batch 계약 |
| 산출물 | batch 구현·test와 품질·실패 원자성·색인 시간 Evidence |
| 변경 가능 | embedding client·indexing 호출의 최소 범위와 test |
| 변경 금지 | model·dimension, Worker 병렬 구조, migration, search threshold |
| Gate | dimension·finite·norm과 activation 보존, 색인 시간 Gate 충족 |
| 중단 | 안전한 batch 계약을 확인하지 못하거나 측정상 개선이 없음 |

### 6. PDF 중복 분석 제거

| 구분 | 계획 |
|---|---|
| 입력 | upload 검증과 Worker 추출의 중복 비용 profile |
| 산출물 | 비용 측정, 채택 시 단일 분석 경로와 PDF regression Evidence |
| 변경 가능 | 측정으로 정당화된 최소 PDF upload·extraction source·test |
| 변경 금지 | OCR, layout 복원, page-crossing chunk, migration |
| Gate | text-layer·password·size 검증과 PAGE 번호 보존, performance Gate 충족 |
| 중단 | 비용 비중이 작거나 validation과 비동기 처리 경계를 결합시킴 |

비용이 의미 없으면 `REJECTED` 또는 `DEFERRED`로 끝내고 제품을 바꾸지 않는다.

### 7. 전체 회귀와 독립 감사

| 구분 | 계획 |
|---|---|
| 입력 | 채택된 단계 통합, target commit과 worktree 고정 |
| 산출물 | backend·frontend·DB·OSS·SBOM·문서 회귀와 최종 Evidence |
| 변경 가능 | 판정 확정 뒤 상태 문서 최소 현행화 |
| 변경 금지 | 새 기능·전면 refactor·재튜닝·TEST 기반 설정 변경 |
| Gate | 필수 회귀, 보존 계약, 고정 TEST와 환경별 결과 통과 |
| 중단 | blocking finding, TEST 실패, 환경 근거 누락 또는 상태 모순 |

감사 중 문제를 자동 수정하지 않는다. `AUDIT_FAIL`을 기록하고 수정용 별도
단계로 돌아간다.

## 위험과 대응

| 위험 | 대응 |
|---|---|
| score를 confidence로 오해 | 순위 신호로만 설명하고 threshold는 고정 dataset에서 결정한다. |
| TEST 누출 | 문서·evidence group 단위로 나누고 TEST 확인 후 조정을 금지한다. |
| 오거부 증가 | 거부율·오거부율과 Direct MRR·nDCG를 함께 Gate로 사용한다. |
| overlap 중복 | relevance label과 `evidenceGroup` 기반 nDCG·중복률을 함께 기록한다. |
| API 호환성 파손 | endpoint·version·adapter를 비교하고 client 이행을 함께 검증한다. |
| active version 손상 | 새 version과 atomic activation만 사용하고 재색인은 별도 승인한다. |
| 환경 결과 혼동 | PostgreSQL과 OpenSQL의 commit·DB·model·결과를 분리한다. |

## 검증 환경

- 0단계는 Markdown·local link·상태·diff만 검증한다. 제품 test는 `NOT_RUN`이다.
- 1단계는 PostgreSQL·pgvector와 실제 Ollama `bge-m3` 기준선을 사용한다.
- OpenSQL은 direct `5432`에서 실제 실행한 결과만 별도 판정한다.
- 2~6단계는 변경에 해당하는 test를 실행하고 전체 회귀는 7단계에서 수행한다.
- model 이름뿐 아니라 실제 identity와 1024차원 여부를 Evidence에 기록한다.

## Rollback·dependency·Git

- Gate 실패 시 해당 branch를 통합하지 않는 것이 기본 rollback이다.
- Flyway clean·repair나 적용 migration 수정, data 자동 삭제·재색인을 사용하지 않는다.
- 새 package·model·image가 필요하면 해당 단계의 Plan과 license·SBOM Gate를 먼저
  갱신한다. 현재 단계에는 dependency 변경이 없다.
- 0단계 branch는 `PRZ-008-search-evidence-reliability`다. 이후 각 단계는 최신
  `main`의 별도 branch·PR을 사용하며 미래 Issue·PR 번호를 예약하지 않는다.
- 4A와 4B는 반드시 분리하고, 6단계가 측정 Gate를 통과하지 않으면 구현 PR을
  만들지 않는다.

## 다음 단계 전 열린 결정

- 4B 이후: 기존 문서 재색인의 필요성·비용·rollback

2A에서 API 이행, profile 설정과 TEST Gate를 고정했다. 제품 구현 중 이 계약을
바꿔야 한다면 TEST 실행 전에 별도 Spec 검토로 돌아가며, TEST 결과로 수치를
재조정하지 않는다.
