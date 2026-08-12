# PRZ-008 작업 목록

> 현재 상태: Batch 2B `PRODUCT_PROFILE_DEFAULT_PROMOTED` — 새 profile, v1 호환
> adapter와 v2 세 상태 응답을 구현·검증했다. S2C-02 TUNING과 고정 v2.3 TEST의
> legacy·opt-in 비교, 실제 OpenSQL direct `5432` API·UI Gate를 통과했고,
> S2C-03 기본 profile 승격 뒤 S2C-04 전체 backend·frontend·OSS 회귀까지 통과했다.

| ID | 작업 | 최종 상태 | 결과 문서 |
|---|---|---|---|
| `S0-01` | 현재 제품 검색·청킹·평가 기준선 확인 | `DONE` | [Spec](spec.md) |
| `S0-02` | 검색 실패 사례와 세 상태 계약 정의 | `DONE` | [Spec](spec.md) |
| `S0-03` | TUNING·TEST 정책과 필수 지표 정의 | `DONE` | [Spec](spec.md) |
| `S0-04` | 1~7단계 범위·Gate·중단 조건 분리 | `DONE` | [Plan](plan.md) |
| `S0-05` | 0단계 문서·링크·변경 범위 자체 검증 | `DONE` | [Spec](spec.md), [Plan](plan.md) |
| `S0-06` | Spec 검토와 다음 단계 착수 승인 | `DONE` | [Spec](spec.md), [Plan](plan.md) |
| `S1A-01` | 기존 Dataset·과거 기준선을 보존하고 합성 Dataset v2 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-02` | TUNING·TEST 문서·근거·질문 그룹 누출 validation 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-03` | owner·version·PDF gold page 라벨 불변식 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-04` | Dataset v2 loader와 의도적 실패 fixture 검증 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-01` | Precision@5·Direct MRR@5·@20·group 중복 nDCG 계산 계약 교정 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-02` | 거부·오거부·no-searchable-documents·top-1·PDF page 지표 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-03` | 사용자 반환 수·후보 수와 total·embedding·DB 지연 분리 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-04` | 현재 제품·평가용 threshold profile을 분리한 보고서 계약과 경계 테스트 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-01` | Dataset v2 TUNING-only 선택과 owner·version fixture 실행 경계 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-02` | PostgreSQL·pgvector·Ollama 실제 TUNING 10문항 기준선 측정 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-03` | 후보 수·지연과 threshold별 거부·오거부·품질 분석 | `BLOCKED` | `THRESHOLD_NOT_SEPARABLE`; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-04` | 실제 실패 구조를 합성한 이력서·포트폴리오와 TUNING 5문항 추가 | `DONE` | 정답·오타·동일 페이지 중복·무근거; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-05` | Dataset v2.1 TUNING 15문항 실제 재측정 | `DONE` | top-1 직접 0.8750·중복 0.0933·무관 거부 0.0000; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-06` | overlap 경계에서 잘린 직접 근거 라벨과 Dataset v2.2 교정 | `DONE` | TEST 10문항 byte 고정 유지 |
| `S1C-07` | 출처·본문·반복 요약 축약과 식별자·수치·핵심어·부정 표현 TUNING profile | `DONE` | 제품 source와 score 단독 threshold 변경 없음 |
| `S1C-08` | TUNING 15문항 top-1·오타·중복·거부 Gate 재측정 | `DONE` | top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0 |
| `S2A-01` | 기존 `/api/search`와 Career Evidence 배열의 호환 경계 확정 | `DONE` | [Spec](spec.md) |
| `S2A-02` | v2 `state`·`results`와 `NO_EVIDENCE`·`NO_SEARCHABLE_DOCUMENTS` 응답 확정 | `DONE` | [Spec](spec.md) |
| `S2A-03` | `PRIZM_SEARCH_PROFILE` 선택·기본값·실패·rollback 계약 확정 | `DONE` | [Spec](spec.md) |
| `S2A-04` | unit·API·frontend·PostgreSQL·OpenSQL·최종 TEST Gate 확정 | `DONE` | [Spec](spec.md), [Plan](plan.md) |
| `S2B-01` | `source-dedup-evidence-signals-v1`을 opt-in 제품 profile로 구현 | `DONE` | 기본값 `legacy-dense-v1` 유지; [Evidence](evidence.md) |
| `S2B-02` | 기존 Career Evidence 배열 adapter와 v2 세 상태 API 구현 | `DONE` | `EVIDENCE_FOUND`·`NO_EVIDENCE`·`NO_SEARCHABLE_DOCUMENTS`; [Evidence](evidence.md) |
| `S2B-03` | profile 설정·service·controller·인증·owner·ACTIVE 경계 검증 | `DONE` | unit·PostgreSQL 통합 회귀 통과; [Evidence](evidence.md) |
| `S2B-04` | 숫자·식별자 exact token, 영문 hard-negative와 누락된 PostgreSQL·API 계약 교정 | `DONE` | 세 상태·후보 20·PDF/TXT 축약·`401`·DB `5xx`; [Evidence](evidence.md) |
| `S2B-05` | 현재 제품 source로 Unicode exact-token TUNING 재실행 | `DONE` | 1차 Gate `FAIL`: top-1 7/8·오거부 0.125; [Evidence](evidence.md) |
| `S2B-06` | 단일 고유명사·완료 행위 경계 교정과 TUNING 재측정 | `DONE` | top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0; [평가 문서](../../docs/evaluation/search-evaluation.md), [Evidence](evidence.md) |
| `S2B-07` | 제목은 순위 보조로 제한하고 근거 판정은 본문으로 분리 | `DONE` | 제목만 정답인 후보 거절 단위·PostgreSQL 회귀 |
| `S2B-08` | PDF gold page를 독립 근거로 만든 Dataset v2.3 추가와 TUNING 재측정 | `DONE` | v2.2 보존·질문 byte 동일·top-1 8/8·PDF 1.0; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S2B-09` | 질문형 완료 표현과 Unicode 복합어 내부 ASCII 부분 일치 차단 | `DONE` | `배포했습니다?`·`출시했습니다?`와 `Kafka랩` 거절 단위·PostgreSQL 회귀; TUNING 15문항 재통과 |
| `S2B-10` | 완료 표현의 질문·전언·꼬리질문·철회 양태 판정 교정 | `DONE` | 대상 단위 27/27·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN` |
| `S2B-11` | 문장 전체의 질문·인용·전언·부정·철회 양태를 fail-closed로 교정 | `DONE` | 대상 단위 27/27·PostgreSQL 1/1·TUNING 15 `PASS`; 고정 TEST·전체 회귀 `NOT_RUN` |
| `S2B-12` | 완료 양태를 필수 Gate로 분리하고 변환 기반 회귀로 고정 | `DONE` | 질문 우회·연속부호 예외 RED → 단위 28/28·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN` |
| `S2B-13` | 동일 claim unit에 대상·완료·직접 긍정 양태를 고정 | `DONE` | 문장·절간 신호 합성·오거절 RED → 단위 29/29·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN` |
| `S2B-14` | 임의 한국어 의미 계약을 폐기하고 완료 질의·주장의 폐쇄 문법과 P1 경계를 고정 | `DONE` | 변환 RED → 단위 31/31·PostgreSQL 1/1; 최종 독립 재감사 P0 0·P1 0 |
| `S2C-01` | v2.3 고정 TEST allow gate와 legacy·opt-in 최종 비교 | `DONE` | selector 4/4, legacy·opt-in 각 runner 1/1 완료; opt-in 직접 근거 오거부 0.8333으로 기본값 승격 보류 |
| `S2C-02` | 직접 근거·정확 수치/날짜 지원 문법을 TUNING으로 보정하고 TEST 재비교 | `DONE` | 변환 RED 후 단위 33/33·PostgreSQL 1/1·TUNING 15 PASS·고정 TEST legacy/opt-in 각 1/1 및 모든 품질 Gate PASS; OpenSQL direct `5432` API·UI Gate PASS |
| `S2C-03` | 검증된 개선 profile을 기본값으로 승격 | `DONE` | 기본값 source·명시적 legacy rollback·대상 단위 50/50 PASS; OpenSQL direct `5432` API·UI Gate PASS |
| `S2C-04` | 기본 profile 전체 회귀 fixture를 직접 근거·중복 축약 계약에 정렬 | `DONE` | 대상 통합 2/2, backend unit 335 pass·15 skip, backend integration 75 pass·3 skip, frontend·OSS Gate PASS |
| `P18` | P12/P17의 제한적 GENERAL exact-token rescue를 기본 profile에 적용 | `DONE` | P8 40 + P17 28에서 평가 프로필과 결과·상태·score/distance 차이 0; backend unit PASS, P18 관련 PostgreSQL integration 6/6 PASS |
| `P18.1` | P4 final ranking과 충돌하는 integration raw-distance assertion 교정 | `DONE` | raw 후보 distance 순서와 profile 최종 순서를 분리 검증; backend integration 75 pass·3 skip·0 fail |

Dataset v2와 v2.1에서 근거·무근거 top-1 score 분포가 겹쳐 score 단독 threshold는
`THRESHOLD_NOT_SEPARABLE`로 유지한다. v2.2의 평가 전용 profile은 TUNING 15문항에서
직접 근거 top-1 8/8, 오타 top-1 2/2, 중복 0, 무관 질문 거부율 1.0과 오거부율 0으로
사전 Gate를 통과했다. Batch 2B에서 같은 profile을 opt-in 제품 코드로 옮기고 기존 API
호환과 세 상태를 검증했다. 제품 source 첫 재측정의 오거부를 exact Unicode 토큰과
제한된 완료 행위 정규화로 교정한 뒤 같은 Gate를 다시 통과했다. 이후 제목은 순위
보조로만 사용하고 본문만 근거 판정에 쓰도록 분리했으며, PDF gold page가 독립 근거가
되는 Dataset v2.3에서도 같은 Gate를 통과했다. 고정 TEST 비교의 초기 runner guard와
fixture scenario 실패는 v2.3·`TEST`·명시 flag 예외 및 질문 참조 fixture 전용 seed로
교정했다. legacy·opt-in run은 모두 완료했지만 opt-in은 직접 근거 오거부율 0.8333으로
legacy(0)보다 악화됐다. 이 S2C-01 결과는 S2C-02의 TUNING 문법 보정 전 측정이다.
S2C-02는 변환 기반 RED를 먼저 재현한 뒤 제한된 직접 근거·정확 사실 문법만 보정했다.
최종 TUNING 15문항과 고정 TEST legacy·opt-in 각 1/1은 모든 품질 Gate를 통과했다.
TEST 결과 뒤에는 재튜닝하지 않았다. 이후 실제 OpenSQL direct 5432 API·UI Gate를
통과했고, S2C-03에서 새 profile을 기본값으로 승격했다.
