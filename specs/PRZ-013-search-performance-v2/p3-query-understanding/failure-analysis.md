# P3 Failure Analysis

동일 72-query development benchmark에서 전체 실패는 18건에서 14건으로 감소했다.

| 유형 | P2 | P3 | 해석 |
|---|---:|---:|---|
| QUERY_UNDERSTANDING | 5 | 1 | semantic alias와 제한적 multi-query로 4건 감소 |
| CANDIDATE_RECALL | 5 | 3 | A04·E04가 Top5에 진입해 2건 감소 |
| EVIDENCE_LOCALIZATION | 3 | 5 | B05·B12가 문서를 찾은 뒤 정확한 GT 근거를 연결하지 못해 이 유형으로 이동 |
| NUMERIC_IDENTIFIER | 3 | 3 | P1 범위를 그대로 보존 |
| RANKING | 2 | 2 | A05·B01은 P3 비범위로 유지 |

새로 실패한 질의는 없다. B05·B12·C11은 candidate 또는 문서를 찾았으나 정확한 근거
page/chunk를 선택하지 못하는 P4 범위다. 이를 해결하기 위한 selector·Evidence Expansion
변경은 P3에서 수행하지 않았다.

Warm P95는 280.405ms에서 600.227ms로 증가했다. fallback 자체는 전체의 9.72%에만
실행되고 warm 평균 증가는 3.68%였지만, 최대 2개 variant를 순차 embedding한 요청이
상위 latency 구간을 형성했다. P3 결과에서 이 비용을 숨기지 않으며 P4/P5 작업으로
확대하지 않는다.
