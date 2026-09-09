# PRIZM Career Evidence Synthetic Dataset v1.0

이 디렉터리는 PRIZM 검색 방식을 비교하려고 만든 완전 합성 페이지 텍스트 벤치마크다.

## 구성

- 엔터티·식별자·source fact가 겹치지 않는 A/B/C 코호트
- 합성 문서 114개
- 질문 300개: TUNING 180, 동결 TEST 120
- 근거 있음 150개, 근거 없음 150개
- 프로젝트명·식별자·ASCII 기술명·수치를 뺀 한국어 PARAPHRASE 질문 60개
- 스키마 버전 2
- 운영 청킹 기준: 800자, 겹침 120자
- 질문의 정답 표시는 `questions.jsonl` 한 곳에만 둔다.

평가 범주별 수량은 `freeze-manifest.json`에 기록한다. 현재 범주는 ABSENT_ENTITY 21, ALTERED_FACT 21, COLLABORATION 11, DIRECT_EVIDENCE 9, EXACT_VALUE 15, NEAR_TOPIC_NO_EVIDENCE 33, NO_EVIDENCE 27, NO_SEARCHABLE_DOCUMENTS 15, OVERLAP_DUPLICATE 8, OWNER_BOUNDARY 18, PARAPHRASE 60, PDF_EVIDENCE 21, PROBLEM_SOLVING 11, TECHNICAL_EXPERIENCE 15, VERSION_BOUNDARY 15다.

## 합성 벤치마크의 출처와 작성 범위

모든 인물, 조직, 고용주, 프로젝트, 사건, 날짜, 성과, 수치, 식별자와 경력 주장은 허구다.
PRIZM 사용자 업로드, 실제 이력서·포트폴리오·채용공고, 데이터베이스 덤프, 개인 통신은
사용하지 않았다. ESCO, O*NET, SkillSpan을 비롯한 제3자 데이터셋 문구를 복사·번역·각색하지
않았다. 실제 기술명은 명목상 참조일 뿐 제휴, 후원, 보증을 뜻하지 않는다.

Codex는 허구 사실 행렬과 결정적 템플릿 작성을 도왔다. 실제 사용자 문서나 이름이 있는
외부 데이터셋을 입력으로 주지 않았다. 모델 학습 데이터의 출처는 확인할 수 없으므로 잠재적
학습 데이터 중복이 전혀 없다고 보증하지 않는다.

사실 행렬의 사용자 대면 한국어 문장은 `humanize-korean` 기본 강도로 교정·윤문했다.
고유명사, 수치, 날짜, 단위, 식별자, 전문 용어, 부정 범위와 qrel은 보호 요소로 고정했다.
결정적으로 생성되는 문맥 문장은 별도의 정적 검사를 거쳤다. 문장을 자연스럽게 다듬으려고
새 경력 사실이나 성과를 추가하지 않았다.

## 파일

- `fact-matrix.json`: 허구 사실과 질문의 작성 원본
- `corpus.json`: 기존 PRIZM 평가 로더가 읽는 문서와 페이지 텍스트
- `questions.jsonl`: 질문, 관련도, 분할, 소유자·버전 시나리오와 PDF 정답 페이지
- `freeze-manifest.json`: 출처, 수량과 SHA-256

`fact-matrix.json`, README와 manifest는 검색 말뭉치에 넣지 않는다. 평가 실행기는
`corpus.json`과 `questions.jsonl`만 읽는다.

## 사용 범위와 한계

이 자료는 검색 후보 회수, 순위, 근거 위치, 소유자·버전 경계와 근거 없음 응답을 평가하는 용도다.
채용, 사람 순위화 또는 고용 의사결정에 사용하지 않는다.

PDF fixture는 실제 PDF 파일이 아니라 추출 결과를 흉내 낸 페이지별 텍스트다. PDF 생성,
파서, 글꼴, 메타데이터, 업로드 제한과 실제 v0→v1 활성 전환은 검증하지 않는다. 스키마 v2는
여러 절을 모두 만족해야 하는 AND 정답, 정확한 문자 범위와 여러 정답 페이지를 표현하지 못한다.
운영 청킹 경계 재현을 위해 일부 생성 문맥 끝의 남는 길이는 공백으로 채운다.

현재 평가용 PostgreSQL FTS는 자연어 질문 전체를 `simple` 구성의 AND 조건으로 바꾼다.
한국어 조사와 질문 종결어까지 모두 일치해야 하므로 이 데이터셋과의 실행 적합성은 검증되지
않았다. Hybrid/RRF 비교 전에 FTS 질의 구성 방식을 별도로 사전 등록하고 후보 회수가 실제로
발생하는지 확인해야 한다. 이번 생성 단계에서 PostgreSQL 검색은 실행하지 않았다.

같은 사실에서 만든 직접·바꿔 묻기·정확값 질문을 서로 독립된 표본으로 과장하지 않는다. 결과를
비교할 때 질문 수뿐 아니라 사실·질문 그룹 수와 정확값 오류 수도 함께 보고한다.
일부 B/C 사실은 같은 검색 위험을 다른 기술과 맥락으로 바꾼 평행 시나리오군이다. 세 코호트를
통계적으로 독립한 표본이라고 가정하지 않고, 전체 결과와 함께 코호트별·평행 시나리오군별 결과를
확인한다.

## 생성과 확인

```powershell
node scripts/generate-prizm-search-evaluation-dataset.mjs --check
```

TEST 검색 평가는 검색 방식을 사전 등록한 뒤 명시적 허용 플래그를 켜고 한 번만 실행한다.
데이터셋을 만드는 이번 단계에서는 TEST 검색 평가를 실행하지 않았다.
