# PRZ-016 P7-B Independent Generalization Run Contract

- 허가 단계: `ORIENT -> SPEC/PLAN -> VERIFY/RUN -> AUDIT`
- 기준 branch: `PRZ-016-search-performance-v2`
- 기준 HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- 권위 입력: `../p7-cross-document-generalization-v2/`
- v1 입력: 사용 금지, 보존 검증만 수행
- production 수정, 평가 runner 추가·수정, tuning, 재실행, commit, push, PR: 금지

## Pre-run fail-closed Gate

검색 전에 다음을 모두 읽기 전용으로 검증하며 하나라도 다르면 실행하지 않는다.

- v2 manifest 열거 자산 31개: hash·byte size mismatch 0
- active corpus aggregate:
  `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498`
- questions:
  `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868`
- ground truth:
  `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680`
- v1 freeze manifest:
  `0b46f12562050c58c6d7ccefe940378a5c42550192d0f35dffc7e2599eae3b79`
- v1 manifest 열거 자산 27개: hash·byte size mismatch 0
- production search source: Java 30개, aggregate
  `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`

사전 결과: `PASS`.

## Execution environment

- 현재 checkout을 Docker Compose로 build한다.
- 기존 project와 분리한 project `prizm-p7b-20260816`을 사용한다.
- 격리 포트: PostgreSQL `25433`, backend `28081`, frontend `25174`.
- 격리 named volume과 별도 DB 이름 `prizm_p7b_20260816`을 사용한다.
- production profile `source-dedup-evidence-signals-v1`, threshold·Top20·max5,
  `bge-m3`, embedding 1024, chunk length 800, overlap 120을 유지한다.
- 호스트 Ollama `bge-m3:latest` digest:
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`.

## Data and query sequence

1. 서로 다른 일반 `USER` 4명을 signup/login한다.
2. 사용자별 ACTIVE 문서는 PDF 이력서와 TXT 포트폴리오 두 개만 둔다.
3. SYN2-U03 이력서에는 inactive fixture를 V0로 먼저 등록·ACTIVE 처리한다.
4. 같은 문서에 frozen PDF를 V1로 업로드하고 V1이 새 `activeVersionId`가 될 때까지 기다린다.
5. 네 사용자의 8개 현재 문서가 모두 정상 처리되고 ACTIVE인 것을 확인한다.
6. frozen questions 48개를 파일 순서 그대로 해당 owner JWT로 각 1회 실행한다.
7. orchestration은 Ground Truth를 읽지 않으며 원 API 응답과 latency를 먼저
   `raw-results.json`에 고정한다.
8. raw SHA-256을 기록한 뒤에만 frozen Ground Truth와 비교한다.

환경 오류가 아닌 검색 실패에는 재실행·수정·튜닝을 하지 않는다.

## Evaluation and final decision

- Positive 36개: Top1, Recall@3, Recall@5, MRR@5.
- Negative 12개: FPR.
- 사용자별·category별 PASS/FAIL, status distribution, 실패 query를 기록한다.
- owner isolation, `activeVersionId` isolation, SYN2-U03 V0 배제를 판정한다.
- 첫 검색 latency를 cold first로, 이후 47개를 warm 통계로 분리한다.
- P7-B 전용 수치 임계값은 문서화되어 있지 않으므로 새로 만들지 않는다.
- 기존 P5 Mandatory Gate인 Negative false positive 0건은 그대로 대조한다.
- P4 development와 P5 holdout 실측은 비교값이며 새 acceptance threshold가 아니다.
- 최종 `P7-B PASS`는 실행 계약·격리·불변성 통과와 기존 Mandatory Gate 통과를 뜻한다.
  어느 하나라도 실패하면 `P7-B FAIL`로 판정한다.

