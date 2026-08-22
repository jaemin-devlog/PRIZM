# PRZ-016 P16 Literal Candidate Phase A Plan

## 최소 변경

- `src/searchEvaluation/java/com/prizm/search/evaluation/`에 generic literal query expression,
  owner/ACTIVE-scoped literal Top20 repository와 D0/D1 candidate diagnostics를 둔다.
- 같은 source set의 Testcontainers benchmark가 frozen synthetic dataset을 실제 PostgreSQL
  `vector(1024)`에 seed하고 로컬 Ollama `bge-m3`로 chunk/query embedding을 생성한다.
- `src/test/java/com/prizm/search/evaluation/`에는 query boundary와 chunk-ID union 단위 계약만
  추가한다.
- `build.gradle`에는 focused P16 evaluation task 하나만 추가한다.
- P16 디렉터리에 dataset, freeze manifest, 실행 result와 evidence를 둔다.

## 데이터 흐름

1. frozen dataset hash를 확인하고 synthetic owner/document/version/chunk를 seed한다.
2. query를 BGE-M3로 한 번 embedding하고 Production repository로 Dense Top20을 조회한다.
3. dataset 이름을 모르는 generic short literal expression parser가 query 전체 표현을
   case-insensitive exact-boundary pattern으로 만든다.
4. literal repository가 같은 owner의 현재 `ACTIVE` version 원문에서 Top20을 조회하며 각
   result의 기존 dense distance/score를 계산한다.
5. D1은 Dense를 먼저 보존하고 literal을 chunk ID로 deduplicate한 뒤 기존 dense score
   내림차순으로 정렬한다. RRF·boost·score replacement는 없다.
6. D0와 D1 candidate에 같은 `ShortGeneralExactTokenRescueProfile`/`CompositeSearchProfile`
   filter를 적용한다. Phase A 판정은 final 품질이 아니라 candidate 복구까지만 사용한다.
7. 기존 `ProductionSearchDecisionTracer`와 실제 `SearchService` 응답으로 D0 parity를 확인한다.

## 안전·일반화

- literal parser는 1~5개의 문자·숫자·일반 identifier punctuation으로 이뤄진 짧은 query
  표현만 허용하며 dataset identifier 상수나 기술명 목록을 갖지 않는다.
- exact boundary의 identifier 문자는 Unicode letter/digit와 `_+#./-`로 정의해 FooEngineX와
  Bundle을 배제한다. multi-word 내부 whitespace만 하나 이상으로 정규화한다.
- SQL parameter binding을 사용하고 owner를 document/version/chunk에 반복 적용한다.
- rollback은 새 P16 evaluation/spec 파일과 Gradle task만 제거하면 되며 Production rollback은 없다.

## 검증 명령

```powershell
.\gradlew.bat test --tests com.prizm.search.evaluation.PhaseALiteralQueryExpressionTest --no-daemon
.\gradlew.bat p16LiteralCandidatePhaseA --no-daemon --rerun-tasks
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
git diff --check
git status --short --branch
```

필수 환경은 Testcontainers PostgreSQL+pgvector와 로컬 Ollama `bge-m3`다. 환경이 없으면
focused evaluation은 `NOT_RUN`이며 P16을 PASS로 판정하지 않는다. OpenSQL/OpenProxy는 이번
Phase의 검증 환경이 아니며 PostgreSQL 결과를 그 근거로 확대하지 않는다.

## 중단 조건

- Production source hash/diff, owner/ACTIVE isolation, boundary 또는 D0 parity가 깨지면 즉시
  `FAIL`로 중단한다.
- literal-only positive가 0개이면 구현 성공으로 보지 않고 dataset의 일반적인 stress 강도를
  검토하되 실행 결과에 맞춘 identifier-specific 코드 변경은 금지한다.
- Phase B, Production 적용, commit/push/PR은 결과와 무관하게 시작하지 않는다.
