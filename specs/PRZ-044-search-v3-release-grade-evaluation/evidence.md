# PRZ-044 Evidence

## 최종 판정

`PREDICTION_PHASE_BLOCKED`

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

## Preflight

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

## 공식 1회 실행

실행 명령:

```powershell
.\gradlew.bat prz044OfficialPredictions -Pprz044InputZip=C:\Users\USER\Downloads\prizm-release-eval-v1.0.3-input.zip --no-daemon
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
- 공식 task 재실행, completion receipt 생성, branch commit/push는 성공 Gate 미충족으로
  `NOT_RUN`이다.
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

## 다음 경계

같은 `attempt-1`은 재사용할 수 없다. 문서 유형을 명시적으로 매핑하는 새 계약과 새 official
attempt identity를 결과를 보지 않은 상태에서 별도 Phase로 정의해야 한다. 그 전까지 Gold를
요청하거나 채점 단계로 넘어갈 수 없다.
