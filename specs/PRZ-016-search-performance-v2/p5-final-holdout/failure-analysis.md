# P5 Holdout Failure Analysis

평가일: 2026-08-14

봉인된 48-query holdout에서 31건이 PASS하고 17건이 FAIL했다. 결과 확인 후 dataset,
ground truth, production 검색 코드는 수정하지 않았다.

## 수동 확정 taxonomy

| Taxonomy | 개수 | Query |
|---|---:|---|
| `EVIDENCE_LOCALIZATION` | 7 | H01, H07, H10, H18, H25, H29, H36 |
| `CANDIDATE_RECALL` | 5 | H03, H08, H09, H11, H12 |
| `NUMERIC_IDENTIFIER` | 1 | H30 |
| `FALSE_POSITIVE` | 3 | N02, N06, N08 |
| `OTHER` | 1 | H23 |

자동 결과 파일은 category 기반으로 H29를 `NUMERIC_IDENTIFIER`, H23을
`EVIDENCE_LOCALIZATION`으로 기록했다. 수동 분석에서는 H29가 같은 ACTIVE 이력서의
수상 일반 문장까지만 찾고 날짜가 있는 chunk 92로 확장하지 못했으므로
`EVIDENCE_LOCALIZATION`으로 분류했다. H23은 반환된 chunk 103 문장이 query에 직접
답하지만 봉인된 acceptable set이 chunk 104만 포함한 GT strictness 사례이므로 `OTHER`다.
평가 결과 파일과 GT는 변경하지 않았다.

## 대표 실패

- H03: FCM 처리 상태를 묻지만 이력서의 일반 Outbox 설명만 반환하고 상태 목록이 있는
  포트폴리오 chunk 99는 candidate에 없었다.
- H09·H11·H12: 동아리 창설, 중앙 해커톤, 창업 아이디어 수상처럼 이력서 활동 영역의
  새로운 자연어 질의가 candidate를 만들지 못했다.
- H07: evidence chunk 101까지 이동했지만 snippet은 `해결 과정 - 담당 구현 범위`라는
  제목만 반환해 실제 구현 범위를 제시하지 못했다.
- H29: 같은 이력서 p2에서 수상 사실은 찾았지만 수상 날짜가 있는 chunk 92로 근거를
  이동하지 못했다.
- H30: `30명`과 `1,100번`이 모두 존재하는 포트폴리오 chunk 96을 찾지 못했다.
- H36: 결과 chunk 96은 4,400회 결과를 포함하지만 evidence는 p1의 일반 측정 한계로
  이동해 결과와 한계를 함께 제시하지 못했다.

## Safety failure

다음 세 negative query에서 문서에 없는 경험을 `EVIDENCE_FOUND`로 반환했다.

| ID | 없는 경험 | 잘못 반환된 근거 | Score |
|---|---|---|---:|
| N02 | OpenTelemetry/Zipkin 분산 추적 | AirConnect 정합성·알림·채팅 개선 | 0.5060 |
| N06 | AWS RDS Multi-AZ | 엑셀 관광지 갱신 | 0.5131 |
| N08 | Spring WebFlux | 일반 비동기 서버 구조 소개 | 0.5433 |

세 개 모두 ACTIVE corpus에 strong identifier가 없지만 semantic dense 결과가 threshold를
넘었다. Negative FPR은 25%이며 PRIZM의 근거 없는 경험 반환 금지 Mandatory Gate를
위반한다.

## 반복·신규 limitation

- 반복: 동일 ACTIVE 문서 안에 직접 근거가 있어도 요약·제목·다른 상세 chunk를 고르는
  Evidence Localization 한계가 다시 나타났다.
- 반복: 여러 page/chunk의 결과와 측정 한계를 함께 요구하는 복합 질의에서 한 근거만
  선택되는 문제가 H36에서 나타났다.
- 반복: 자연어 표현이 활동·수상 영역의 candidate recall로 이어지지 않는 사례가 확인됐다.
- 신규: 기존 guard 목록 밖의 강한 기술 identifier 세 건이 false positive를 만들었다.
- 평가 한계: H23은 사전 acceptable evidence set이 실제 유효 chunk 103을 포함하지 않아
  strict GT 실패가 발생했다. 봉인 후 수정 금지 원칙에 따라 그대로 보존했다.
