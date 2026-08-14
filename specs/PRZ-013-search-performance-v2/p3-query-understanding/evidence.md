# P3 Query Understanding Evidence

> 역사 보존: 초기 `PRZ-013 Evidence`를 PRZ-013 Search Performance V2의 P3
> evidence로 편입했다. 아래 fallback 검증 결과는 변경하지 않았다.

- 판정일: 2026-08-14
- Source commit: — (미커밋 작업 트리)
- 환경: 기존 PostgreSQL·pgvector, Ollama `bge-m3`, 정상 USER의 동일 ACTIVE 이력서·포트폴리오
- Gate: `PASS`

## 구현 근거

- 원본 query embedding과 기존 composite profile을 먼저 실행한다.
- 직접 근거가 없을 때만 표기·조사·질문형 표현을 보정한 variant를 1회 실행한다.
- 경험 요청이나 기술명 단독 질의는 canonical anchor가 원문에 없는 결과를 제외한다.
- repository·SQL·threshold·ranking·P18·PRZ-012 selector는 변경하지 않았다.

## 실제 문서 focused verify

| 질의 | 결과 |
|---|---|
| `AirConnect 프로젝트` | PASS, 3건 |
| `AirConnect에서 뭐했어?` | PASS, AirConnect 문서 4건 |
| `Springboot` | PASS, Spring Boot 근거 1건 |
| `springboot` | PASS, Spring Boot 근거 1건 |
| `Spring Boot` | PASS, Spring Boot 근거 1건 |
| `SpringBoot를 활용한 경험` | PASS, Spring Boot 근거 2건 |
| `Redis를 왜 사용했어?` | PASS, Redis 근거 3건 |
| `배포 경험 알려줘` | PASS, GCP·Docker Compose 배포 근거 1건 |
| `동시성 문제를 어떻게 해결했어?` | PASS, 동시성 검증 근거 4건 |
| `결제 시스템 구현 경험` | PASS, 관련 근거 없음 |
| `Kubernetes 사용 경험` | PASS, 관련 근거 없음 |

## 자동 검증

- `SearchServiceTest`, `NaturalLanguageQueryFallbackTest`, `SearchTokenNormalizerTest`: PASS
- backend-only Compose build와 실제 브라우저 질의: PASS
- 전체 Docker rebuild와 PRZ-008 대규모 평가는 요청 범위에 따라 `NOT_RUN`

## P3 확장: Query Understanding + Limited Multi-Query

원본 검색이 결과를 선택하지 못한 경우에만 최대 2개 variant를 순차 실행한다. variant는
strong identifier와 numeric anchor를 보존하며, 새 기술이나 해결책을 주입하지 않는다.
candidate는 chunk ID로 합치고 동일 chunk의 가장 높은 dense score/distance를 유지한 뒤
기존 P4·P1·P2·PRZ-012 흐름을 그대로 적용한다.

### Focused target

| ID | Before | 생성 variant | After | 판정 |
|---|---|---|---|---|
| B05 | `NO_EVIDENCE` | `실제 운영 환경에 배포해본 경험`; `운영 환경 배포 환경 구축 경험` | 결과는 생성됐으나 고정 GT 근거 rank 없음 | FAIL, Evidence Localization으로 이동 |
| B12 | `NO_RELEVANT_RESULTS` | `실사용 서비스를 운영 경험은?`; `실사용 서비스 운영 경험` | 결과는 생성됐으나 고정 GT 근거 rank 없음 | FAIL, Evidence Localization으로 이동 |
| B14 | `NO_RELEVANT_RESULTS` | `스프레드시트 엑셀에서 기존 데이터 갱신한 적 있어?`; `스프레드시트 엑셀 기존 데이터 갱신 경험` | 정답 rank 1 | PASS |
| C08 | `NO_RELEVANT_RESULTS` | `후보 상태가 변경 여부 확정 전 재검증한 경험은?`; `확정 전 상태 재검증 경험` | 정답 rank 1 | PASS |
| C11 | `NO_RELEVANT_RESULTS` | `업로드 파일을 웹 서버가 직접 서빙 경험은?`; `웹 서버 파일 직접 서빙 경험` | 결과는 생성됐으나 고정 GT 근거 rank 없음 | FAIL, P4 대상 |
| A04 | `NO_EVIDENCE` | `Docker Compose 배포 환경 구축 경험` | 정답 rank 1 | PASS |
| E04 | `NO_EVIDENCE` | `GCP Docker Compose Nginx Spring Boot 서비스를 배포한 경험은?`; `GCP Docker Compose Nginx Spring Boot 서비스 배포 환경 구축 경험은?` | 정답 rank 1 | PASS |

- target 개선: 4/7
- 기존 자연어 9건: 회귀 0
- P1 numeric 8건과 near-miss 3건: 회귀 0
- strong identifier negative 11건: false positive 0
- 정상 USER의 owner/ACTIVE version 격리: PASS

상세 original·variant·merged/final 결과는 [focused-results.json](focused-results.json)에
기록했다.

### 동일 72-query development benchmark

| 지표 | P2 Before | P3 After |
|---|---:|---:|
| Top1 Accuracy | 67.86% | 75.00% |
| Recall@3 | 71.43% | 78.57% |
| Recall@5 | 71.43% | 78.57% |
| MRR@5 | 0.6935 | 0.7649 |
| Negative FPR | 0% | 0% |
| 실패 질의 | 18 | 14 |
| QUERY_UNDERSTANDING | 5 | 1 |
| CANDIDATE_RECALL | 5 | 3 |
| Warm average | 231.193 ms | 239.694 ms |
| Warm P95 | 280.405 ms | 600.227 ms |

A04·B14·C08·E04가 새로 PASS했고 새로 FAIL한 질의는 없다. B05·B12는 candidate를
찾아 failure taxonomy가 QUERY_UNDERSTANDING에서 EVIDENCE_LOCALIZATION으로 이동했다.
전체 결과는 [benchmark-results.json](benchmark-results.json), 잔여 실패 해석은
[failure analysis](failure-analysis.md)에 기록했다.

72건 중 variant fallback은 7건(9.72%)에서 실행됐다. 추가 variant embedding 호출은
11회이며 평균 embedding 호출은 질의당 1.1528회다. warm 평균 증가는 3.68%지만,
2개 variant를 순차 실행한 fallback 질의 때문에 warm P95가 114.06% 증가했다.

### 자동 검증

- query variant, identifier/numeric 보존, candidate merge와 SearchService focused tests: PASS
- 전체 backend test: 506건, failure/error 0, 환경 조건 16건 skip
- backend-only Compose rebuild와 실제 USER focused/benchmark: PASS
- `git diff --check`: PASS

P3 Gate: **PASS**
