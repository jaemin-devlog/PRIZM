# PRZ-009 — 경력 키워드 맵 Plan

## 상태와 기준선

`IMPLEMENTED_UNVERIFIED` — 구현·전체 PostgreSQL integration·최종 감사 완료, OpenSQL opt-in `NOT_RUN`

- 기준 branch: `PRZ-009-career-keyword-map`
- 기준 source: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`
- 구현 source: 현재 작업 트리(아직 commit하지 않음)
- PRZ-008은 독립 진행 중이며 검색 판정·응답·평가 source는 수정하지 않는다.

## 구현 배치

0. 기술 사전을 canonical definition과 category 중심으로 정리하고 별칭·버전 표기를 같은
   aggregate로 합치며, summary와 evidence에 category/variants/matchedTerms를 노출한다.
1. owner·active·문서 유형을 SQL에서 제한하는 keyword source repository와 원문
   overlap 조립·결정적 추출기를 구현하고 단위 테스트한다.
2. 키워드 요약·근거 service와 `GET /api/career-keywords` API를 추가하고 인증·입력·
   빈 결과·owner isolation을 controller/integration test로 검증한다.
3. 기존 original endpoint를 PDF/TXT 공통 응답으로 확장하되 thumbnail은 PDF 전용,
   owner 확인과 보안 header는 그대로 유지한다.
4. React route·API client·키워드 맵·오른쪽 근거 목록·TXT/PDF viewer를 추가하고 기존
   화면 style token과 반응형 규칙을 재사용한다.
   category filter와 언급 수/문서 수/균형 점수 전환은 반환된 owner-scoped keyword 목록에
   클라이언트에서 결정적으로 적용한다. 근거는 document/version 기준으로 묶고 PDF URL에는
   page/search fragment를, TXT viewer에는 첫 일치 mark 자동 스크롤을 적용한다.
5. 현재 상태·architecture·roadmap·Registry와 evidence를 실제 검증 결과에 맞춰
   갱신하고 전체 회귀와 최종 diff를 감사한다.

## 변경 경계

- 신규 backend `careerkeyword` controller·DTO·repository·service와 해당 테스트
- `SecurityConfiguration`의 USER 전용 keyword API matcher
- original response/service/controller와 기존 테스트의 TXT 지원 확장
- `frontend/src/api/careerKeywordApi.ts`, `App.tsx`, `styles.css`
- PRZ-009 Spec·Plan·Tasks·Evidence와 현재 상태 문서

수정하지 않는 범위는 Flyway V1~V13, embedding·chunk 생성, processing job 상태·lease,
Career Evidence 검색과 PRZ-008 평가 source다. dependency와 migration은 추가하지 않는다.

## 위험과 대응

- chunk overlap 빈도 중복: source 단위로 정렬한 뒤 가장 긴 동일 suffix/prefix를 한
  번만 조립하고 단위 테스트로 고정한다.
- 일반 단어 과다 노출: 작은 내장 불용어 집합과 최소 token 규칙만 적용한다. 원문에
  없는 keyword를 사전에서 생성하지 않는다.
- 별칭 오병합: 내장 definition에 명시한 별칭만 합치고 추론 기반 유사어 병합은 하지 않는다.
- 균형 점수 불명확성: Spec에 고정한 수식을 UI helper와 동일하게 둔다.
- 원본 위치 이동 한계: PDF built-in viewer가 지원하는 page/search fragment를 사용하고 별도
  PDF 렌더러 dependency는 추가하지 않는다. TXT는 실제 원본 bytes에서 첫 일치를 강조한다.
- 대용량 응답: keyword 60개, evidence 50개, 발췌 길이를 제한한다.
- 소유권 누출: repository SQL에서 세 owner column과 active pointer·status·문서 유형을
  동시에 제한하고 두 사용자 integration test를 추가한다.
- 원본 object URL 누수: 교체·닫기·unmount cleanup을 구현한다.

## 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config --quiet
git diff --check
```

가능하면 OpenSQL opt-in integration과 로컬 브라우저 흐름을 별도로 실행한다. 환경이
없으면 각각 `NOT_RUN`으로 기록한다. 자동 검증 실패, owner 경계 위반, 과거·실패
version 노출 또는 기존 검색 회귀가 있으면 완료하지 않고 IMPLEMENT로 돌아간다.

## 통합과 rollback

현재 승인 범위는 기능 구현·검증·감사까지다. commit·push·PR·merge는 별도 승인 없이
수행하지 않는다. 계획된 변경은 migration과 dependency가 없으므로 PRZ-009 source·문서
diff를 되돌리면 기존 schema와 저장 데이터에 영향 없이 rollback할 수 있다.
