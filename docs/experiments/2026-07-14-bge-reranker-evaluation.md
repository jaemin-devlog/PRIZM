# BGE Reranker 평가와 비채택 결정

상태: `EXPERIMENTED_NOT_ADOPTED`

이 문서는 삭제할 실험 브랜치 `experiment/bge-reranker-evaluation`의 기술 판단을 보존한다. 실험 commit은 `617eacf3f045def52241073eaad45b066698075c`이며, 운영 검색 API·SQL·임계값은 변경하지 않았다.

## 질문

Dense 검색의 상위 20개 후보를 `BAAI/bge-reranker-v2-m3` Cross-Encoder로 재정렬하면 상위 5개의 직접 근거 품질이 운영 비용을 정당화할 만큼 개선되는가?

## 조건

- 합성 문서 11개, 실제 생성 청크 14개
- 질문 30개: TUNING 20개, TEST 10개
- Dense: PostgreSQL 16.14, pgvector, Ollama `bge-m3`
- Reranker: Python 3.12, `FlagEmbedding` 1.4.0, `transformers` 4.57.6
- CPU, FP32, batch 8, max length 512, 후보 최대 20개

실제 개인 문서나 운영 규모 corpus가 아닌 작은 합성 데이터이므로 결과를 서비스 전체 성능으로 일반화하지 않는다. 특히 14개 청크에서 계산한 Recall@20은 대규모 후보 회수 성능을 증명하지 않는다.

## 결과

| 지표 | Dense | Reranker | 판정 |
|---|---:|---:|---|
| 전체 Precision@5 | 0.1933 | 0.1867 | 하락 |
| TEST Precision@5 | 0.2000 | 0.2000 | 변화 없음 |
| TEST Direct Precision@5 | 0.1600 | 0.1600 | 변화 없음 |
| TEST MRR@20 | 0.6333 | 0.7500 | 개선 |
| TEST nDCG@5 | 0.8301 | 0.9021 | 개선 |
| false evidence | - | 감소 0건, 증가 1건 | 악화 사례 존재 |

비용 측정:

- 모델 로딩: 2,360.66ms
- 질문당 reranking p50/p95: 40,526.81ms / 51,864.44ms
- Dense 포함 질문당 평균/p95: 43,045.60ms / 54,258ms
- peak RSS: 2,102.25MB

## 결정

운영 코드에 도입하지 않는다.

- 직접 근거를 포함한 TEST Precision@5가 개선되지 않았다.
- 전체 Precision@5는 오히려 하락했다.
- CPU 지연과 메모리 비용이 현재 Reference App의 상호작용 경로에 부적합하다.
- 외부 Python 실행 도구, 모델 배포·캐시와 제3자 라이선스 검토 범위를 늘리지만 현재 품질 이득이 이를 정당화하지 못한다.

따라서 Reranker Java/Python 코드는 `main`에 보존하지 않는다. Dense 평가 하네스, 합성 데이터와 측정 방법은 [검색 품질 평가](../search-evaluation.md)에 유지한다. 이후 재평가는 더 큰 고정 TEST corpus, 명시적인 latency budget과 라이선스 검토를 먼저 정한 별도 spec에서만 수행한다.
