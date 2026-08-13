# PRZ-008 — 검색 근거 신뢰성 Tasks

> **현재 상태:** `IN_PROGRESS` — 새 profile, v1 호환
> adapter와 v2 세 상태 응답을 구현·검증했다. S2C-02 TUNING과 고정 v2.3 TEST의
> legacy·opt-in 비교, 실제 OpenSQL direct `5432` API·UI Gate를 통과했고,
> S2C-03 기본 profile 승격 뒤 S2C-04 전체 backend·frontend·OSS 회귀까지 통과했다.

## P1. 검색 개선 Spec 확정

- [x] **ID:** `S0-01`
  - 작업: 현재 제품 검색·청킹·평가 기준선 확인
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S0-02`
  - 작업: 검색 실패 사례와 세 상태 계약 정의
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S0-03`
  - 작업: TUNING·TEST 정책과 필수 지표 정의
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S0-04`
  - 작업: 1–7단계 범위·Gate·중단 조건 분리
  - 최종 상태: `DONE`
  - 결과 문서: [Plan](plan.md)
- [x] **ID:** `S0-05`
  - 작업: 0단계 문서·링크·변경 범위 자체 검증
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md), [Plan](plan.md)
- [x] **ID:** `S0-06`
  - 작업: Spec 검토와 다음 단계 착수 승인
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md), [Plan](plan.md)
## P2. 검색 평가 기준선 교정

- [x] **ID:** `S1A-01`
  - 작업: 기존 Dataset·과거 기준선을 보존하고 합성 Dataset v2 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1A-02`
  - 작업: TUNING·TEST 문서·근거·질문 그룹 누출 validation 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1A-03`
  - 작업: owner·version·PDF gold page 라벨 불변식 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1A-04`
  - 작업: Dataset v2 loader와 의도적 실패 fixture 검증
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1B-01`
  - 작업: Precision@5·Direct MRR@5·@20·group 중복 nDCG 계산 계약 교정
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1B-02`
  - 작업: 거부·오거부·no-searchable-documents·top-1·PDF page 지표 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1B-03`
  - 작업: 사용자 반환 수·후보 수와 total·embedding·DB 지연 분리
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1B-04`
  - 작업: 현재 제품·평가용 threshold profile을 분리한 보고서 계약과 경계 테스트 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-01`
  - 작업: Dataset v2 TUNING-only 선택과 owner·version fixture 실행 경계 추가
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-02`
  - 작업: PostgreSQL·pgvector·Ollama 실제 TUNING 10문항 기준선 측정
  - 최종 상태: `DONE`
  - 결과 문서: [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-03`
  - 작업: 후보 수·지연과 threshold별 거부·오거부·품질 분석
  - 최종 상태: `BLOCKED`
  - 결과 문서: `THRESHOLD_NOT_SEPARABLE`; [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-04`
  - 작업: 실제 실패 구조를 합성한 이력서·포트폴리오와 TUNING 5문항 추가
  - 최종 상태: `DONE`
  - 결과 문서: 정답·오타·동일 페이지 중복·무근거; [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-05`
  - 작업: Dataset v2.1 TUNING 15문항 실제 재측정
  - 최종 상태: `DONE`
  - 결과 문서: top-1 직접 0.8750·중복 0.0933·무관 거부 0.0000; [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S1C-06`
  - 작업: overlap 경계에서 잘린 직접 근거 라벨과 Dataset v2.2 교정
  - 최종 상태: `DONE`
  - 결과 문서: TEST 10문항 byte 고정 유지
- [x] **ID:** `S1C-07`
  - 작업: 출처·본문·반복 요약 축약과 식별자·수치·핵심어·부정 표현 TUNING profile
  - 최종 상태: `DONE`
  - 결과 문서: 제품 source와 score 단독 threshold 변경 없음
- [x] **ID:** `S1C-08`
  - 작업: TUNING 15문항 top-1·오타·중복·거부 Gate 재측정
  - 최종 상태: `DONE`
  - 결과 문서: top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0
## P3. 제품 적용 계약 확정

- [x] **ID:** `S2A-01`
  - 작업: 기존 `/api/search`와 Career Evidence 배열의 호환 경계 확정
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S2A-02`
  - 작업: v2 `state`·`results`와 `NO_EVIDENCE`·`NO_SEARCHABLE_DOCUMENTS` 응답 확정
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S2A-03`
  - 작업: `PRIZM_SEARCH_PROFILE` 선택·기본값·실패·rollback 계약 확정
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md)
- [x] **ID:** `S2A-04`
  - 작업: unit·API·frontend·PostgreSQL·OpenSQL·최종 TEST Gate 확정
  - 최종 상태: `DONE`
  - 결과 문서: [Spec](spec.md), [Plan](plan.md)
## P4. 근거 없음 판정 제품 적용

- [x] **ID:** `S2B-01`
  - 작업: `source-dedup-evidence-signals-v1`을 opt-in 제품 profile로 구현
  - 최종 상태: `DONE`
  - 결과 문서: 기본값 `legacy-dense-v1` 유지; [Evidence](evidence.md)
- [x] **ID:** `S2B-02`
  - 작업: 기존 Career Evidence 배열 adapter와 v2 세 상태 API 구현
  - 최종 상태: `DONE`
  - 결과 문서: `EVIDENCE_FOUND`·`NO_EVIDENCE`·`NO_SEARCHABLE_DOCUMENTS`; [Evidence](evidence.md)
- [x] **ID:** `S2B-03`
  - 작업: profile 설정·service·controller·인증·owner·ACTIVE 경계 검증
  - 최종 상태: `DONE`
  - 결과 문서: unit·PostgreSQL 통합 회귀 통과; [Evidence](evidence.md)
- [x] **ID:** `S2B-04`
  - 작업: 숫자·식별자 exact token, 영문 hard-negative와 누락된 PostgreSQL·API 계약 교정
  - 최종 상태: `DONE`
  - 결과 문서: 세 상태·후보 20·PDF/TXT 축약·`401`·DB `5xx`; [Evidence](evidence.md)
- [x] **ID:** `S2B-05`
  - 작업: 현재 제품 source로 Unicode exact-token TUNING 재실행
  - 최종 상태: `DONE`
  - 결과 문서: 1차 Gate `FAIL`: top-1 7/8·오거부 0.125; [Evidence](evidence.md)
- [x] **ID:** `S2B-06`
  - 작업: 단일 고유명사·완료 행위 경계 교정과 TUNING 재측정
  - 최종 상태: `DONE`
  - 결과 문서: top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0; [평가 문서](../../docs/evaluation/search-evaluation.md), [Evidence](evidence.md)
- [x] **ID:** `S2B-07`
  - 작업: 제목은 순위 보조로 제한하고 근거 판정은 본문으로 분리
  - 최종 상태: `DONE`
  - 결과 문서: 제목만 정답인 후보 거절 단위·PostgreSQL 회귀
- [x] **ID:** `S2B-08`
  - 작업: PDF gold page를 독립 근거로 만든 Dataset v2.3 추가와 TUNING 재측정
  - 최종 상태: `DONE`
  - 결과 문서: v2.2 보존·질문 byte 동일·top-1 8/8·PDF 1.0; [평가 문서](../../docs/evaluation/search-evaluation.md)
- [x] **ID:** `S2B-09`
  - 작업: 질문형 완료 표현과 Unicode 복합어 내부 ASCII 부분 일치 차단
  - 최종 상태: `DONE`
  - 결과 문서: `배포했습니다?`·`출시했습니다?`와 `Kafka랩` 거절 단위·PostgreSQL 회귀; TUNING 15문항 재통과
- [x] **ID:** `S2B-10`
  - 작업: 완료 표현의 질문·전언·꼬리질문·철회 양태 판정 교정
  - 최종 상태: `DONE`
  - 결과 문서: 대상 단위 27/27·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN`
- [x] **ID:** `S2B-11`
  - 작업: 문장 전체의 질문·인용·전언·부정·철회 양태를 fail-closed로 교정
  - 최종 상태: `DONE`
  - 결과 문서: 대상 단위 27/27·PostgreSQL 1/1·TUNING 15 `PASS`; 고정 TEST·전체 회귀 `NOT_RUN`
- [x] **ID:** `S2B-12`
  - 작업: 완료 양태를 필수 Gate로 분리하고 변환 기반 회귀로 고정
  - 최종 상태: `DONE`
  - 결과 문서: 질문 우회·연속부호 예외 RED → 단위 28/28·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN`
- [x] **ID:** `S2B-13`
  - 작업: 동일 claim unit에 대상·완료·직접 긍정 양태를 고정
  - 최종 상태: `DONE`
  - 결과 문서: 문장·절간 신호 합성·오거절 RED → 단위 29/29·PostgreSQL 1/1; TUNING·고정 TEST·전체 회귀 `NOT_RUN`
- [x] **ID:** `S2B-14`
  - 작업: 임의 한국어 의미 계약을 폐기하고 완료 질의·주장의 폐쇄 문법과 P1 경계를 고정
  - 최종 상태: `DONE`
  - 결과 문서: 변환 RED → 단위 31/31·PostgreSQL 1/1; 최종 독립 재감사 P0 0·P1 0

## P5. 직접 근거·정확 사실 문법 보정

- [x] **ID:** `S2C-01`
  - 작업: v2.3 고정 TEST allow gate와 legacy·opt-in 최종 비교
  - 최종 상태: `DONE`
  - 결과 문서: selector 4/4, legacy·opt-in 각 runner 1/1 완료; opt-in 직접 근거 오거부 0.8333으로 기본값 승격 보류
- [x] **ID:** `S2C-02`
  - 작업: 직접 근거·정확 수치/날짜 지원 문법을 TUNING으로 보정하고 TEST 재비교
  - 최종 상태: `DONE`
  - 결과 문서: 변환 RED 후 단위 33/33·PostgreSQL 1/1·TUNING 15 PASS·고정 TEST legacy/opt-in 각 1/1 및 모든 품질 Gate PASS; OpenSQL direct `5432` API·UI Gate PASS
- [x] **ID:** `S2C-03`
  - 작업: 검증된 개선 profile을 기본값으로 승격
  - 최종 상태: `DONE`
  - 결과 문서: 기본값 source·명시적 legacy rollback·대상 단위 50/50 PASS; OpenSQL direct `5432` API·UI Gate PASS
- [x] **ID:** `S2C-04`
  - 작업: 기본 profile 전체 회귀 fixture를 직접 근거·중복 축약 계약에 정렬
  - 최종 상태: `DONE`
  - 결과 문서: 대상 통합 2/2, backend unit 335 pass·15 skip, backend integration 75 pass·3 skip, frontend·OSS Gate PASS
- [x] **ID:** `P18`
  - 작업: P12/P17의 제한적 GENERAL exact-token rescue를 기본 profile에 적용
  - 최종 상태: `DONE`
  - 결과 문서: P8 40 + P17 28에서 평가 프로필과 결과·상태·score/distance 차이 0; backend unit PASS, P18 관련 PostgreSQL integration 6/6 PASS
- [x] **ID:** `P18.1`
  - 작업: P4 final ranking과 충돌하는 integration raw-distance assertion 교정
  - 최종 상태: `DONE`
  - 결과 문서: raw 후보 distance 순서와 profile 최종 순서를 분리 검증; backend integration 75 pass·3 skip·0 fail

## P6. 검색 UI 신뢰성 개선

- [x] v2 상태별 안내, snippet 기본 표시와 원문 펼치기를 구현했다.
- [x] 인증·server 오류와 score 비확률 표현을 분리했다.

## P7. 의미 단위 청킹 비교 실험

- [x] 평가 전용 section·paragraph 청킹을 비교했다.
- [x] 품질 하락 결과를 근거로 제품 적용을 보류했다.

## P8. 검증된 청킹 제품 적용

- [ ] 비교 Gate를 통과한 청킹 방식이 없어 제품 `TextChunker`를 변경하지 않았다.

## P9. Ollama batch embedding

- [ ] batch embedding은 아직 구현·검증하지 않았다.

## P10. PDF 중복 분석 제거

- [x] 평가 전용 page dedup 방식을 비교했다.
- [ ] 제품 적용 Gate를 통과한 방식은 아직 없다.

## P11. 전체 회귀와 독립 감사

- [x] 현재 적용 범위의 backend·frontend·OSS 회귀를 통과했다.
- [ ] 미완료 청킹·batch embedding·PDF 최적화 Gate가 남아 있다.

## 후속 또는 제외 범위

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
