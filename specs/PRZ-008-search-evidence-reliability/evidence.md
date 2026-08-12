# PRZ-008 검색 근거 신뢰성 — Evidence

## 현재 판정

`IN_PROGRESS` — S2C-03 기본 profile 승격과 S2C-04 전체 회귀 fixture 정렬은 완료했으며, PRZ-008의 이후 단계와 최종 통합은 별도 작업이다.

- 실행일: `2026-08-07`~`2026-08-08`
- 검증 기준: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f` 위 uncommitted worktree
- Batch 2B opt-in 제품 구현과 PostgreSQL Gate: `PASS`
- 직전 S2B-11 source·Dataset v2.3 TUNING 15문항 Gate: `HISTORICAL_PASS_NOT_RERUN`
- 기본 profile: `source-dedup-evidence-signals-v1`; 명시적 `legacy-dense-v1` override는 rollback으로 유지
- S2C-02 고정 Dataset v2.3 TEST 비교: `PASS` — legacy·opt-in runner 각각 1/1, 모든 품질 Gate 통과
- 실제 OpenSQL direct `5432` 제품 Gate: `PASS` — API 세 상태·owner/ACTIVE/TXT/PDF와 UI 회원가입·로그인·업로드·근거 표시
- 최종 독립 재감사: `PASS` — API 경계·양태·Dataset 보존 P0 0 / P1 0
- commit·PR·GitHub 통합: 수행하지 않음
- S2C-03 대상 설정·service·claim-unit test: `PASS` — 50/50
- S2C-04 전체 backend·frontend·OSS 회귀: `PASS` — backend unit 350건·backend integration 78건 모두 실패 0
- P18 제한적 exact-token rescue: `PASS` — Product와 P12 평가 프로필의 P8 40 + P17 28 결과·상태·원래 score/distance 차이 0

Batch 2B는 기존 API를 유지하면서 `source-dedup-evidence-signals-v1`을 opt-in으로
추가했다. v2 API는 `EVIDENCE_FOUND`, `NO_EVIDENCE`,
`NO_SEARCHABLE_DOCUMENTS`를 구분한다. 새 profile은 숫자와 영문 식별자를 정확한
토큰으로 검사하며 같은 PDF page와 인접 TXT overlap을 축약한다. Unicode 핵심어도
부분 문자열이 아닌 정규화된 전체 토큰으로 비교하고, 단일 고유명사 근거와 제한된
  출시·배포 완료 활용형을 별도 회귀로 고정했다. 근거 판정에는 사용자에게 인용되는
  본문만 사용하고 문서 제목은 순위 보조로 제한한다.

## 실행 결과

| 범위 | 명령 | 결과 |
|---|---|---|
| exact-token 대상 회귀 | `.\gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --tests "com.prizm.search.service.SearchServiceTest" --no-daemon --rerun-tasks` | `PASS` — Unicode 경계·단일 고유명사·완료 행위 양성/음성 포함 |
| backend unit 전체 | `.\gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 350개 중 335 pass, Windows 파일시스템 제약·OpenSQL 승인 Gate 15 skip |
| backend integration 전체 | `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — 78개 중 75 pass, Windows 파일시스템 제약 2개·OpenSQL opt-in 1개 skip |
| 제품 source TUNING 15 | `.\gradlew.bat searchEvaluation --no-daemon --rerun-tasks ...`와 `TUNING`·opt-in profile 고정 | `PASS` — top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0 |
| 고정 Dataset TEST | legacy·opt-in 각각 최종 실행 | `PASS` — 모든 품질 Gate 충족 |
| 실제 OpenSQL 제품 Gate | direct 5432 API·UI 실행 | `PASS` — 세 상태·owner/ACTIVE/TXT/PDF·UI 근거 표시 |
| P18 P8 40 + P17 28 | Product와 `short-general-exact-token-rescue-v1` 각각 `searchEvaluation` | `PASS` — 68문항 결과 ID·상태·relevance·원래 score/distance 차이 0; P17 최종 기준과도 차이 0 |
| P18 backend unit 전체 | `.\gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 420개 중 405 pass, 기존 환경 조건 15 skip |
| P18.1 backend integration 전체 | `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — stale raw-distance assertion을 repository raw order와 P4 profile final order 검증으로 분리한 뒤 78개 중 75 pass, 3 skip, 0 fail |
| P18 관련 PostgreSQL integration | GENERAL·completed Claim Gate·owner 격리 대상 6건 | `PASS` — 6/6 |

PostgreSQL 검증은 격리된 Testcontainers PostgreSQL·pgvector와 기존 Ollama를
사용했다. 실제 사용자 DB와 OpenSQL은 사용하거나 변경하지 않았다.

## 계약 근거

- 기본값과 opt-in 설정, 알 수 없는 profile의 시작 실패를 확인했다.
- `/api/search`는 변경하지 않았고 기존 Career Evidence API는 배열 형식을 유지한다.
- v2의 세 상태, 잘못된 요청 `400`, 무인증 `401`, 관리자 `403`, 임베딩 오류와 DB
  오류 `5xx`를 확인했다. 내부 판정 사유는 응답에 노출하지 않는다.
- PostgreSQL에서 owner·`ACTIVE`·과거 version 제외, `NO_EVIDENCE`,
  `NO_SEARCHABLE_DOCUMENTS`, 후보 20개 제한, PDF page와 TXT overlap 축약을
  확인했다.
- `8`과 `18`, `Java`와 `JavaScript`, 영문 absent-technology를 구분하는 회귀
  테스트를 추가했다.
- `자바`와 `자바스크립트`, `루미나`와 `루미나랩`을 구분하고 단일 `루미나`·`Kafka`
  정답을 허용한다. 문장 끝 구두점은 토큰에서 제거하되 `Node.js`·`C++`·`C#`은
  보존한다.
- `출시한`과 `배포했다` 같은 완료 활용형은 연결하지만 계획·자동화·부정문과 더 긴
  파생어는 완료 근거로 허용하지 않는다.
- 문서 제목만 정답이고 본문이 무관한 후보는 `NO_EVIDENCE`가 되며, 이 경계를 단위·
  PostgreSQL 회귀로 확인했다.

## Batch 2B TUNING 검증 이력

Run A와 Run B는 Dataset `prizm-search-evidence-synthetic-v2.2`, 결합 SHA-256
`f946fed8c145112c1082d7c08c25b357bd459b4c7cc53deb7b7e23731a2b7c2c`,
Docker Desktop 29.6.2, PostgreSQL 16.14·pgvector 0.8.2, Ollama 0.32.6,
`bge-m3:latest` digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` 조건에서
TUNING 15문항만 실행했다. 기준 commit은 `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`이며
Run A~C는 모두 그 위의 미커밋 worktree다.

| 실행 | source 상태 | top-1 | 오타 | 중복 | 무관 거부 | 오거부 | 판정·raw |
|---|---|---:|---:|---:|---:|---:|---|
| Run A (`2026-08-07T08:09:50Z`) | exact-token 1차 교정, 완료 행위 정규화 전 | 7/8 | 2/2 | 0 | 1.0 | 0.125 | `FAIL`; `local/search-evaluation/prz008-tuning-v22-unicode-exact-20260807/` |
| Run B (`2026-08-07T08:21:19Z`) | profile SHA-256 `100fb6b2f751b9b39334dfe4d654b7a0487fa1089634812036fb750ea4ef06c5` | 8/8 | 2/2 | 0 | 1.0 | 0 | `PASS`; `local/search-evaluation/prz008-tuning-v22-unicode-exact-final-20260807/` |
| Run C (`2026-08-07T09:37:12Z`) | v2.3·본문 근거 전용, profile SHA-256 `e10d2d923a046855a0df1a8a81ca60b23a28c5cf20b70140ba75153a25892a83` | 8/8 | 2/2 | 0 | 1.0 | 0 | `PASS`; `local/search-evaluation/prz008-tuning-v23-exact-document-title-final-20260807/` |
| Run D (`2026-08-07T10:02:10Z`) | 질문형 완료 표현·Unicode 복합어 경계 교정, profile SHA-256 `e72a63fe5eec96640828d402b41fb642c9fbd862049fa65b8d020a32576c79c3` | 8/8 | 2/2 | 0 | 1.0 | 0 | `PASS`; `local/search-evaluation/prz008-tuning-v23-p1-fix-20260807/` |
| Run E (`2026-08-07T10:51:07Z`) | S2B-11 문장 양태 fail-closed, profile SHA-256 `01eb199af0ff74c427b5178cce5c312d51b85a0ec1745f87ea89804b981194c6` | 8/8 | 2/2 | 0 | 1.0 | 0 | `PASS`; `local/search-evaluation/prz008-tuning-v23-s2b11-20260807/` |

Run A는 직접 근거가 dense 후보 1위였지만 `출시한`과 `배포했다`의 완료 행위 경계를
연결하지 못해 `v2-t-paraphrase-lumen`을 `NO_EVIDENCE`로 오거절했다. Run B는 완료
활용형만 제한적으로 정규화해 해당 정답을 반환했고, 역할 변경·부정·없는 기술을 포함한
무근거 7문항은 계속 모두 거절했다. 각 JSON·CSV는 `/local/` ignore 규칙 아래 보존되며
TEST split 지표나 행은 포함하지 않는다.

Run C는 기존 v2.2를 변경하지 않고 Dataset
`prizm-search-evidence-synthetic-v2.3`(결합 SHA-256
`f1bf3cffd1cc51d7c5f972e55fe99a8afe9dce45e403ef742a7e3d0b25bb7f9f`)을 사용했다.
v2.3의 질문 파일은 v2.2와 byte 단위로 같고, Atlas PDF gold page 본문 한 곳에 정확한
합성 문서명을 추가해 제목 없이도 독립 근거가 되게 했다. PDF page 인용 정확도는
`1.0`이고 `v2-t-pdf-date`는 gold page 2의 본문으로 relevance 2를 1위에 반환했다.

Run D는 `배포했습니다?`·`출시했습니다?` 같은 질문형 문장을 완료 사실로 보지 않고,
`Kafka랩` 같은 Unicode 복합어 안의 ASCII 부분 문자열을 식별자 일치로 추출하지 않도록
교정한 뒤 같은 Dataset v2.3 TUNING 15문항만 재실행했다. 직접 근거 8문항과 오타
2문항은 모두 1위였고 무근거 7문항은 모두 거절됐다. Direct MRR@5·@20은 `1.0`,
nDCG@5는 `0.9783`, PDF page 정확도는 `1.0`이었다. 고정 TEST와 OpenSQL Gate는
실행하지 않았다.

## S2B-10 질문 양태 교정

기준 commit `83631f13c21eab54ac0f32ebb0f893b6c5acea0f` 위 미커밋 worktree에서
질문·긍정 전언·전언된 질문·꼬리질문·철회 합성 사례를 한 단위 테스트에 먼저 고정했다.
구현 전 같은 대상 명령은 27개 중 새 테스트 1개가 실패해 전언된 질문을 완료 주장으로
오인하는 문제를 재현했다. 완료형 뒤의 국소 문맥 판정만 교정한 뒤 다음 결과를 얻었다.

| 범위 | 명령 | 결과 |
|---|---|---|
| 대상 단위 | `.\gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `PASS` — 27/27, skip 0 |
| 대상 PostgreSQL·pgvector | `.\gradlew.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1, skip 0 |
| TUNING·고정 TEST·전체 회귀 | 실행하지 않음 | `NOT_RUN` |

직접·전언된 질문, 꼬리질문과 명시적 철회는 완료 근거에서 제외하고 긍정 전언은
완료 근거로 유지했다. 실제 사용자 DB와 OpenSQL은 사용하거나 변경하지 않았다.

위 S2B-10의 긍정 전언 판정은 당시 실행 결과이며, 아래 S2B-11의 더 엄격한
fail-closed 계약으로 대체됐다.

## S2B-11 문장 양태 fail-closed 교정

명확한 완료 평서문만 인정하고, 완료형이 있는 문장과 바로 다음 문장에 질문부호,
인용부호, 전언 동사, 부정·철회 표지가 있으면 `NO_EVIDENCE`로 판정하도록 교정했다.
완료 단언이 없는 `배포 여부` 문장도 거절한다. 구현 전 대상 단위 테스트는 27개 중
신규 양태 테스트 1개가 실패해 `“주문 API를 배포했습니다”라고 했나요?`를 완료
사실로 오인하는 문제를 재현했다.

| 범위 | 명령 | 결과 |
|---|---|---|
| 대상 단위 | `.\gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `PASS` — 27/27, skip 0 |
| 대상 PostgreSQL·pgvector | `.\gradlew.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1, skip 0 |
| 현재 source TUNING 15 | `$env:PRIZM_SEARCH_EVALUATION_SPLIT='TUNING'; $env:PRIZM_SEARCH_EVALUATION_PROFILE='source-dedup-evidence-signals-v1'; .\gradlew.bat searchEvaluation --no-daemon --rerun-tasks -PsearchEvaluationDataset=src/test/resources/search-evaluation/v2-3 -PsearchEvaluationOutput=local/search-evaluation/prz008-tuning-v23-s2b11-20260807` | `PASS` — top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0·PDF page 1.0 |
| 고정 TEST·전체 회귀·독립 감사 | 실행하지 않음 | `NOT_RUN` |

대상 단위 클래스에서 제목은 순위 보조로만 사용되는 경계, `Kafka랩` 차단과 정확
토큰 경계도 함께 통과했다. Run E는 Dataset
`prizm-search-evidence-synthetic-v2.3`, 결합 SHA-256
`f1bf3cffd1cc51d7c5f972e55fe99a8afe9dce45e403ef742a7e3d0b25bb7f9f`, Docker
Desktop 29.6.2, PostgreSQL 16.14·pgvector 0.8.2, Ollama 0.32.6과
`bge-m3:latest` digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`에서 실행했다.
Direct MRR@5·@20은 `1.0`, nDCG@5는 `0.9783`, total p50·p95는 `117ms`·`130ms`,
embedding p50·p95는 `114ms`·`127ms`, DB p50·p95는 `3ms`·`3ms`였다. raw JSON·CSV는
`local/search-evaluation/prz008-tuning-v23-s2b11-20260807/`에 보존했다. 실제 사용자
DB와 OpenSQL은 사용하거나 변경하지 않았다.

## S2B-12 완료 양태 필수 Gate 교정

독립 재감사에서 완료 양태가 핵심어 coverage와 합산돼, 완료 질문이더라도 핵심어 두
개만 일치하면 `EVIDENCE_FOUND`가 되는 P1을 확인했다. 같은 완료문을 질문·꼬리질문·
전언·인용·부정·철회로 변환하고 대상 핵심어를 하나와 둘로 바꾸는 단위 테스트를 먼저
추가했다. 구현 전 대상 단위는 28개 중 신규 테스트 1개가 실패했고
`주문 API를 배포했습니다, 맞습니까.`를 완료 근거로 오인했다.

완료 이력 질의에서는 `completedReleaseClaim`을 핵심어 점수와 별개인 필수 Gate로
검사한다. 완료형 뒤에 추가 어절이 있는 문장은 명확한 완료 평서문으로 인정하지 않고,
전언 frame과 확대된 부정·철회 표지를 같은 문장과 바로 다음 문장에서 검사한다.
버전·소수점 내부의 마침표는 문장 경계에서 제외한다.

| 범위 | 명령 | 결과 |
|---|---|---|
| 실패 재현 | `.\gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `FAIL` — 28개 중 신규 변환 테스트 1 fail, skip 0 |
| 문장부호 경계 재현 | 같은 명령 | `FAIL` — 연속 마침표 변환에서 `StringIndexOutOfBoundsException` 재현 |
| 대상 단위 | 같은 명령 | `PASS` — 28/28, skip 0 |
| 대상 PostgreSQL·pgvector | `.\gradlew.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1, skip 0 |
| TUNING·고정 TEST·전체 회귀 | 실행하지 않음 | `NOT_RUN` |

직전 Run E는 변경 전 source의 역사적 결과로 보존하며 현재 source의 검증 결과로
확대하지 않는다. API·ownership·Dataset·migration·dependency는 변경하지 않았다.

## S2B-13 claim unit 완료 주장 Gate 교정

S2B-12 독립 재감사에서 청크 전체의 핵심어와 다른 문장의 완료 신호가 합성되는 우회,
제한된 양태 blacklist 밖 표현의 승인, 줄바꿈 경계 누락과 무관한 `없`·`취소`·인용부호
때문에 명확한 완료문이 오거절되는 P1을 확인했다. 구현 전 변환 기반 대상 단위는 29개
중 신규 테스트 2개가 실패했다. `문제없이 주문 API를 배포했습니다.`는 오거절됐고,
`주문 결제 API를 배포했습니다? 정산 API를 배포했습니다.`는 두 unit의 신호가 합성돼
근거로 승인됐다.

구현 중 자체 감사에서는 `주문 결제 API 배포 여부를 확인했고 정산 API를 배포했습니다.`가
한 문장 안의 두 절을 합성하는 추가 실패도 재현했다. 접속형 predicate와 독립 종결형 절의
경계를 claim unit 분리에 포함하고, 교정 표지와 뒤따르는 부정 절은 쉼표만으로 분리하지
않도록 교정했다.

완료 활용형과 `출시 이력`·`배포 경험`·`배포 여부` 명사형 의도를 완료 질의로 분류하고,
후보 본문은 줄바꿈·CRLF·연속 종결부호와 버전·소수점 내부 마침표를 구분한 claim
unit으로 나눈다. 하나의 unit이 대상 식별자·수치·핵심어와 지원 완료 predicate 및 직접
긍정 양태를 모두 만족할 때만 Gate를 통과한다. 바로 다음 unit은 교정 양태와 앞 주장
참조 cue 또는 같은 대상의 출시·배포 참조가 함께 있을 때만 앞 주장을 무효화한다.

| 범위 | 명령 | 결과 |
|---|---|---|
| 실패 재현 | `.\gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `FAIL` — 29개 중 신규 테스트 2 fail, skip 0 |
| 절간 합성 자체 감사 재현 | 같은 명령 | `FAIL` — 29개 중 절간 합성 테스트 1 fail, skip 0 |
| 대상 단위 | 같은 명령 | `PASS` — 29/29, skip 0 |
| 대상 PostgreSQL·pgvector | `.\gradlew.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1, skip 0 |
| TUNING·고정 TEST·전체 회귀·OpenSQL | 실행하지 않음 | `NOT_RUN` |

직전 Run E와 S2B-11 TUNING은 현재 source에서 재실행하지 않은 역사적 결과다. 기존
API·세 상태·owner·`ACTIVE`·Dataset v2.2/v2.3·migration·dependency·설정은 변경하지
않았다. 별도 독립 재감사는 후속 Gate로 남긴다.

## S2B-14 폐쇄형 완료 문법과 유한 P1 계약

임의의 한국어 문장 의미를 모두 판정한다는 무한 계약을 폐기했다. 질의는 등록 생산식과
완료 민감 탐지 경계에 따라 `SUPPORTED`·`UNSUPPORTED`·`NONE`으로 분류한다. 후보는
등록 prefix, 질의에서 얻은 불투명 대상 token 순서, 단일 후행 annotation, 등록 완료
평서 predicate와 필수 평서 종결부호를 하나의 claim unit에서 full match할 때만
승인한다. P1은 등록 질의 생산식·탐지 집합 오분류, 문법 밖 claim unit 자체 승인,
unit·제목 간 신호 합성, 등록 변환의 오거절로 한정했다.

첫 변환 RED에서는 대상 단위 31개 중 4개 메서드가 실패해 unsupported 질의 fallback,
떨어진 명사형 marker 합성, wrapper·followup 우회, 부정 대상과 다른 완료 대상의 합성을
재현했다. 첫 구조 구현 뒤 단위 31/31과 PostgreSQL 1/1을 통과했다. 독립 감사에서
분리 표기 완료 질의, 질의의 중간 구두점·후행 token 순서 손실, 종결부호 없는 claim
승인 P1 3건을 찾았다. 이를 고정한 두 번째 RED는 31개 중 2개 메서드가 실패했고 교정
뒤 31/31과 PostgreSQL 1/1을 통과했다.

다음 독립 감사에서는 SPEC의 단일 후행 annotation과 달리 version과 별칭을 연속
소비하는 P1 1건을 찾았다. `주문 API v1.2 “오로라”를 배포했습니다.` 변환을 추가한
RED는 31개 중 1개 메서드가 실패했다. 별칭과 version branch를 상호 배타적으로 만든
뒤 두 annotation 순서 모두 거절되며 대상 단위 31/31, 대상 PostgreSQL 1/1을
통과했다.

그다음 독립 감사에서는 완료 대상 token에 범용 의미 어간화를 적용해 `주문하는`과
`주문`을 같은 대상으로 승인하는 P1 1건을 찾았다. `하는`·`한`·`된` suffix 변환 RED는
31개 중 대상 결합 메서드 1개가 실패했다. 완료 대상 전용 정규화를 분리해 NFKC·소문자와
ASCII 식별자 조사만 허용하고 의미 어간화는 제거했다. 교정 뒤 대상 단위 31/31을
통과했다.

이어진 독립 감사에서는 raw 비등록형 `출시하는`을 범용 stem이 bare `출시`로 축약해
일반 검색으로 fallback하는 P1과, 불투명 대상의 `경험` token을 marker로 금지하는 P1을
찾았다. 비등록형과 예약어 대상 변환 RED는 31개 중 2개 메서드가 실패했다. query
candidate 개수로 조기 판정하지 않고 각 candidate의 전체 생산식을 먼저 검증해 유일한
성공 parse를 고른 뒤, 성공이 없을 때만 raw 비등록형을 탐지하도록 바꿨다. 대상구의
완료·marker token도 불투명하게 보존한다. 교정 뒤 대상 단위 31/31을 통과했다.

후속 자체 감사에서는 query noun·marker 판정에 남은 범용 stem이 `출시하는 이력`과
`출시 경험하는`을 등록 명사형으로 승인할 수 있음을 확인했다. 변환 RED는 31개 중
unsupported 질의 메서드 1개가 실패했다. query 문법 정규화를 exact base와 등록 조사
10개로 분리하고, 바로 인접한 비등록 marker 포함 token은 malformed nominal intent로
닫았다. `출시 계획과 운영 경험`의 일반 검색 경계는 그대로 두었다. 교정 뒤 대상 단위
31/31을 통과했다.

같은 자체 감사에서 claim prefix를 항상 최대로 소비하면 대상 자체가 `문제없이`나
등록 날짜로 시작할 때 동일한 직접 주장을 오거절하는 모호성도 확인했다. prefix 대상
변환 RED는 31개 중 대상 결합 메서드 1개가 실패했다. prefix를 0개부터 한 요소씩
소비하는 유한 parse 후보로 만들고 exact target이 일치하는 해석만 채택했다. 교정 뒤
대상 단위 31/31을 통과했다.

5차 독립 감사에서는 불투명 대상 token이 우연히 `했고` 등 접속형 suffix로 끝나면
형태 기반 claim splitter가 대상을 잘라 등록 직접 주장을 오거절하는 P1 1건을 찾았다.
등록 접속형 suffix 전체를 대상으로 바꾼 RED는 31개 중 대상 결합 메서드 1개가
실패했다. exact target+predicate full match가 절간 신호 합성을 이미 차단하므로 중복된
형태 기반 splitter를 제거했다. 기존 절간 합성 음성 변환과 접속형처럼 보이는 대상
양성 변환이 함께 단위 31/31을 통과했다.

6차 독립 감사에서는 SPEC이 target token 끝 `.`·`_`·`-` 제거를 지원한다고 잘못
기록해, 공백-only query separator 구현과 충돌하는 P1 1건을 찾았다. 이 경계는 지원
문법에서 제외하고 query와 claim target 모두 token 사이 순수 공백을 요구하도록
일치시켰다. 내부 `PRIZM-v1`·`Node.js`·`v1.2`는 보존하고 `PRIZM- API` 같은 경계
합성은 거절한다. claim target 경계 변환 RED는 31개 중 대상 결합 메서드 1개가
실패했으며 교정 뒤 대상 단위 31/31을 통과했다.

후속 자체 감사에서는 내부 마침표 지원을 문장 경계와 동일하게 제한하지 않으면
`C#.NET`처럼 query에서는 한 token이지만 claim에서는 두 문장으로 갈리는 모순을
확인했다. 비등록 마침표 RED는 31개 중 unsupported 질의 메서드 1개가 실패했다.
내부 `.` 양쪽이 ASCII 영숫자인 형식만 query target으로 지원해 `Node.js`·`v1.2`·
`1.25` 경계와 일치시켰고, 교정 뒤 대상 단위 31/31을 통과했다.

7차 독립 감사에서는 균형 제품 별칭 body를 무제한 문자열로 기술해 내부 문장부호가
claim unit 경계와 충돌하는 P1 1건을 찾았다. 임의 구두점을 quote-aware 문장 파서로
확장하지 않고 별칭 body를 Unicode 문자·숫자·공백·`+ # _ -`로 폐쇄했다. 허용 별칭과
제외 구두점 변환 RED는 31개 중 양태 변환 메서드 1개가 실패했고, regex와 SPEC을 함께
교정한 뒤 대상 단위 31/31을 통과했다.

| 범위 | 명령 | 결과 |
|---|---|---|
| 최초 구조 RED | `gradle.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `FAIL` — 31개 중 4개 메서드 fail, skip 0 |
| 1차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 2개 메서드 fail, skip 0 |
| 2차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 annotation 변환 메서드 1개 fail, skip 0 |
| 3차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 불투명 대상 token 변환 메서드 1개 fail, skip 0 |
| 4차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 query 탐지·예약어 대상 메서드 2개 fail, skip 0 |
| query 문법 stem 자체 감사 RED | 같은 명령 | `FAIL` — 31개 중 unsupported 질의 메서드 1개 fail, skip 0 |
| claim prefix 자체 감사 RED | 같은 명령 | `FAIL` — 31개 중 불투명 대상 메서드 1개 fail, skip 0 |
| 5차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 접속형 대상 메서드 1개 fail, skip 0 |
| 6차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 target separator 메서드 1개 fail, skip 0 |
| 내부 마침표 자체 감사 RED | 같은 명령 | `FAIL` — 31개 중 unsupported 질의 메서드 1개 fail, skip 0 |
| 7차 감사 finding RED | 같은 명령 | `FAIL` — 31개 중 별칭 body 변환 메서드 1개 fail, skip 0 |
| 최종 대상 단위 | 같은 명령 | `PASS` — 31/31, skip 0 |
| 최종 대상 PostgreSQL·pgvector | `gradle.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1, skip 0 |
| 최종 독립 재감사 | 읽기 전용 정적 감사 | `PASS` — API 경계·양태·Dataset 보존 P0 0 / P1 0; 양태·Dataset 감사 중 테스트 재실행 없음 |
| 고정 Dataset TEST 사전 실행 — legacy 기본 profile | `$env:PRIZM_SEARCH_EVALUATION_SPLIT='TEST'; .\gradlew.bat searchEvaluation --no-daemon --rerun-tasks -PsearchEvaluationDataset=src/test/resources/search-evaluation/v2-3 -PsearchEvaluationOutput=local/search-evaluation/prz008-test-v23-legacy-20260807` | `FAIL` — 1/1; 이전 runner가 `This Batch permits Dataset v2 TUNING execution only.`로 색인·검색·지표 산출 전 중단 |
| v2.3 allow·fixture 선택 단위 | `SearchEvaluationDatasetSelectorTest` 대상 단위 | `PASS` — 4/4; v2.2·flag 누락·TUNING은 거절하고, v2.3 `TEST`·`true`와 질문 참조 fixture만 허용 |
| 고정 Dataset TEST 비교 — legacy 기본 profile | `$env:PRIZM_SEARCH_EVALUATION_SPLIT='TEST'; $env:PRIZM_SEARCH_EVALUATION_ALLOW_FROZEN_TEST='true'; $env:PRIZM_SEARCH_EVALUATION_PROFILE='current-product'; .\gradlew.bat searchEvaluation --no-daemon --rerun-tasks -PsearchEvaluationDataset=src/test/resources/search-evaluation/v2-3 -PsearchEvaluationOutput=local/search-evaluation/prz008-test-v23-legacy-final-20260807` | `PASS` — 1/1; Direct MRR@5 `1.0000`, nDCG@5 `0.9710`, no-evidence rejection `0`, evidence false rejection `0` |
| 고정 Dataset TEST 비교 — opt-in profile | `$env:PRIZM_SEARCH_EVALUATION_SPLIT='TEST'; $env:PRIZM_SEARCH_EVALUATION_ALLOW_FROZEN_TEST='true'; $env:PRIZM_SEARCH_EVALUATION_PROFILE='source-dedup-evidence-signals-v1'; .\gradlew.bat searchEvaluation --no-daemon --rerun-tasks -PsearchEvaluationDataset=src/test/resources/search-evaluation/v2-3 -PsearchEvaluationOutput=local/search-evaluation/prz008-test-v23-opt-in-final-20260807` | `PASS` — 1/1; Direct MRR@5 `0.1667`, nDCG@5 `0.1667`, no-evidence rejection `1.0000`, evidence false rejection `0.8333`; 기본값 승격 보류 |
| TUNING·전체 회귀·OpenSQL | 실행하지 않음 | `NOT_RUN`; 직전 TUNING은 `HISTORICAL_PASS_NOT_RERUN` |

기존 `/api/search`와 Career Evidence v1/v2, 세 검색 상태, owner·`ACTIVE`·
`SYSTEM_ADMIN` 경계, Kafka/Kafka랩 exact token, 제목 근거 차단과 Dataset v2.2/v2.3을
보존했다. migration·dependency·설정은 변경하지 않았다.

고정 TEST 비교는 `legacy-dense-v1` 기본 동작을 runner의 `current-product` 선택으로
실행했다. 기본 TUNING 제한은 유지하면서 v2.3·`TEST`·명시 flag만 허용했다. 선택 split의
질문이 참조하지 않는 fixture는 owner·version scenario를 추론하지 않고 seed 대상에서
제외해 기존 fixture 불변식을 보존했다. 두 profile run은 각각 1/1로 완료했다.

| 지표 | legacy (`current-product`) | opt-in (`source-dedup-evidence-signals-v1`) |
|---|---:|---:|
| Direct MRR@5 | 1.0000 | 0.1667 |
| nDCG@5 | 0.9710 | 0.1667 |
| no-evidence rejection | 0 | 1.0000 |
| evidence false rejection | 0 | 0.8333 |
| duplicate result ratio | 0 | 0 |

위 S2C-01 결과는 S2C-02 문법 보정 전의 비교로 보존한다.

## S2C-02 직접 근거·정확 사실 문법 보정과 최종 비교

S2C-02는 완료 이력의 동일 claim unit Gate를 완화하지 않았다. 직접 근거·정확 수치·날짜
질의에 한해 본문의 바로 앞 프로젝트 이름/참여 선언과 직접 긍정 완료 claim을 제한적으로
연결하고, 제목·질문·인용·전언·부정·철회·`Kafka랩` 부분 일치는 계속 근거에서 배제했다.
Dataset v2.2/v2.3과 고정 TEST 파일은 변경하지 않았다.

| 검증 | 명령 또는 범위 | 결과 |
|---|---|---|
| 변환 기반 단위 RED | `SearchEvaluationCompositeProfileTest` 대상 | `FAIL` — 구현 전 33개 중 `supportsBoundedProjectIdentityDirectEvidenceAndExactCompletionFacts` 1개 fail; TUNING의 Lumen 직접 근거를 추가한 뒤에도 같은 대상 33개 중 1개 fail |
| 최종 대상 단위 | `./gradlew.bat test --tests "com.prizm.search.evaluation.SearchEvaluationCompositeProfileTest" --no-daemon --rerun-tasks` | `PASS` — 33/33 |
| 최종 대상 PostgreSQL·pgvector | `./gradlew.bat integrationTest --tests "com.prizm.infrastructure.PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql" --no-daemon --rerun-tasks` | `PASS` — 1/1 |
| 최초 TUNING | v2.3 `TUNING`, opt-in | `FAIL` — runner 1/1이나 Direct MRR@5/@20 `0.8750`, 근거 오거부 `0.1250`; Lumen 직접 근거 문법 누락을 재현 |
| 최종 TUNING | v2.3 `TUNING`, opt-in, 15문항 | `PASS` — Direct MRR@5/@20 `1.0000`, nDCG@5 `0.9783`, 중복 `0`, 무근거 거부 `1.0`, 근거 오거부 `0`, total p95 `138ms` |
| 고정 TEST — legacy | v2.3 `TEST`, allow flag, `current-product` | `PASS` — 1/1; Direct MRR@5/@20 `1.0000`, nDCG@5 `0.9710`, total p95 `138ms` |
| 고정 TEST — opt-in | v2.3 `TEST`, allow flag, opt-in | `PASS` — 1/1; Direct MRR@5/@20 `1.0000`, nDCG@5 `0.9710`, top-1 직접 근거 `1.0`, 무근거 거부 `1.0`, 근거 오거부 `0`, 중복 `0`, PDF page `1.0`, total p95 `160ms` (+`15.9%`) |

최종 결과 JSON·후보 CSV는
`local/search-evaluation/prz008-tuning-v23-s2c02-final-20260808/`,
`local/search-evaluation/prz008-test-v23-legacy-s2c02-20260808/`,
`local/search-evaluation/prz008-test-v23-opt-in-s2c02-20260808/`에 있다. 고정 TEST 뒤에는
구현·설정·threshold를 재조정하지 않았다. 실제 OpenSQL, 전체 회귀, commit·push는 실행하지
않았다.

## 남은 Gate

- 실제 OpenSQL direct `5432`에서 동일한 제품 상태와 owner 경계를 검증한다.
- backend 전체 회귀, 필요한 frontend·OSS·SBOM 검사와 독립 재감사를 수행한다.

2026-08-10에는 OpenSQL 실행 환경을 찾지 못해 제품 Gate를 `NOT_RUN`으로 기록했다.
2026-08-11 실제 사용자 VirtualBox 등록부에서 `PRIZM-OpenSQL` VM을 확인해 GUI로 정상
기동했다. Rocky Linux 로그인 화면과 Host-only DHCP lease까지 확인했지만 SSH `22`,
OpenSQL direct `5432`, Patroni `8008`, OpenProxy `6432`는 모두 닫혀 있었다. Guest
Additions 원격 실행 경로도 없어 OS 인증 전에는 의도적으로 `disabled`인 Patroni를 안전하게
시작할 수 없다. 비밀번호를 추측·초기화하거나 가상 디스크·VM 설정을 변경하지 않았으며,
API·UI·자동 OpenSQL 검증과 데이터베이스 변경은 아직 `NOT_RUN`이다. VM은 로그인 대기
상태로 켜 두었다. 재개에는 VM 콘솔의 승인된 OS 관리자 로그인, 분리된 runtime/Flyway
자격증명 주입, 호스트 Ollama `bge-m3`와 API·frontend 실행 환경이 필요하다.

같은 준비 점검에서 호스트 Ollama `0.32.6`과 기존 `bge-m3:latest` digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`를 확인했다.
검증 probe는 1024차원·finite·non-zero embedding을 반환했다. 로컬 비공개 `.env`의
DB 이름과 역할 이름은 `prizm`, `prizm_owner`, `prizm_app`에 맞고 두 DB 비밀번호도
비어 있지 않았다. 값은 출력·문서화하지 않았고 아직 OpenSQL 인증에 사용하지 않았다.

남은 Gate 전에는 새 profile을 기본값으로 승격하거나 PRZ-008을 `VERIFIED`로
표시하지 않는다.

2026-08-11 actual OpenSQL direct `5432` API run: the existing VM was started, Patroni
reported the single-node leader ready, and the opt-in Spring Boot process started with the
`opensql` profile. Its health endpoint returned `UP`. Host Ollama `bge-m3` returned a
1024-dimensional finite, non-zero embedding. No OpenProxy route was used.

The API product Gate passed against that running process. A fresh synthetic USER_A and USER_B
were created through the public auth API. USER_B, which had no searchable documents, received
`NO_SEARCHABLE_DOCUMENTS`. USER_A uploaded a synthetic TXT and PDF; both immutable versions
became `ACTIVE`, with `TEXT_CHUNK` and `PAGE` source metadata respectively. The v2 Career
Evidence API returned `EVIDENCE_FOUND` for each direct marker, `NO_EVIDENCE` for an unrelated
query, and no more than five results. USER_B could neither list USER_A documents nor read a USER_A
document detail (404), and USER_B's query for USER_A evidence remained
`NO_SEARCHABLE_DOCUMENTS`. Result: `PRZ008_OPENSQL_PRODUCT_GATE_API=PASS`.

Frontend/UI verification passed through the allowed `http://localhost:5173` origin. The in-app
browser created and logged in a fresh synthetic USER, uploaded a TXT source through the UI, and
showed its completed processing state in the document list. The completed-release query
`Aurora UI 프로젝트에서 주문 API 8개를 배포한 이력이 있나요?` displayed one matching source
chunk with the exact uploaded evidence. An earlier `127.0.0.1:5173` attempt was correctly
rejected by the configured explicit CORS origin policy and is not used as product evidence.

The OpenSQL direct `5432` product Gate is `PASS` for the executed API and UI scope. The
default profile is `source-dedup-evidence-signals-v1`; explicit `legacy-dense-v1` remains the rollback override.
No TUNING, full regression, OpenSQL automated integration test, commit, or push was run in this attempt.

2026-08-11 S2C-03 promoted the configured default from legacy-dense-v1 to
source-dedup-evidence-signals-v1. The code-level default and YAML fallback now agree;
PRIZM_SEARCH_PROFILE=legacy-dense-v1 remains the explicit rollback path. The focused verification
command ran SearchPropertiesTest, SearchServiceTest, and SearchEvaluationCompositeProfileTest with
--no-daemon --rerun-tasks and passed with 50 tests and zero failures. The first attempt did not
start because the sandbox blocked the Gradle wrapper download; the same command was then rerun
with approved network access and passed. TUNING, fixed Dataset TEST, PostgreSQL integration, full
backend/frontend regression, and OpenSQL automation were not rerun because the promotion changed
only the selected default, not ranking, claim-unit logic, API, Dataset, schema, or dependencies.

## S2C-04 전체 통합 회귀 fixture 정렬

Docker Desktop Engine 29.6.2가 사용자별 설치 경로에서 실행 중인 것을 확인한 뒤 전체
Testcontainers 회귀를 실행했다. 최초 전체 integration run은 78건 중 2건이 실패했다.
`/api/career-evidence/search` fixture의 의미상 유사한 질의와 v2 fixture의 일반 영문
질의는 기본 `source-dedup-evidence-signals-v1`의 직접 근거 Gate를 충족하지 않았고,
기존 dense 전용 fixture는 정확히 5건을 기대했으나 새 profile의 중복 축약 결과는 2건이었다.

명세의 기본 profile·v1 배열 호환·최대 5건·중복 축약 계약은 변경하지 않았다. 구현을
legacy로 되돌리거나 claim-unit Gate를 완화하지 않고, Authentication 통합 fixture의 v1/v2
질의를 본문과 동일한 직접 근거로 바꾸고 PostgreSQL assertion을 `1..5`로 교정했다.

| 검증 | 명령 또는 범위 | 결과 |
|---|---|---|
| Docker·Compose | `docker version`, `docker compose config --quiet` | `PASS` — Engine 29.6.2 Linux, Compose config 통과 |
| 초기 전체 integration | `./gradlew.bat integrationTest --no-daemon --rerun-tasks` | `FAIL` — 78건 중 2 fail·3 skip; stale dense fixture 기대와 기본 profile 결과 불일치 |
| 대상 재현 | 두 실패 메서드 대상 integration | `FAIL` — 2/2 재현 |
| 교정 대상 integration | 같은 두 메서드 | `PASS` — 2/2 |
| backend integration 전체 | `./gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — 78건 중 75 pass·3 skip·실패 0 |
| backend unit 전체 | `./gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 350건 중 335 pass·15 skip·실패 0 |

TUNING과 고정 Dataset TEST는 실행하지 않았다. 이 교정은 profile 구현, Dataset,
threshold, migration, dependency, API schema, ownership 또는 `ACTIVE` 경계를 바꾸지 않았다.

## 실제 UI 검색 품질 최소 보정

2026-08-12 실제 PostgreSQL 사용자 문서에서 `Springboot 활용 경험`의 상위 resume 청크는
dense `0.513676424`, portfolio 개요 청크는 `0.504518121`이었다. 상위 resume 청크 전체에는
AirConnect 운영·알림·동시성 설계 근거가 있었지만 기존 snippet은 동점인 첫
`Java / Spring Boot` 헤더를 선택했다. 인증 통합 청크는 문제 문장을 선택하고도 바로 앞의
긴 기술 스택 줄을 먼저 붙인 뒤 360자에서 잘라 핵심 문제·해결 문장을 가렸다. 또한 같은
이력서 PDF가 서로 다른 두 ACTIVE 문서로 등록되어 완전히 동일한 본문 그룹 6개와 중복
청크 6개가 존재했으나, source dedup은 document version 경계를 넘지 않아 함께 노출됐다.

검색 profile·ranking·threshold는 변경하지 않았다. snippet은 동일 lexical score일 때
질의가 경험·활용을 요구하면 뒤쪽의 구현·운영·개선 문맥이 있는 문장을 선택하고, 선택된
핵심 문장을 360자 앞쪽에 반드시 보존한다. SearchService 표현 단계에서는 CRLF와 가장자리
공백을 제외하고 본문이 완전히 같은 결과만 첫 순서·원래 score/distance를 유지한 채 한 건으로
축약한다. 의미 유사도 기반 dedup, 새 ranking 신호, threshold·Top20·최대 5건 변경은 없다.

| 검증 | 결과 |
|---|---|
| snippet·SearchService focused unit | `PASS` — 26/26; Spring Boot 경험 문맥, 인증 문제·해결 우선, cross-document exact-content 축약 |
| P8 40 | `PASS` — Top1 `28/34`, Recall@5 `29/34`, 무근거 오탐 `1/6` |
| P17 28 | `PASS` — Top1 `9/15`, Recall@5 `10/15`, 무근거 오탐 `1/13` |
| P18 기준 JSON 비교 | `PASS` — 68문항 결과 ID·순서·상태·relevance 차이 `0` |
| backend unit 전체 | `PASS` — 423개 중 408 pass·15 skip·실패 0 |
| backend integration 전체 | `PASS` — 78개 중 75 pass·3 skip·실패 0 |

평가는 PostgreSQL·pgvector와 호스트 Ollama `bge-m3`에서 실행했다. OpenSQL은 이번 보정에서
재실행하지 않았다. 로컬 backend 최신 이미지 재빌드·재가동과 actuator `UP`은 확인했다.
자동화 브라우저에는 사용자 로그인 세션이 없어 인증된 수정 후 화면 확인은 `NOT_RUN`이며,
사용자 세션에서 새로고침 후 확인한다.
