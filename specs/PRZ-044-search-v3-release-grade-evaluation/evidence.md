# PRZ-044 Evidence

## 최종 판정

`EVALUATION_INTEGRITY_BLOCKED`

Passage 상한 결함을 고친 `attempt-3`은 실제 PostgreSQL·pgvector·BGE-M3 환경에서 V2/V3
90개 문서를 모두 색인하고 예측을 각각 600건 동결했다. completion receipt까지 생성했으며
owner·lifecycle·artifact 위반은 0건이었다. 다만 정식 PRZ-044 Gold artifact가 제공되지 않아
metric과 Adoption Gate는 실행하지 않았다. 다른 버전의 Gold를 대체 사용하지 않았다.

| 항목 | attempt-3 실제 결과 |
| --- | --- |
| 상태 | `PREDICTIONS_FROZEN_READY_FOR_GOLD` |
| 공식 실행 | `1/1`, 재실행 `0` |
| V2/V3 indexing | `90/90`, `90/90` |
| V2/V3 prediction | `600/600`, `600/600` |
| duplicate/missing query | `0/0` |
| V3 passage bound failure | `0` |
| completion receipt | `PASS` |
| Gold present/accessed | `false / false` |
| metric·Adoption Gate | `NOT_RUN` |

## attempt-3 계약과 실행 근거

### 동결값

| 항목 | 값 |
| --- | --- |
| identity | `PRZ044_ATTEMPT_3_PASSAGE_BOUND_FIX_V1` |
| Search source commit | `25f39795ba7073242c04604185387f05e4b49080` |
| contract SHA-256 | `5fe416a0a65e7b1d03f50e3f810285e6212cddb608554c63bd0d27ac5f1b51b4` |
| INPUT ZIP SHA-256 | `8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0` |
| mapping contract SHA-256 | `55841989c12df285bd2d3d07f2e06b1a61d78b4408fdd4378e94ff19e87d34e2` |
| model | `bge-m3`, dimension `1024`, cosine |
| model digest | `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` |
| runtime | PostgreSQL `16.14`, pgvector `0.8.2` |

Source boundary SHA-256은 V2
`0f7483db24796d915b0f41d85f99823ae1a770c87a7ba091adcec10d04028337`, V3
`2a4828a7f4da884b4be9c38315c958026c560e932e47b61a61fe475c6e9feeb7`, SHARED
`4e3bee47f92d458232c5dfacdfe6de86de423e43f3e07e019300a00519eb3f22`, EVALUATOR
`339d13c27674059c88a92b3b7ba3a069cb06a410e2aa737667b5861fc393d609`다.

Synthetic preflight는 official dataset이나 Gold에 접근하지 않고 실제 PostgreSQL 16.14,
pgvector 0.8.2, BGE-M3, TXT/PDF V2/V3 runtime을 검증했다. contract와 preflight receipt는 같은
SHA-256에 묶였고 focused test는 28건 모두 통과했다.

### Prediction artifact

| artifact | rows | canonical SHA-256 | file/receipt SHA-256 |
| --- | ---: | --- | --- |
| V2 prediction | 600 | `f19f9a354d993a7b0b1f3374e49083665dc2021cd8c620e134a7d2c4059f4fd8` | file `dac6d56148dfdc29182266d31d96122a6e9374b4b0eeb4c7d5a5773ef4aa3578` / receipt `54aa91c6f7da1617495a79bf38400cbc4fd84836c6b1df8a7276f2dbf30cef7f` |
| V3 prediction | 600 | `7c8001fdb60234eebba401e04f3a5b4698c69a2babacceff9bd35255c9e47044` | file `17de236bba8795ab58e5cee0de4099e409cf2f871d429065c1bd840bb0d776c1` / receipt `346eea61dac88bbe63fef3ce2f21691ddf5b6d671d07dc5e177f99292c0f5e1f` |

Attempt marker SHA-256은
`83b4c29bccc04f782826328bf452d1cb54981786860ff1b799a95259780abe70`, completion receipt SHA-256은
`42ff3da843fdae165e2d53752b8e5078ab38b901cc9f073f5416a31c2d26297f`다. Completion receipt는
attempt `3`, V2/V3 row `600/600`, 상태 `PREDICTIONS_FROZEN`을 기록한다. 개별 prediction
receipt의 `attempt=1`은 새 계약에서 허용한 공식 실행 횟수 `1/1`을 뜻하며, attempt identity는
attempt marker SHA와 contract SHA로 묶인다.

### Runtime와 무결성

| 항목 | V2 | V3 |
| --- | ---: | ---: |
| document | 90 | 90 |
| index unit | 120 | 286 |
| vector | 120 | 783 |
| raw vector bytes | 491,520 | 3,207,168 |
| indexing wall time | 6,058.7659 ms | 25,836.7473 ms |
| query execution | 600 | 600 |

두 runtime 모두 owner leakage, inactive-version leakage, lifecycle violation,
duplicate/mixed artifact, cross-parent merge가 0이었다. 추가 model/service도 0이며 실제 BGE-M3를
사용했다. V3는 수정 전 실패했던 8개 문서를 포함해 90개 문서를 모두 색인했고
`ATOMIC_CHILD_EXCEEDS_PASSAGE_BOUND`는 재발하지 않았다.

### Gold와 최종 Gate

Completion receipt와 frozen prediction을 먼저 검증한 뒤에만 정식 Gold artifact의 제공 여부를
확인했다. 제공 위치에는 `prizm-release-eval-v1.0.3-input.zip`만 있고 PRZ-044 Gold package는
없었다. 입력 ZIP의 `sealed/gold.json`은 물리 파일이 아니라 commitment 레코드다. 따라서 Gold
SHA, V2/V3 metric, bootstrap CI, slice 결과와 Adoption Gate는 모두 `NOT_RUN`이다.

| criterion | 결과 |
| --- | --- |
| indexing correctness | `PASS` |
| runtime/reliability·reproducibility | `PASS` |
| evaluation integrity through prediction freeze | `PASS` |
| Gold identity/access | `BLOCKED` — artifact 미제공 |
| retrieval quality·V2 대비 개선 | `NOT_RUN` |
| release readiness | `BLOCKED` |

Search V3를 Production 기본 검색으로 채택할 수 있는지는 이번 결과만으로 판정할 수 없다.

### attempt-3 검증

| 검증 | 결과 |
| --- | --- |
| `prz044Attempt3Preflight` | `PASS`, 1 test, failures/errors/skips `0/0/0` |
| `prz044Attempt3OfficialPredictions` | `PASS`, 1 test, failures/errors/skips `0/0/0` |
| `prz044Focused` | `PASS`, 28 tests, failures/errors/skips `0/0/0` |
| Search V3 PostgreSQL integration/runtime | `PASS`, 46 tests, failures/errors/skips `0/0/0` |
| backend 전체 test | `PASS`, 669 tests, failures/errors/skips `0/0/20` |
| OSS readiness | `PASS`, Markdown 263 files·local links 847·external links 97 |
| `git diff --check` | `PASS` |
| frontend 전체 test | `NOT_RUN` — frontend 변경 0 |
| OpenSQL | `NOT_RUN` |

Production fix 이후 Search V2 source 변경은 0이고 migration, dependency, frontend, MCP 변경도
없다. Prediction 동결 뒤 source·dataset·mapping·contract·scoring 변경은 0이다.

## attempt-2 실패 기록

| 항목 | attempt-2 실제 결과 |
| --- | --- |
| 상태 | `FAILED_ATTEMPT_CONSUMED` |
| 실패 단계 | `RUNTIME_V3` |
| DocumentType mapping | `90/90`, unmapped/ambiguous `0/0` |
| 90-document V2 indexing | `PASS` |
| V2 prediction | `600/600`, frozen `true` |
| V3 prediction | `NOT_CREATED`, frozen `false` |
| V3 passage generation 실패 | 8 documents |
| completion receipt | `NOT_CREATED` |
| Gold present/accessed | `false / false` |
| metric | `NOT_RUN` |

## attempt-2 계약과 실행 근거

Dataset type은 `CAREER_DESCRIPTION` 15개, `PORTFOLIO` 15개, `RESUME` 60개였다. Production
`DocumentType` 12개를 확인한 뒤 다음처럼 명시적으로 매핑했다.

| dataset type | Production type |
| --- | --- |
| `CAREER_DESCRIPTION` | `RESUME` |
| `PORTFOLIO` | `PORTFOLIO` |
| `RESUME` | `RESUME` |

Unknown type은 fallback 없이 거부한다. 매핑 계약 SHA-256은
`55841989c12df285bd2d3d07f2e06b1a61d78b4408fdd4378e94ff19e87d34e2`다.

| artifact | SHA-256 |
| --- | --- |
| attempt-2 contract | `eea133dca032aef1f4ac186cc85bbfe078a9369446e2b10486824f84531e8823` |
| attempt-2 preflight receipt | `3d0be88dbdc6b22399d73745aabb236f90ec812fd40100397fbd28600419205b` |
| attempt-2 marker | `34037157c0bd741c67af1767f94ad3bb576d817d9e0f0b5c24c538db669d13d1` |
| V2 canonical prediction | `ede22eb435c781c827259044017c97f6e364776f6fbf09e4bc28aabe8e028308` |
| V2 prediction file | `bb2d000ee1aa3656317d435262a8bfb5f418b5fc525ad8dfad32f57df2f390b3` |
| attempt-2 failure receipt | `df1eb573c587a6320c91b7e335496661548791164ca7d98b9d49179706b85b7c` |

실패한 V3 job은 `17, 22, 41, 46, 54, 70, 83, 89`다. 공통 원인은
`atomic EvidenceChild exceeds retrieval passage absolute bound`이며, 결과를 본 뒤 구조 정책이나
dataset을 바꾸지 않았다.

V2 frozen receipt는 올바른 attempt marker SHA와 contract SHA에 묶여 있지만 JSON의 `attempt`
필드가 legacy 값 `1`로 기록됐다. 원본 artifact를 고치지 않고 `OPEN_ISSUE`로 보존한다. 이
메타데이터 결함과 V3 미완료 때문에 completion/Gold 단계로 진행할 수 없다.

## attempt-1 보존

Synthetic preflight는 통과했지만 공식 `attempt-1`은 `RUNTIME_V2` 단계에서 실패했다. 입력의
`sourceDocumentType=CAREER_DESCRIPTION`을 Production `DocumentType.valueOf(...)`로 직접
변환했으나, Production enum 12개 값에 해당 이름이 없었다. 첫 문서 적재와 검색 전에 발생한
평가 어댑터 호환성 오류다.

One-shot 계약에 따라 이 시도는 소비됐으며 같은 dataset으로 다시 실행하지 않았다. V2/V3
prediction과 completion receipt는 생성되지 않았다. Gold는 물리적으로 없었고 접근하지 않았다.

| 항목 | 실제 결과 |
| --- | --- |
| 공식 attempt | `1` |
| 상태 | `FAILED_ATTEMPT_CONSUMED` |
| 실패 단계 | `RUNTIME_V2` |
| 90-document indexing | 첫 문서 적재 전 중단 |
| V2/V3 prediction | `NOT_CREATED / NOT_CREATED` |
| 동결 row | `0 / 0` |
| V2/V3 frozen | `false / false` |
| completion receipt | `NOT_CREATED` |
| Gold present/accessed | `false / false` |
| metric·V3 판정 | `NOT_RUN` |

## 기준과 동결값

| 항목 | 값 |
| --- | --- |
| branch 기준 | `PRZ-043-search-v3-release-grade-evaluation@82606d242c2e1077c25ffefaeae98c2cdb51c4b4` |
| Search V2/V3 source 기준 | `refactor/search-v3@0e95472bb68f72accf0d6b2171c22f0719fe6941` |
| 입력 | `prizm-release-eval-v1.0.3-input.zip` |
| ZIP SHA-256 | `8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0` |
| contract SHA-256 | `be03a7edb6d836478b7daaa406b52bf023e67e222be37d020a89f1700bb51913` |
| model | `bge-m3`, dimension `1024` |
| model digest | `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` |

공식 실행을 제어한 source boundary는 다음과 같다.

| source boundary | 파일 수 | SHA-256 |
| --- | ---: | --- |
| V2 | 48 | `0f7483db24796d915b0f41d85f99823ae1a770c87a7ba091adcec10d04028337` |
| V3 | 68 | `20f295e13fa687bb310aaeafbb1180d8909ac38277780f8b2e763d53b429c6b4` |
| SHARED | 281 | `afae48e79dd1281051cafbc839f25332c7311de94f6c30287756348b35c15ced` |
| EVALUATOR | 19 | `8db0a328505dd4d82a1d587b15c2a5b52bd087f7e7eb3de8804ca88e86fc66f3` |

`src/main/**`은 `refactor/search-v3@0e95472bb68f72accf0d6b2171c22f0719fe6941`과 동일했고,
PRZ-044에서 변경하지 않았다.

## Gold-free 입력 감사

ZIP을 추출하지 않고 중앙·로컬 header와 허용된 payload를 읽기 전용으로 검사했다.

- ZIP entry `100`: 문서 `90`, 입력 metadata·validation `10`
- user `75`, document `90`, query `600`, TXT/PDF `45/45`
- profession `15`: 직군별 user `5`, document `6`, query `40`
- unsafe·absolute·traversal·drive·ADS·backslash path `0`
- symlink·encrypted entry `0`, CRC 오류 `0`
- raw·NFC/casefold path 중복 `0`
- user/document/version/path/document SHA/query ID 중복 `0`
- normalized query 중복 `0`
- 실제 Gold·sealed entry `0`
- 질문 허용 필드 외 annotation·Gold 성격 필드 `0`

| 경계 | SHA-256 |
| --- | --- |
| manifest raw | `1c6a363f06765c4715a03e70d2cb70e3f045259d651e6be621b5ddb92b9dede1` |
| manifest canonical | `762b520be8618657f4f57e6829c60b68857c87c86b142d7003a7c2f9156d890a` |
| 실제 입력 payload combined | `8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0` |
| manifest combined commitment | `6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec` |
| 물리적으로 없는 sealed Gold commitment | `d0a507764449315645fabac06d785c1ef8598b1f9ab131674b6e20ad58dda696` |

Manifest combined commitment에는 실제 입력 92개와 물리적으로 없는 `sealed/gold.json`
commitment 레코드가 포함된다. category·answerability 분포는 package claim일 뿐 이번 단계에서
Gold로 검증하지 않았다.

## attempt-1 Preflight

최종 source freeze 기준 synthetic preflight는 공식 dataset에 접근하지 않고 통과했다. 이전
preliminary receipt는 source boundary 확대 전에 만들어져 공식 실행에 사용하지 않았고,
`source-freeze-v2` receipt만 official gate로 사용했다.

| 검사 | 결과 |
| --- | --- |
| focused tests | `PASS`, 24 tests, failures/errors/skips `0/0/0` |
| PostgreSQL | `PASS`, PostgreSQL 16.14 |
| pgvector | `PASS`, 0.8.2 |
| 실제 BGE-M3 | `PASS`, digest/dimension 일치 |
| V2/V3 synthetic runtime | `PASS` |
| TXT/PDF·PDF page provenance | `PASS` |
| writer/hash/disk reload/CREATE_NEW | `PASS` |
| official dataset access | `false` |
| Gold present/accessed | `false / false` |

Preflight receipt SHA-256은
`d60cf3081c03a93645f365b83202b301504bb614f6526ccdf2db0340b9cbc85e`다.

## attempt-1 공식 실행

실행 명령:

```powershell
.\gradlew.bat prz044OfficialPredictions -Pprz044InputZip=<INPUT_ZIP_PATH> --no-daemon
```

테스트 결과는 1 test, failures/errors/skips `1/0/0`이다. `attempt-1`에는 `attempt.json`과
`failure-receipt.json`만 있으며 prediction·completion artifact는 없다.

| artifact | SHA-256 |
| --- | --- |
| attempt | `5630c6d6d2028076b862abdb3e2fa60b2c80e81196cdb71e596cc8e033c7bb74` |
| failure receipt | `b06921eaf1d8f896e2cda2ac68c925b3f3960a118fb14c1a60a3b5189f591551` |
| failure message | `6b34f994a9fbcc9ac0fd09328b82d82c50ca515b964b3c193acdd2d859bedd7f` |

원인은 평가용 runtime이 dataset의 문서 유형 vocabulary와 Production enum의 의미 매핑을
정의하지 않고 이름이 같다고 가정한 것이다. Synthetic preflight fixture에는 Production enum에
없는 문서 유형이 없어 이 호환성 경계를 검출하지 못했다. 결과를 본 뒤 adapter를 수정하거나
같은 dataset을 재실행하지 않았다.

## 보호 경계와 남은 검증

- PRZ-042 `V3_NO_GO / SEED_FINAL_PROTOCOL_RESULT`와 PRZ-043 `EVALUATION_INVALID`는 그대로다.
- `src/main/**`, migration, dependency, frontend, MCP, Search V2/V3 정책 변경은 `0`이다.
- Gold 요청·탐색·open·parse·metric·실패 query 분석은 모두 `NOT_RUN`이다.
- PostgreSQL 성공을 OpenSQL 근거로 사용하지 않았다. `OPENSQL_VALIDATION=NOT_RUN`이다.
- `attempt-1` 당시 official task 재실행과 completion receipt 생성은 `NOT_RUN`이었다.
- 관련 전체 backend/frontend 회귀는 official one-shot 실패 뒤 `NOT_RUN`이다.

사후 감사 결과:

| 검증 | 결과 |
| --- | --- |
| `prz044Focused` | `PASS`, 24 tests, failures/errors/skips `0/0/0` |
| 실패 receipt 원본/문서 사본 byte parity | `PASS`, SHA-256 `b06921eaf1d8f896e2cda2ac68c925b3f3960a118fb14c1a60a3b5189f591551` |
| official attempt directory | `PASS`, attempt `1`, 파일 `2`, prediction/completion 파일 `0` |
| OSS readiness | `PASS`, Markdown 263 files·local links 847, external links 97 |
| `git diff --check` | `PASS` |
| `src/main/**`·migration·frontend diff | `0` |

## attempt-2 최종 검증

| 검증 | 결과 |
| --- | --- |
| DocumentType mapping | `PASS`, documents `90/90`, unmapped/ambiguous `0/0` |
| 실제 mapping/indexing preflight | `PASS`, 1 test, failures/errors/skips `0/0/0` |
| PostgreSQL/pgvector/model | `16.14 / 0.8.2 / bge-m3` |
| `prz044Focused` | `PASS`, 28 tests, failures/errors/skips `0/0/0` |
| attempt-1 byte parity | `PASS` |
| attempt-2 failure/V2 receipt 사본 parity | `PASS` |
| OSS readiness | `PASS`, Markdown 263 files·local links 847, external links 97 |
| `git diff --check` | `PASS` |
| `src/main/**`·migration·frontend diff | `0` |
| backend/frontend 전체 회귀 | `NOT_RUN` |
| Gold·metric | `NOT_RUN` |

## 다음 경계

`attempt-1`과 `attempt-2`는 실패로, `attempt-3`은 prediction 완료로 각각 소비됐다. 같은
attempt를 재실행하지 않는다. 채점은 commitment와 일치하는 정식 PRZ-044 Gold artifact가 별도로
제공될 때만 가능하며, 제공 전에는 `EVALUATION_INTEGRITY_BLOCKED`를 유지한다.
