# PRZ-016 P17: PRIZM 전용 검색 평가 데이터셋

- 상태: `IMPLEMENTED — VERIFY_AND_AUDIT_PASS, INTEGRATION_NOT_RUN`
- 범위: synthetic dataset, evaluation harness, test, documentation only
- 기준 source: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`

## 문제와 목표

기존 v2.3 평가셋은 합성 13문서·25문항이고 split별 검색 가능 corpus가 작아 Dense Top20이
사실상 전체 corpus가 되는 경우가 있다. 이 상태로는 Dense, PostgreSQL FTS+Dense RRF,
Cross-encoder Reranker와 chunking profile의 차이를 안정적으로 비교하기 어렵다.

P17은 PRIZM Career Evidence 검색의 실제 위험을 직접 측정하는 완전 합성 benchmark를 만든다.
제품 검색 정책을 바꾸거나 어느 방식이 최선이라고 결론 내리는 단계가 아니라, 동일한 고정
질문과 qrel로 네 방식을 비교할 수 있는 재현 가능한 측정 기반을 만드는 단계다.
300문항 v1은 paired regression과 후보 선별용이다. 같은 fact의 query variant는 독립 표본처럼
과장하지 않고 fact/question group 단위 결과도 함께 본다. 작은 slice의 비율만으로 통계적 절대
우위를 선언하지 않으며 최종 보고에는 문항 수와 exact error count를 비율과 함께 표시한다.

## Dataset contract

- dataset ID는 `prizm-career-evidence-synthetic-v1.0`이고 기존 schema version 2를 사용한다.
- project, identifier와 source fact ID가 겹치지 않는 A/B/C 세 cohort로 114문서·300문항을
  구성한다. 통계적으로 독립한 표본이라는 뜻은 아니다.
- 총 300문항을 `TUNING 180`과 동결 `TEST 120`으로 분리한다.
- TUNING은 evidence 90·no-evidence 90, TEST는 evidence 60·no-evidence 60으로 고정한다.
- corpus는 두 split에서 persona, project, identifier, source fact, evidence group과 question group을
  공유하지 않는다.
- 각 split의 primary owner `ACTIVE` corpus는 Production `800/120` chunking으로 최소 150개
  chunk를 만들어 Dense Top20과 evaluation Top30이 전체 corpus를 대신하지 않게 한다.
- 12개 `DocumentType`, TXT와 페이지별 PDF extracted-text fixture, relevance `0/1/2`, PDF
  `goldPage`, overlap duplicate를 포함한다.
- 실제 PDF binary가 아니라 평가 runner가 seed하는 페이지별 추출 텍스트다. PDF parser,
  font, metadata, upload 제한 또는 ingestion 정확도의 근거로 사용하지 않는다.

## 평가 차원

1. Dense 의미 검색: 자연어 바꿔쓰기, 한영 혼용과 표현 순서 변경. 이 중 60개
   `PARAPHRASE`는 project·identifier·ASCII 기술명·수치를 뺀 한국어 질문이다.
2. FTS/Hybrid: 정확한 기술명·프로젝트 식별자·버전·날짜·숫자.
3. Reranker: 같은 사실의 짧은 요약 근거 `relevance=1`과 구체적인 프로젝트 근거
   `relevance=2`를 함께 제공한다.
4. Chunking: Production overlap에 같은 anchor가 두 chunk에 들어가는 문서와 section/paragraph
   문맥 문서를 제공한다.
5. Rejection·격리: absent entity, altered fact, theory/plan/negation, other owner only,
   past version only, no searchable documents를 포함한다.

schema v2가 표현하지 못하는 여러 절의 AND 조합, canonical character span과 실제 v0→v1
activation 전환은 P17 qrel의 범위가 아니다. 해당 주장은 이 데이터셋으로 검증하지 않는다.

## 출처·개인정보·사용 제한

- 모든 인물, 조직, 고용주, 프로젝트, 사건, 날짜, 성과, 수치와 경력 주장은 허구다.
- PRIZM 사용자 업로드, 실제 이력서·포트폴리오·채용공고, DB dump와 개인 통신을 사용하지
  않는다.
- ESCO, O*NET, SkillSpan 또는 다른 제3자 dataset 문구를 복사·번역·각색하지 않는다.
- 실제 기술명은 사실을 지칭하는 명목적 참조일 뿐 제휴나 보증을 의미하지 않는다.
- Codex는 허구 fact matrix와 결정적 template 작성 보조에만 사용한다. 모델 학습 데이터의
  출처를 알 수 없으므로 잠재적 학습 데이터 중복이 0이라고 보증하지 않는다.
- 이 benchmark는 retrieval, evidence location, ownership/version boundary와 no-evidence
  평가용이다. 채용, 사람 순위화 또는 고용 의사결정에 사용하지 않는다.
- 일부 B/C fact는 같은 위험을 다른 기술과 맥락으로 바꾼 평행 시나리오군이다. cohort와
  같은 fact의 query variant를 통계적으로 독립한 표본으로 세지 않고, cohort·시나리오군·
  fact/question group 단위 결과와 exact error count를 함께 본다.

## 보존 계약과 비범위

- `src/main`, Flyway, Production SQL/API/config, frontend와 기존 동결 v2/v2.3 자산을 수정하지
  않는다.
- `bge-m3`, embedding dimension, owner/ACTIVE 경계, 단일·최대 5결과 제품 계약을 바꾸지
  않는다.
- 현재 Dense, Hybrid/RRF, Reranker 또는 Semantic Chunking의 우열 판정과 Production 적용은
  수행하지 않는다.
- OpenSQL/OpenProxy, 실제 사용자 corpus와 실제 PDF ingestion 검증은 수행하지 않는다.
- commit, push, PR과 merge는 사용자 승인 범위가 아니다.

## Acceptance criteria

1. 기존 loader가 변경 없는 schema v2로 dataset을 읽고 정확히 114문서·300문항을 반환한다.
2. TUNING `180=90 positive+90 no-evidence`, TEST `120=60 positive+60 no-evidence`와 모든 15개
   평가 category가 자동 검증된다. 세 cohort는 각각 기존 100문항 설계와 같은 비율을 유지한다.
3. split별 primary-owner ACTIVE Production chunk가 150개 이상이고 모든 corpus 문서가 해당
   split 질문에서 참조된다.
4. 모든 positive anchor는 선언한 fixture와 한 page에 존재하고 PDF positive의 `goldPage`가
   일치한다. no-evidence에는 positive relevance가 없다.
5. split 간 project, fact, evidence/group, question group, exact query와 normalized document
   문장 재사용이 0건이다.
6. query 전체가 corpus 원문의 NFKC-normalized substring인 사례가 0건이다.
7. graded relevance pair, exact identifier/number/date, paraphrase, PDF, overlap, altered/absent,
   owner/version/no-searchable 시나리오가 TUNING과 TEST에 모두 존재한다.
8. dataset card와 manifest가 synthetic-only, real-user-data false, third-party-text false,
   deterministic-template, Apache-2.0과 사용 제한을 기록한다.
9. 이메일·전화번호·주소·URL·로컬 경로·credential 형태의 finding이 0건이다.
10. fact matrix, corpus와 questions의 SHA-256이 freeze manifest 및 hard-coded unit gate와
    일치하고 generator `--check`가 byte drift를 거부한다.
11. 새 TEST split은 기존 명시적 allow 환경변수가 `true`일 때만 evaluation runner가 허용한다.
12. focused dataset tests, 전체 backend unit, OSS readiness와 `git diff --check`가 통과하고
    AUDIT blocking finding이 0건이다.
13. cohort 간 project, identifier, source fact, authored anchor와 query의 raw/NFKC 중복이
    0건이며, fact matrix의 authored anchor/query에는 이름만 바꾼 문장 복제가 없다.
14. `$humanize-korean` 기본 강도로 fact·수치·날짜·단위·식별자·부정과 qrel을 보호한 문장별
    교정·윤문을 수행하고 보호 요소 변경 0건을 확인한다.

## 현재 구현 및 검증 상태

- `prizm-career-evidence-synthetic-v1.0`, schema version 2로 project·identifier·source fact가
  겹치지 않는 A/B/C cohort의 114문서·300문항을 생성했다. TUNING은
  `180=90 positive+90 no-evidence`, 동결 TEST는 `120=60 positive+60 no-evidence`다.
- corpus는 12개 `DocumentType`과 schema v2의 15개 평가 category를 모두 포함한다.
- positive 중 60개는 project·identifier·ASCII 기술명·수치를 뺀 한국어 `PARAPHRASE`다.
- 고정 fact matrix를 corpus와 questions로 렌더링하는 결정적 generator, dataset card와
  SHA-256 freeze manifest를 추가했다. TEST 질문 파일의 hard-coded SHA-256 gate는
  `c07e105023287663542601133e82fbfa78f3a341e75efc326989a0aadcc63600`다.
- `$humanize-korean` 기본 강도 로컬 작성 감사에서 문장 항목 576개와 보호 요소 619개를 대조했다.
  보호 요소 변경과 새 경력 사실 추가는 모두 0건으로 `PASS`다.
- generator drift 검사, dataset·loader·selector focused test 24건, 전체 backend unit,
  SBOM·OSS readiness와 최종 diff 검사는 모두 `PASS`다.
- privacy·license·split leakage·qrel·claim scope 독립 AUDIT의 blocking finding은 0건이다.
- 동결 TEST retrieval, 실제 PDF ingestion, OpenSQL/OpenProxy와 Production Dense·Hybrid/RRF·
  Reranker·chunking 비교는 모두 `NOT_RUN`이다. focused 구조 검증 결과를 검색 방식의 성능
  판정으로 확대하지 않는다.
- 현재 evaluation-only FTS는 자연어 질문 전체를 PostgreSQL `simple` AND 조건으로 바꾼다.
  이 데이터셋과의 실행 적합성은 `NOT_VERIFIED`이며 Hybrid/RRF 비교 전에 lexical candidate
  회수와 질의 구성 방식을 별도로 사전 등록해야 한다.
- commit, push, PR과 merge는 `NOT_RUN`이다.
