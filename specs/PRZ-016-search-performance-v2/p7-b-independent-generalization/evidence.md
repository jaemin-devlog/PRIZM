# PRZ-016 P7-B Independent Generalization Evidence

- 최종 상태: `P7-B FAIL`
- 실행일: 2026-08-16 (Asia/Seoul)
- branch: `PRZ-016-search-performance-v2`
- HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- 실행 입력: `../p7-cross-document-generalization-v2/`만 사용
- v1: 보존 검증만 수행, 검색 입력으로 사용하지 않음
- production source 변경: 0
- commit/push/PR: `NOT_RUN`

## Final decision

P7-B의 실행 유효성, owner 격리, 현재 active pointer 격리, inactive V0 배제와
source/freeze 불변성은 모두 통과했다. 그러나 Negative 12개 중 5개가 근거를
반환해 FPR이 `41.67%`였다. 기존 P5에서 문서화한 Mandatory Gate인 Negative false
positive 0건을 통과하지 못했으므로 최종 판정은 `P7-B FAIL`이다.

P7-B 전용 Search Quality 수치 기준은 문서에 없다. 따라서 Positive 지표는
`OBSERVED`로 보고하며 새 threshold를 만들지 않는다.

## Pre-run fail-closed verification

검색 실행 전에 read-only로 다음을 확인했다.

| 대상 | 기대값 | 실제 결과 |
|---|---|---|
| v2 frozen assets | 31개 | 31개, hash·size mismatch 0 |
| active corpus aggregate | `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498` | 일치 |
| questions | `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868` | 일치 |
| ground truth | `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680` | 일치 |
| v1 freeze manifest | `0b46f12562050c58c6d7ccefe940378a5c42550192d0f35dffc7e2599eae3b79` | 일치 |
| v1 frozen assets | 27개 | 27개, hash·size mismatch 0 |
| production search source | 30개, `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31` | 일치 |

선행 Gate: `PASS`.

## Environment and execution

- Docker Engine `29.7.2`, Linux `x86_64`.
- 격리 Compose project: `prizm-p7b-20260816`.
- 격리 DB: `prizm_p7b_20260816`, host port `25433`.
- backend `28081`, frontend `25174`; 기존 project의 `15433/18081/15174`와 분리.
- 현재 checkout의 Docker backend image build: `PASS` (`bootJar` 성공).
- `docker compose config --quiet`: `PASS`.
- backend health `UP`, frontend HTTP `200`.
- production profile: `source-dedup-evidence-signals-v1`.
- Ollama: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`.
- embedding 1024, chunk length 800, overlap 120 유지.
- 합성 `USER` 4명은 서로 다른 owner ID `1, 2, 3, 4`를 받았다.
- owner별 문서 수는 모두 2개다. DB에는 문서 8개, version 9개, chunk 31개가 있다.
- ProcessingJob 9개는 모두 `COMPLETED`; backend error/exception log line은 0개다.

SYN2-U03 resume V0는 document `1`, version `1`로 먼저 처리해 당시
`active_version_id=1`임을 확인했다. 같은 문서에 V1 PDF를 version `2`로 올린 뒤
최종 pointer가 `active_version_id=2`가 됐다. DB의 immutable V0 row 상태 값은
`ACTIVE`로 보존되지만 현재 검색 pointer는 V1만 가리킨다.

### Environment recovery

첫 orchestration 명령은 host PATH에서 `node`를 찾지 못해 프로세스 시작 전에
종료됐다. 이때 raw 파일은 없었고 API·검색 호출도 시작되지 않았다. 번들 Node의
절대 경로만 적용하고 동일 script/hash/input으로 다시 시작했다. 검색 결과 실패를
고치기 위한 재실행은 없었다.

## Raw freeze

일회성 orchestration은 저장소 밖에만 두었고 corpus manifest와 questions만 읽었다.
Ground Truth는 raw 고정 전까지 읽지 않았다.

- script:
  `C:\Users\정재민\AppData\Local\Temp\prizm-p7b-20260816\orchestrate-p7b.mjs`
- script SHA-256:
  `db774737912f22e1ed322bc2a26942831ab10fb3887a99a543777a23551b22fd`
- 실행 명령:
  `C:\Users\정재민\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe <script> C:\Users\정재민\Project\PRIZM http://127.0.0.1:28081 4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- raw: `raw-results.json`, 225,850 bytes, 48개 HTTP `200` 응답
- raw SHA-256:
  `defc5e35dbf26f48a640f3df673e2247c14437b0cb65e8c8c05a0bd3b6e2cb2e`
- raw freeze 이후 hash 변경: 0
- raw 내 access token, password, synthetic email 기록: 0

raw가 고정된 뒤 frozen Ground Truth를 처음 읽고 `evaluated-results.json`을 만들었다.

## Overall metrics

| 항목 | 결과 |
|---|---:|
| 전체 | 48 |
| Positive / Negative | 36 / 12 |
| Top5 기준 PASS / FAIL | 28 / 20 |
| Top1 | 12/36 = `33.33%` |
| Recall@3 | 21/36 = `58.33%` |
| Recall@5 | 21/36 = `58.33%` |
| MRR@5 | `0.4491` |
| Negative false positives | 5/12 |
| Negative FPR | `41.67%` |

## Per-user results

| User | PASS / FAIL | Top1 | Recall@3 | Recall@5 | MRR@5 | Negative FPR |
|---|---:|---:|---:|---:|---:|---:|
| SYN2-U01 | 8 / 4 | 44.44% | 77.78% | 77.78% | 0.5926 | 66.67% |
| SYN2-U02 | 6 / 6 | 11.11% | 33.33% | 33.33% | 0.2222 | 0.00% |
| SYN2-U03 | 9 / 3 | 33.33% | 77.78% | 77.78% | 0.5370 | 33.33% |
| SYN2-U04 | 5 / 7 | 44.44% | 44.44% | 44.44% | 0.4444 | 66.67% |

각 user의 분모는 Positive 9개, Negative 3개다.

## Per-category results

| Category | PASS / FAIL | Top1 | Recall@3 | Recall@5 | MRR@5 | FPR |
|---|---:|---:|---:|---:|---:|---:|
| Direct Experience | 4 / 4 | 25.00% | 50.00% | 50.00% | 0.3750 | — |
| Natural Variation | 6 / 2 | 62.50% | 75.00% | 75.00% | 0.6667 | — |
| Indirect Problem | 5 / 3 | 25.00% | 62.50% | 62.50% | 0.4375 | — |
| Numeric / Identifier | 1 / 3 | 25.00% | 25.00% | 25.00% | 0.2500 | — |
| Complex Natural Language | 5 / 3 | 25.00% | 62.50% | 62.50% | 0.4167 | — |
| Negative | 7 / 5 | — | — | — | — | 41.67% |

## Status and latency

| Response state | Count |
|---|---:|
| `EVIDENCE_FOUND` | 38 |
| `NO_RELEVANT_RESULTS` | 10 |
| `NO_EVIDENCE` | 0 |
| `NO_SEARCHABLE_DOCUMENTS` | 0 |

| Latency | ms |
|---|---:|
| Cold first | 280.136 |
| Warm count | 47 |
| Warm average | 262.728 |
| Warm median | 255.150 |
| Warm P95 | 307.525 |
| Warm max | 493.799 |
| 전체 average / median / P95 / max | 263.090 / 255.314 / 307.525 / 493.799 |

## Failure queries

| ID | Category | Failure | Query |
|---|---|---|---|
| V2-U01-D01 | Direct | CANDIDATE_RECALL | 여러 출하 거점의 건설 자재 배차와 계근 전표를 처리하는 서비스를 맡아 본 이력이 있어? |
| V2-U01-NV02 | Natural Variation | EVIDENCE_LOCALIZATION | 거점과 자재 종류에 따라 필요한 작업자만 배차 메시지를 받도록 나눈 경험이 있나요? |
| V2-U01-N01 | Negative | FALSE_POSITIVE | Solace PubSub+를 QuarryFlow의 실제 메시지 인프라로 전환했나요? |
| V2-U01-N03 | Negative | FALSE_POSITIVE | 동일 견적 replay의 세 자릿수 지연 값이 340ms였다고 볼 자료가 있어? |
| V2-U02-D02 | Direct | EVIDENCE_LOCALIZATION | Airflow로 조정하고 Spark로 계산하는 영상 데이터 파이프라인을 운영했어? |
| V2-U02-NV02 | Natural Variation | CANDIDATE_RECALL | 같은 촬영 장면의 보정판과 단순 재전송을 구별해서 적재한 기준이 문서에 있나요? |
| V2-U02-IP02 | Indirect Problem | EVIDENCE_LOCALIZATION | 깨진 위성 파일이 모자이크와 공개 목록에 섞이기 전에 차단한 경험이 있어? |
| V2-U02-NI01 | Numeric / Identifier | NUMERIC_IDENTIFIER | 하루 6.4TB 영상 처리를 138분에서 41분으로 단축했다는 근거가 있나요? |
| V2-U02-CN01 | Complex Natural Language | CANDIDATE_RECALL | 960만 건의 과거 scene을 다시 넣으면서 중단 지점부터 이어가고 동일 revision 중복도 막은 방법을 보여줘. |
| V2-U02-CN02 | Complex Natural Language | CANDIDATE_RECALL | 입력 무결성 확인을 통과하지 못한 영상은 계산 자원과 공개 catalog 양쪽에서 제외되도록 한 근거가 있어? |
| V2-U03-IP01 | Indirect Problem | EVIDENCE_LOCALIZATION | 리더 프로세스가 멈춘 뒤 새 운행 허가를 낼 수 있을 때까지의 시간을 줄였나요? |
| V2-U03-NI01 | Numeric / Identifier | NUMERIC_IDENTIFIER | 12,800대의 가상 열차를 16개 구역에서 실행한 검증 기록이 있나요? |
| V2-U03-N01 | Negative | FALSE_POSITIVE | 현재 ACTIVE 경력에서 etcd lease로 RailPulse 제어기 선출을 운영했나요? |
| V2-U04-D01 | Direct | EVIDENCE_LOCALIZATION | 음악 저작권 자료를 받는 NestJS 수집 API를 개발한 적이 있어? |
| V2-U04-D02 | Direct | CANDIDATE_RECALL | 음악 저작물 230만 건을 이름·권리자·지역 조건으로 조회할 색인을 만든 기록이 있어? |
| V2-U04-IP01 | Indirect Problem | CANDIDATE_RECALL | 두 공급자가 같은 지역 권리를 모두 자기 것이라고 주장할 때 자동으로 한쪽을 택하지 않게 만든 경험이 있어? |
| V2-U04-NI01 | Numeric / Identifier | NUMERIC_IDENTIFIER | 검색 P95를 1.6초에서 240밀리초로 낮춘 성과가 적혀 있나요? |
| V2-U04-CN02 | Complex Natural Language | CANDIDATE_RECALL | 월별 정산 파일이 어느 시점의 권리 상태로 계산됐는지 나중에 재현할 수 있게 했나요? |
| V2-U04-N01 | Negative | FALSE_POSITIVE | ScoreRights의 production 검색을 Typesense로 운영했나요? |
| V2-U04-N02 | Negative | FALSE_POSITIVE | 저작권 정산 이력을 블록체인 원장에 기록하도록 구현했어? |

Failure taxonomy는 CANDIDATE_RECALL 7, EVIDENCE_LOCALIZATION 5,
NUMERIC_IDENTIFIER 3, FALSE_POSITIVE 5다.

SYN2-U03 inactive-only 질문 `V2-U03-N01`은 V0 version `1`을 반환하지 않았다.
대신 현재 ACTIVE portfolio version `7`의 “초기 resume v0에는 ... prototype이
기록돼 있었다”는 부정·과거 범위 문장을 근거로 반환했다. 따라서 V0 isolation은
통과했지만 Negative 판정은 false positive다.

## Isolation

| Contract | Result |
|---|---|
| 사용자마다 서로 다른 owner | `PASS` |
| owner별 문서 2개 | `PASS` |
| 응답의 다른 owner document | 0, `PASS` |
| 응답 version과 document `active_version_id` 불일치 | 0, `PASS` |
| SYN2-U03 V0 version `1` 응답 | 0, `PASS` |
| SYN2-U03 최종 active pointer | V1 version `2`, `PASS` |

## Three-scenario comparison

| Scenario | Top1 | Negative FPR | Interpretation |
|---|---:|---:|---|
| 내 문서 + 개발 질문 (P4) | 82.14% | 0% | 기존 development 실측 |
| 내 문서 + unseen 질문 (P5) | 50.00% | 25.00% | 기존 holdout 실측, FAIL |
| unseen 사용자 문서 + unseen 질문 (P7-B) | 33.33% | 41.67% | 이번 독립 실측, OBSERVED |

P7-B는 P4보다 Top1 `48.81pp` 낮고, P5보다 `16.67pp` 낮다. Negative FPR은
P4보다 `41.67pp`, P5보다 `16.67pp` 높다. 이 차이는 관측 비교이며 새 gate가 아니다.

## Gate and audit

| 항목 | 판정 |
|---|---|
| Pre-run frozen/production fail-closed Gate | `PASS` |
| 48개 단일 실행과 raw-first freeze | `PASS` |
| owner/ACTIVE/inactive-version isolation | `PASS` |
| P5 Mandatory Gate: Negative false positive 0 | `FAIL` — 5건 |
| P7-B numeric Search Quality threshold | `NOT_DOCUMENTED` |
| P7-B quality metrics | `OBSERVED` |
| v2 31개 frozen asset post-run hash | `PASS`, mismatch 0 |
| v1 27개 frozen asset post-run hash | `PASS`, mismatch 0 |
| production source before/after | `PASS`, 30개 aggregate 동일 |
| raw freeze hash | `PASS`, 변경 0 |
| `git diff --check` | `PASS` |
| AUDIT blocking findings | 1 — Mandatory Gate 실패 |

전체 backend unit/integration과 frontend lint/build는 production code 변경이 없는
독립 환경 실행 범위여서 `NOT_RUN`이다. 현재 checkout Docker image의 `bootJar` build와
실제 API/DB/Ollama 흐름은 실행했다.

감사 뒤 `prizm-p7b-20260816` 컨테이너 3개, 전용 network, 합성 DB/runtime volume
2개와 임시 orchestration 디렉터리를 제거했다. 기존
`prizm-clean-clone-tcjhymbhn3vu` project에는 손대지 않았다. raw와 평가·감사 산출물은
이 디렉터리에 보존한다.

## Artifacts

- `run-contract.md`: 실행 전 계약
- `raw-results.json`: GT 비교 전 고정한 원 응답과 latency
- `raw-freeze.json`: raw hash, script hash와 명령
- `evaluated-results.json`: query별 GT 비교와 집계
- `audit.json`: frozen/source/raw/git 불변성 감사
