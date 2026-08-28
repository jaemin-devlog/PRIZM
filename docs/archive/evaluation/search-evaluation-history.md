# 검색 품질 평가 실행 이력

> **역사 기록:** 이 문서는 검색 평가의 날짜별 실행 환경, 수치와 당시 판정을
> 원문 그대로 보존합니다. 현재 평가 도구 사용법과 데이터 계약은
> [검색 품질 평가](../../evaluation/search-evaluation.md)를 확인하세요.

아래 결과는 기록된 소스·Dataset·PostgreSQL·Ollama 환경에만 해당합니다. 과거
`PASS`를 현재 소스의 결과로 바꾸거나 PostgreSQL 결과를 OpenSQL 결과로 확대하지 않습니다.

## PRZ-008 Dataset과 profile 실행 메모

기존 `sample` Dataset 30문항과 아래 v2·v2.1·v2.2 과거 기준선은 변경하지 않았습니다.
Dataset v2의 TUNING 10문항, v2.1의 실패 재현 15문항, v2.2의 평가 profile 15문항과
v2.3의 본문 근거 profile 15문항을 각각 측정했습니다. 고정 v2.3 TEST 최종 비교는
별도 명시 allow 조건으로만 완료했으며, 결과로 기본 profile을 변경하지 않았습니다.

2026-08-08 S2C-02에서 Dataset v2.3은 변경하지 않은 채 opt-in을 TUNING 15문항과 고정
TEST 10문항으로 재검증했습니다. TUNING은 Direct MRR@5/@20 `1.0000`, 근거 오거부 `0`으로
통과했고, TEST에서 opt-in은 Direct MRR@5/@20 `1.0000`, nDCG@5 `0.9710`, 무근거 거부
`1.0`, 근거 오거부 `0`, 중복 `0`, PDF page 정확도 `1.0`, total p95 `160ms`(legacy `138ms`,
+`15.9%`)를 기록했습니다. 이후 실제 OpenSQL direct `5432` API·UI Gate도 통과했고,
사용자 승인으로 기본 profile은 `source-dedup-evidence-signals-v1`으로 승격했습니다.

Dataset v2는 split을 명시하지 않으면 실행을 거부합니다. 기존 sample Dataset의 기본
실행 계약은 유지합니다. Batch 1C의 TUNING-only 실행은
현재 PowerShell 프로세스에 다음 환경변수를 설정해 수행했습니다.

평가용 profile 결과를 제품 threshold나 현재 제품 동작으로 표현하지 않습니다. Batch
1B에서는 profile 계약만 추가했으며 threshold 값은 선택하지 않았습니다.

## 2026-08-06 PRZ-008 Dataset v2 TUNING 기준선

기준 source는 `b980e593ead1013704cd6eb6ce0664904e244879`이며 평가 전용 변경이 남은
작업 트리에서 실행했습니다. Dataset ID는 `prizm-search-evidence-synthetic-v2`입니다.
`corpus.json` bytes 뒤에 `questions.jsonl` bytes를 이어 계산한 SHA-256은
`aecf3cca052e30e4937919920f7d53bfc117512bae8f2d9004a8f5f23e57c3c5`입니다.
Docker Desktop 29.6.2, PostgreSQL 16.14를 포함한
`pgvector/pgvector:0.8.2-pg16-bookworm` 이미지와 Ollama 0.32.5를 사용했습니다. 모델은
`bge-m3:latest`, digest는
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, embedding은
1024차원입니다. 후보 수는 20, 사용자 반환 수는 5, 순위는 exact cosine이며 제품
threshold는 없습니다.

| 지표 | 현재 제품 기준선 |
|---|---:|
| 질문 | TUNING 10개 |
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 |
| Precision@5 / Direct Precision@5 | 0.1000 / 0.1000 |
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9566 |
| 중복 결과 비율 | 0.0200 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 0.0000 / 0.0000 |
| 사용자 반환 수 min / avg / max | 5 / 5.0 / 5 |
| 후보 수 min / avg / max | 6 / 6.0 / 6 |
| total p50 / p95 | 117ms / 127ms |
| embedding p50 / p95 | 115ms / 124ms |
| DB p50 / p95 | 2ms / 4ms |

후보 수 5·10·20의 prefix 품질은 이 작은 corpus에서 모두 Recall 1.0000, Direct MRR@20
1.0000, nDCG@5 0.9566이었습니다. DB p50은 모두 2ms였고 p95는 각각 2ms, 2ms,
4ms였습니다. 실제 후보가 질문마다 6개뿐이므로 10과 20의 차이는 운영 규모 후보 수
효과를 증명하지 않습니다.

근거 질문 top-1 score 범위는 `0.5582~0.7281`, 무근거 질문은 `0.3740~0.5849`로
겹쳤습니다. `0.50`에서는 무근거 거부율 0.6667, 오거부율 0.0000과 기존 MRR·nDCG를
유지했지만, `0.585`에서는 무근거 질문을 모두 거부하는 대신 오거부율이 0.2500,
Direct MRR@5·@20이 0.7500, nDCG@5가 0.7066으로 떨어졌습니다. 하나의 threshold가
두 집단을 안정적으로 분리하지 못하므로 결과는 `THRESHOLD_NOT_SEPARABLE`입니다.
평가 profile과 제품 threshold는 고정하지 않았고 TEST도 실행하지 않았습니다. raw JSON과
후보 CSV는 Git에서 제외된 `local/search-evaluation/prz008-tuning-baseline/`에 있습니다.
이 결과는 v2.1 TUNING 실패 재현 사례를 추가하기 전의 과거 기준선이며, 새 5문항의 실제
검색 결과로 확대 해석하지 않습니다.

## 2026-08-06 PRZ-008 Dataset v2.1 TUNING 실패 재현

기준 source commit은 `b980e593ead1013704cd6eb6ce0664904e244879`이며 평가 전용 변경이
남은 작업 트리에서 실행했습니다. Dataset ID는
`prizm-search-evidence-synthetic-v2.1`, 결합 SHA-256은
`5e91468f3de7263f558116b83b8bbb32803c242cf0d5a0f1ba1a814cad1bf61c`입니다.
Docker Desktop 29.6.2, PostgreSQL 16.14·pgvector 0.8.2 Testcontainer, Ollama 0.32.5와
`bge-m3:latest`를 사용했습니다. 모델 digest는
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`이고 embedding은
1024차원입니다. 후보 20개를 평가하고 사용자 결과 5개를 반환하는 현재 제품 profile이며
threshold는 없습니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 |
| Precision@5 / Direct Precision@5 | 0.1867 / 0.1467 |
| Direct MRR@5 / @20 | 0.9375 / 0.9375 |
| nDCG@5 | 0.9321 |
| top-1 직접 근거 정확도 | 0.8750 |
| 중복 결과 비율 | 0.0933 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 0.0000 / 0.0000 |
| 사용자 반환 수 / 실제 후보 수 | 질문별 5 / 11 |
| total p50 / p95 | 120ms / 130ms |
| embedding p50 / p95 | 117ms / 127ms |
| DB p50 / p95 | 3ms / 4ms |

추가한 실패 재현 5문항에서 정답 질문은 relevance 순서 `2,2,0,1,0`, 매칭 오타 질문은
`2,0,2,1,0`, 외부 푸시 오타 질문은 `2,0,0,0,0`으로 직접 근거가 1위였습니다. 오타
질문 2개는 모두 top-1 직접 근거를 유지했습니다. 동일 페이지 중복 질문은
`0,2,1,0,0`으로 무관 청크가 1위이고 직접 근거가 2위였습니다. 새 5문항의 top-5 중
같은 evidence group 반복은 6건으로 중복률은 24%였습니다. 유사 주제 무근거 질문은
관련 근거가 없는데도 `EVIDENCE_FOUND`와 5개 결과를 반환했고 top-1 score는 0.5781이었습니다.

전체 근거 질문 top-1 score 범위 `0.5582~0.7281`과 무근거 질문 범위
`0.3740~0.5849`는 계속 겹칩니다. 따라서 score 단독 threshold는 여전히
`THRESHOLD_NOT_SEPARABLE`이며 제품 profile을 고정하지 않았습니다. 실행 결과의 split은
`TUNING` 하나뿐이고 TEST는 실행하지 않았습니다. raw JSON과 후보 CSV는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v21-failure-reproduction-20260806/`에 있습니다.

## 2026-08-07 PRZ-008 Dataset v2.2 TUNING profile

기준 source commit은 `b980e593ead1013704cd6eb6ce0664904e244879`이며 평가 전용 변경이
남은 작업 트리에서 실행했습니다. Dataset ID는 `prizm-search-evidence-synthetic-v2.2`,
결합 SHA-256은
`f946fed8c145112c1082d7c08c25b357bd459b4c7cc53deb7b7e23731a2b7c2c`입니다.
TUNING은 질문 15개, fixture 8개, evidence group 13개입니다. Docker Desktop 29.6.2,
PostgreSQL 16.14·pgvector 0.8.2 Testcontainer, Ollama 0.32.5와 `bge-m3:latest`를
사용했습니다. 모델 digest는
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`이고 embedding은
1024차원입니다.

이 측정 당시 평가 전용이었던 `source-dedup-evidence-signals-v1` profile은 exact cosine 후보 최대 20개를
그대로 측정한 뒤 같은 PDF page·TXT overlap을 축약합니다. 각 반환 후보에 고유 식별자,
수치, 핵심어와 명시적 부정 신호를 검사하고, 강한 식별자 또는 수치와 핵심어가 함께
반복되는 요약 근거는 문서가 달라도 한 결과로 묶었습니다. 이 측정 시점에는 제품 검색
동작이 아니었으며, 이후 Batch 2B에서 opt-in 제품 profile로 옮겼습니다.
Recall@20과 후보 수는 원래 후보를 사용하고 Precision·MRR@5·nDCG·top-1·중복률은 실제
반환 chunk ID와 반환 순서를 사용합니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 |
| Precision@5 / Direct Precision@5 | 0.1067 / 0.1067 |
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 중복 결과 비율 | 0.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 1.0000 / 0.0000 |
| 사용자 반환 수 min / avg / max | 0 / 0.5333 / 1 |
| 후보 수 min / avg / max | 11 / 11.0 / 11 |
| total p50 / p95 | 122ms / 132ms |
| embedding p50 / p95 | 119ms / 129ms |
| DB p50 / p95 | 3ms / 4ms |

직접 근거 질문 8개와 오타 질문 2개는 모두 relevance 2를 1위로 반환했습니다. 무관 질문
7개는 모두 `NO_EVIDENCE`와 결과 0건이었고, 근거 질문의 오거부는 없었습니다. 모든 근거
질문은 직접 근거 한 건만 반환해 같은 page·overlap·이력서 요약의 중복과 약한 후속 결과가
남지 않았습니다. 따라서 사전 고정한 작은 TUNING Gate는 통과했습니다.

이 결과는 평가 profile 동작을 고정할 근거일 뿐 제품 품질 완료나 일반화를 뜻하지
않습니다. 이 측정 당시에는 제품 source와 threshold를 변경하지 않았고 TEST·Batch 1D도
실행하지 않았습니다. raw JSON과 후보 CSV는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v22-composite-final-20260807/`에 있습니다.

## 2026-08-07 Batch 2B 현재 제품 source TUNING 재측정

Batch 2B opt-in 제품 source를 기준으로 Unicode 전체 토큰의 정확 일치와 단일 고유명사
정답 허용을 교정한 뒤 Dataset v2.2의 TUNING 15문항만 다시 실행했습니다. 기준 commit은
`83631f13c21eab54ac0f32ebb0f893b6c5acea0f`이며, 아래 두 실행은 그 위의 미커밋
worktree를 사용했습니다. Dataset ID와 결합 SHA-256은 각각
`prizm-search-evidence-synthetic-v2.2`와
`f946fed8c145112c1082d7c08c25b357bd459b4c7cc53deb7b7e23731a2b7c2c`입니다.

공통 환경은 Docker Desktop 29.6.2, PostgreSQL 16.14·pgvector 0.8.2
Testcontainer, Ollama 0.32.6, `bge-m3:latest`입니다. 모델 digest는
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`이고 embedding은
1024차원입니다. 모든 결과의 split은 `TUNING`이며 고정 TEST는 실행하지 않았습니다.

### Run A — exact-token 1차 교정 실패

Unicode 부분 문자열을 전체 토큰 일치로 바꾸고 단일 고유명사를 허용한 첫 실행에서는
직접 근거 8문항 중 7문항만 1위로 반환했습니다. `v2-t-paraphrase-lumen`의 직접 근거는
dense 후보 1위였지만 질문의 `출시한`과 근거의 `배포했다`를 같은 완료 행위로 판단하지
못해 `INSUFFICIENT_CORE_TERM_COVERAGE`로 오거절했습니다.

- top-1 직접 근거: `7/8`
- 오타 질문 top-1: `2/2`
- 무관 질문 거부율 / 근거 질문 오거부율: `1.0000 / 0.1250`
- Direct MRR@5 / @20: `0.8750 / 0.8750`
- nDCG@5: `0.8533`
- 중복 결과 비율: `0.0000`
- raw 결과: `local/search-evaluation/prz008-tuning-v22-unicode-exact-20260807/`

### Run B — 제한된 완료 행위 정규화 후 통과

`출시한`·`출시했다`와 `배포한`·`배포했다` 계열의 명시적 완료 활용형만 같은 평가
토큰으로 정규화했습니다. 계획·자동화·부정문과 `출시일`·`배포판`·`재배포` 같은 더 긴
파생어는 완료 근거로 인정하지 않는 회귀 테스트를 함께 고정했습니다. 최종 profile
SHA-256은
`100fb6b2f751b9b39334dfe4d654b7a0487fa1089634812036fb750ea4ef06c5`입니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 |
| Precision@5 / Direct Precision@5 | 0.1067 / 0.1067 |
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 오타 질문 top-1 | 2 / 2 |
| 중복 결과 비율 | 0.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 1.0000 / 0.0000 |
| 사용자 반환 수 min / avg / max | 0 / 0.5333 / 1 |
| 후보 수 min / avg / max | 11 / 11.0 / 11 |
| total p50 / p95 | 121ms / 129ms |
| embedding p50 / p95 | 118ms / 125ms |
| DB p50 / p95 | 3ms / 4ms |

직접 근거 8문항과 오타 2문항은 모두 relevance 2를 1위로 반환했고, 역할 변경·부정·없는
기술을 포함한 무근거 7문항은 모두 `NO_EVIDENCE`와 결과 0건을 반환했습니다. raw JSON과
후보 CSV는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v22-unicode-exact-final-20260807/`에 있습니다.
이 결과는 PostgreSQL TUNING 증거이며 OpenSQL 결과나 고정 TEST 결과로 확대하지 않습니다.

### Run C — 제목과 본문 근거 분리 및 Dataset v2.3 통과

근거 존재 여부는 인용되는 `content`만으로 판정하고 문서 제목은 순위 보조에만
사용하도록 교정했습니다. 이에 맞춰 Atlas PDF gold page가 문서 식별자와 완료 사실을
본문만으로 함께 제공하도록 Dataset v2.3을 추가하고, TUNING 15문항만 재실행했습니다.
v2.2 질문 파일은 byte 단위로 보존했으며 고정 TEST는 실행하지 않았습니다. 제품 profile
SHA-256은 `e10d2d923a046855a0df1a8a81ca60b23a28c5cf20b70140ba75153a25892a83`입니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 중복 결과 비율 | 0.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 1.0000 / 0.0000 |
| total p50 / p95 | 125ms / 156ms |
| embedding p50 / p95 | 123ms / 152ms |
| DB p50 / p95 | 3ms / 4ms |

직접 근거 8문항은 모두 relevance 2를 1위로 반환했고 무근거 7문항은 모두
`NO_EVIDENCE`를 반환했습니다. `v2-t-pdf-date`는 Atlas 문서 제목 없이 gold page 2의
본문만으로 `EVIDENCE_FOUND`가 됐습니다. raw 결과는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v23-exact-document-title-final-20260807/`에 있습니다.
이 결과는 PostgreSQL TUNING 증거이며 OpenSQL 또는 고정 TEST 결과가 아닙니다.

### Run D — 질문형 완료 표현과 Unicode 복합어 경계 교정

`배포했습니다?`·`출시했습니다?`처럼 물음표로 끝나는 질문형 문장을 완료 사실로
판정하지 않고, `Kafka랩`처럼 ASCII 식별자가 더 긴 Unicode 복합어 안에 포함된 경우를
정확 일치로 보지 않도록 교정했습니다. 단위·PostgreSQL 회귀를 통과한 뒤 Dataset
v2.3 TUNING 15문항만 재실행했으며 고정 TEST는 실행하지 않았습니다. 제품 profile
SHA-256은 `e72a63fe5eec96640828d402b41fb642c9fbd862049fa65b8d020a32576c79c3`입니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 중복 결과 비율 | 0.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 1.0000 / 0.0000 |
| total p50 / p95 | 121ms / 139ms |
| embedding p50 / p95 | 119ms / 136ms |
| DB p50 / p95 | 3ms / 3ms |

직접 근거 8문항과 오타 2문항은 모두 relevance 2를 1위로 반환했고 무근거
7문항은 모두 `NO_EVIDENCE`를 반환했습니다. raw 결과는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v23-p1-fix-20260807/`에 있습니다. 이 결과는
PostgreSQL TUNING 증거이며 OpenSQL 또는 고정 TEST 결과가 아닙니다.

### Run E — S2B-11 문장 양태 fail-closed 재검증

질문·인용·전언·같은 문장 또는 바로 다음 문장의 부정·철회를 `NO_EVIDENCE`로
판정하는 S2B-11 현재 source에서 Dataset v2.3 TUNING 15문항만 재실행했습니다.
기준 commit은 `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`이고 그 위 미커밋
worktree의 제품 profile SHA-256은
`01eb199af0ff74c427b5178cce5c312d51b85a0ec1745f87ea89804b981194c6`입니다.
Dataset 결합 SHA-256은
`f1bf3cffd1cc51d7c5f972e55fe99a8afe9dce45e403ef742a7e3d0b25bb7f9f`로 유지됐습니다.

| 지표 | TUNING 15문항 |
|---|---:|
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 |
| Direct MRR@5 / @20 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 |
| top-1 직접 근거 / PDF page 정확도 | 1.0000 / 1.0000 |
| 중복 결과 비율 | 0.0000 |
| 무관 질문 거부율 / 근거 질문 오거부율 | 1.0000 / 0.0000 |
| 사용자 반환 수 min / avg / max | 0 / 0.5333 / 1 |
| 후보 수 min / avg / max | 11 / 11 / 11 |
| total p50 / p95 | 117ms / 130ms |
| embedding p50 / p95 | 114ms / 127ms |
| DB p50 / p95 | 3ms / 3ms |

직접 근거 8문항과 오타 2문항은 모두 relevance 2를 1위로 반환했고 무근거 7문항은
모두 `NO_EVIDENCE`였습니다. Docker Desktop 29.6.2, PostgreSQL 16.14·pgvector
0.8.2, Ollama 0.32.6, `bge-m3:latest` digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`에서 실행했습니다.
raw JSON·CSV는 Git에서 제외된
`local/search-evaluation/prz008-tuning-v23-s2b11-20260807/`에 보존했습니다. 고정
TEST와 OpenSQL Gate는 실행하지 않았습니다.

## 2026-07-14 합성 기준선

실제 PostgreSQL 16.14·pgvector와 Ollama `bge-m3`로 실행한 파일럿 결과입니다.

| 지표 | 결과 |
|---|---:|
| Recall@20 | 1.0000 |
| Precision@5 | 0.1933 |
| Direct Precision@5 | 0.1600 |
| Legacy aggregate direct-rank score (PRZ-001 교정 이전 전체 질문 분모, Direct MRR@20과 비교 불가) | 0.6556 |
| nDCG@5 | 0.8543 |
| 중복 결과 비율 | 0.0067 |
| 평균 / p95 검색 지연 | 864.20ms / 999ms |

합성 corpus가 실제로 만든 청크는 14개이므로 Recall@20은 사실상 작은 corpus의 hit-rate 성격이며 운영 규모 회수 성능을 증명하지 않습니다. 이 결과는 평가 파이프라인의 재현 가능한 기준선이지 제품 품질 보증이 아닙니다.

## 2026-07-24 PRZ-001 정합성 교정 후 재측정

Docker Desktop 29.6.2, Testcontainers PostgreSQL 16.14·pgvector, 로컬 Ollama
`bge-m3`로 `./gradlew.bat searchEvaluation --no-daemon`을 실행한 결과입니다.
TUNING과 TEST 사이 양성 근거를 분리한 뒤, Direct MRR@20은 직접 근거가 있는
질문만 분모로 계산합니다. 따라서 아래 TEST 값만 이후 설정 변경의 최종 비교에
사용하고, TUNING 값은 파라미터 탐색에만 사용합니다.

| 구분 | Recall@20 | Precision@5 | Direct MRR@20 | nDCG@5 | 평균 / p95 검색 지연 |
|---|---:|---:|---:|---:|---:|
| 전체 30문항 | 1.0000 | 0.1933 | 0.8551 | 0.8543 | 779.50ms / 1080ms |
| TEST 10문항 | 1.0000 | 0.2000 | 0.7917 | 0.8494 | 737.40ms / 1005ms |
| TUNING 20문항 | 1.0000 | 0.1900 | 0.8889 | 0.8572 | 800.55ms / 1080ms |

이 수치는 합성 파일럿 코퍼스와 현 시점의 로컬 하드웨어·Ollama 실행 상태에
한정됩니다. OpenSQL, OpenProxy, OpenHA 호환성이나 운영 규모의 검색 성능을
증명하지 않습니다.

같은 후보에 `BAAI/bge-reranker-v2-m3`를 적용한 CPU 실험은 상위 5개 직접 근거 품질을 개선하지 못하고 큰 지연·메모리 비용을 보여 운영 도입에서 제외했습니다. 코드가 아니라 조건·수치·거절 근거만 [BGE Reranker 평가와 비채택 결정](../experiments/2026-07-14-bge-reranker-evaluation.md)에 보존합니다.
