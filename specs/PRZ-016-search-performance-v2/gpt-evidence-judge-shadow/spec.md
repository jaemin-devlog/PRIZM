# PRZ-016 GPT-J1 Evidence Judge Shadow Spike Spec

- 상태: `DONE — NO_GO`
- 기준 branch: `PRZ-016-search-performance-v2`
- 시작 source: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- 목적: 현재 P4 retrieval 후보에서 실제 경력 근거가 있는 chunk 하나만 GPT가 고르게 하고,
  서버 측 검증을 통과한 결과만 사용했을 때 P5 negative false positive를 제거할 수 있는지 진단한다.

## 고정 기준선

2026-08-16 Docker Desktop `29.7.2` Linux/x86_64 환경에서 현재 기준선은 다음과 같다.

| 항목 | 결과 |
|---|---|
| PRZ 번호·branch 충돌 해소 | `PASS` |
| 최신 `origin/main` 병합 | `PASS` |
| unit | 521 tests, failure/error 0, conditional skip 16 |
| Docker daemon | `PASS` |
| integration | 113 tests, 104 pass, 8 skip, 1 fail |

integration 1건은 `PRIZM API를 출시한 이력이 있나요?`와 `PRIZM- API를 배포했습니다.`에서
expected `NO_EVIDENCE`, actual `NO_RELEVANT_RESULTS`인 기존 P4 strong-identifier 조기 종료다.
P6에서도 동일하게 재현된 알려진 회귀이며 main 병합·Docker·GPT-J1 변경이 만든 실패가 아니다.
GPT-J1에서는 P4 코드나 이 테스트 기대값을 수정하지 않는다.

## Shadow 데이터 흐름

```text
P5 query
  -> 현재 P4의 owner-scoped ACTIVE dense retrieval Top20
  -> 순위를 보존한 Top10의 extractive snippet만 선택
  -> OpenAI Responses API GPT Evidence Judge
  -> strict JSON decision
  -> Spring Boot evaluation context의 DB 원문 재검증
  -> P4와 P4+GPT 지표 비교
```

P4의 final API 결과는 baseline 지표에 그대로 사용한다. Judge 후보는 P4가 최초 조회하는
동일 dense Top20에서 상위 10개만 사용한다. P3 variant·numeric fallback을 새로 만들거나
lexical/RRF/H2 후보를 섞지 않는다.

## API 입력·출력 계약

OpenAI에는 질문과 다음 필드만 보낸다.

- `chunkId`
- `snippet`: 기존 `SearchSnippetGenerator`가 원문 chunk에서 추출한 최대 3문장

전체 PDF, 전체 chunk, 문서 제목, 사용자·문서·버전 ID, score, distance, 원본 파일은 보내지
않는다. `/v1/responses` 요청에는 `store: false`를 명시하고 File Search, 파일 업로드, tool,
background mode를 사용하지 않는다. 기본 abuse-monitoring 보존과 승인형 Zero Data Retention은
OpenAI 공식 Data Controls의 별도 계정 설정이며, 이 source가 ZDR을 보장한다고 주장하지 않는다.

Structured Outputs는 `text.format.type=json_schema`, `strict=true`,
`additionalProperties=false`로 다음 필드를 모두 요구한다.

- `evidenceFound`: 실제 근거 존재 여부
- `chunkId`: 선택한 제출 후보 ID 또는 `null`
- `evidenceSentence`: snippet에서 그대로 복사한 근거 문장 또는 `null`
- `reason`: 짧은 판정 이유

## 서버 재검증

GPT 출력은 신뢰하지 않는다. `evidenceFound=true` 결과는 모두 다음 조건을 통과해야 한다.

1. `chunkId`가 이번 요청에 실제 제출한 Top10 안에 있다.
2. chunk가 DB에 존재한다.
3. chunk, version, document의 `owner_user_id`가 현재 평가 USER와 모두 같다.
4. document의 `active_version_id`가 chunk version과 같고 version status가 `ACTIVE`다.
5. `evidenceSentence`가 비어 있지 않고 선택 chunk 원문에 exact substring으로 존재한다.

하나라도 실패하면 최종 결과는 evidence 없음으로 fail-closed 처리하고 검증 실패 원인을 별도
diagnostic으로 기록한다. API 오류·refusal·incomplete·schema 오류는 정답 negative로 계산하지
않고 실험 오류로 기록한다.

## 범위

- frozen P5 48개(positive 36, negative 12)만 diagnostic으로 재사용한다.
- P4 baseline과 P4+GPT verified decision만 비교한다.
- query별 후보 수, judge 판정, DB 검증 결과, latency와 token usage를 로컬 결과에 기록한다.
- tracked evidence에는 집계 지표와 query ID만 기록하고 candidate snippet·reason·API key는 남기지 않는다.
- GPT-J1 결과가 좋아도 production에 적용하지 않는다.

## 비범위

- P4·strong-identifier·검색 상태 회귀 수정
- P7 검색 알고리즘, lexical/RRF/H2, threshold·embedding·chunking 변경
- API endpoint, frontend, MCP, Flyway, DB schema/index 변경
- OpenAI File Search, PDF/file upload, 전체 문서 전송
- 새 unseen holdout 작성 또는 최종 채택

## Acceptance criteria

1. `src/main`, Flyway, runtime config, dependency, frontend 변경이 0건이다.
2. P5 frozen dataset·ground truth hash와 현재 P4 source hash가 실행 전후 동일하다.
3. OpenAI 요청에 허용된 최소 필드만 있고 `store=false`와 strict JSON Schema가 적용된다.
4. forged chunk, 다른 owner, inactive version, 원문에 없는 문장은 모두 fail-closed 된다.
5. API 오류와 검증 실패는 negative 정답으로 오계산되지 않는다.
6. P5 48개의 P4와 P4+GPT Top1/Recall@3/5/MRR@5/Negative FPR을 비교한다.
7. Negative FPR이 25%에서 0%가 되고 positive 회귀와 judge 오류가 0일 때만
   `GO_FOR_UNSEEN_HOLDOUT`으로 판정한다.
8. 그 외 실행 완료 결과는 `NO_GO`, API key·환경 부족으로 live 실행하지 못하면
   `NOT_VERIFIED`로 판정한다.
9. 알려진 P4 integration 1건 외 새 unit·integration 실패가 없고 AUDIT blocking finding이 0건이다.

## 공식 OpenAI 근거

- [Structured model outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Data controls in the OpenAI platform](https://developers.openai.com/api/docs/guides/your-data)
