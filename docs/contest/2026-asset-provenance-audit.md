# PRIZM 2026 저장소 자산 provenance 감사

> 현재 source-only 배포의 종합 결론은
> [2026 compliance](2026-compliance.md)에 있다. 이 문서는 fixture, sample,
> image, 문서와 binary 후보의 상세 provenance 근거를 보존한다.

## 문서 상태

| 항목 | 값 |
|---|---|
| PRZ 작업 | [`PRZ-002-open-source-readiness`](../../specs/PRZ-002-open-source-readiness/spec.md) |
| 범위 | Git에 추적되는 fixture·sample·이미지·문서·binary와 로컬 자산 참조 |
| 기준 commit | `a5f5cd53525d1e759d558ce0c09e2b1cc42544a1` |
| 최초 감사일 / 현재 상태 기준일 | 2026-07-24 / 2026-07-30 |
| 상태 | `COMPLETE` — 자산 provenance blocker 없음 |
| PRIZM outgoing license | `Apache-2.0` root `LICENSE`와 source-only `NOTICE` 적용 완료 |
| 배포 경계 | source·문서·실행 설정만 배포 |
| 법적 성격 | 기술적 provenance 감사이며 법률 자문이 아님 |

이 감사는 `LICENSE`와 `NOTICE`를 만들기 전에 저장소가 실제로 함께
배포하는 비코드 자산의 출처와 재배포 권리를 확인하기 위해 시작했다.
Apache-2.0 선택과
검색 평가 fixture·초기 PRIZM 골격·Mermaid diagram의 공개 허락은
2026-07-24 사용자가 확인했다. 같은 날 외부 Toss reference에서 유래한
frontend token을 제거하고 독립 PRIZM color·spacing·radius 체계로
교체했다. 당시 남아 있던 비자산 license Gate도 이후 해결되어 현재
source-only 범위에는 root `LICENSE`와 `NOTICE`가 적용돼 있다. 미래 binary·
image·model 재배포 조건은 현재 source-only 배포를 막지 않으며 해당 산출물을
실제로 배포하기 전 별도 Gate로 다시 확인한다.

## 상태 정의

| 상태 | 의미 |
|---|---|
| `VERIFIED_DIRECT` | `Jaemin Jeong`이 PRIZM을 위해 직접 작성했고 공개할 권리가 확인됨 |
| `VERIFIED_EXTERNAL` | upstream·version·license와 재배포 조건이 확인됨 |
| `NOT_DISTRIBUTED` | Git 미추적 또는 ignore되어 현재 PRIZM source 배포물에 포함되지 않음 |
| `UNKNOWN` | 출처, 원저작자 또는 공개·재배포 권리 중 하나 이상을 확인하지 못함 |
| `BLOCKED` | 재배포 제한·license 충돌 또는 확인 불가 때문에 배포와 고지 작업을 중단해야 함 |

자산별 표에는 위 상태 중 하나만 기록한다. 현재 배포 대상 source에서 외부
design reference의 token 값과 이름을 제거했으며, 새 PRIZM token은
Career Vault의 차분한 문서 관리 화면을 위해 독립적으로 선택했다.
따라서 `BLOCKED_EXTERNAL_DESIGN_RIGHTS`는 해소됐다.

## 감사 방법

2026-07-24 현재 다음을 읽기 전용으로 대조했다.

1. `git ls-files`의 274개 추적 파일 전체를 확장자, 크기와 NUL byte
   signature로 검사했다.
2. `frontend/public`, `frontend/assets`, test resource, `README.md`,
   `docs/`, Dockerfile, Compose와 GitHub Actions의 로컬 파일 참조를
   검색했다.
3. Markdown image 문법, HTML `img`, CSS `url()`, favicon·font·media와
   PDF·Office·archive·model 확장자를 검색했다.
4. 검색 평가 fixture의 내용, SHA-256, record 수, 민감정보 형태와
   `git log --follow` 이력을 확인했다.
5. copyright, SPDX, copied/adapted/derived/source와 한글 출처 표기를
   추적 파일에서 검색했다.
6. `.gitignore`, `git check-ignore`와 `git ls-files -- <path>`로
   local output, upload, model cache와 build output이 배포물에 포함되지
   않는지 확인했다.
7. 초기 repository commit의 전체 message·file set과 frontend color
   token을 외부 generator·template·design system 후보와 대조했다.
8. 사용자에게 직접 제작·외부 참고 범위를 확인하고, 답변에서 확인된
   Spec Kit·Robo Architect·oh-my-design upstream과 공개 license 표기를
   2026-07-24 다시 대조했다.

Git commit author는 파일을 저장소에 추가한 사람을 보여줄 뿐, 문구가
처음부터 독립 제작됐다는 법적 증명은 아니다. 따라서 fixture의 합성
표기나 commit author만으로 `VERIFIED_DIRECT`를 부여하지 않았다. 외부
코드와의 의미 유사성 전수 비교도 이 감사의 증거로 가장하지 않는다.

## 추적 파일 범위

| 조사 범위 | 추적 파일 수 | 결과 |
|---|---:|---|
| 전체 저장소 | 274 | 누락된 추적 파일 0 |
| test·evaluation resource | 5 | 설정 YAML 3개, 검색 평가 fixture 2개 |
| 검색 평가 fixture | 2 | 아래 provenance 확인 대상 |
| NUL byte가 있는 binary | 1 | `gradle-wrapper.jar` |
| image·PDF·Office·font·media·model | 1 | 직접 제작 SVG 1개, 그 외 파일 없음 |
| frontend `public`·`assets` | 1 | 로그인 배경 SVG 1개 |
| docs의 비-Markdown 첨부 | 0 | 로컬 image·attachment 없음 |
| Markdown image 참조 | 0 | 저장소 이미지에 대한 참조 없음 |
| inline Mermaid block | 5 | Markdown source로 직접 렌더링되며 별도 binary 자산은 아님 |

`frontend/src/App.tsx`의 `<img>`는 Git에 저장한 그림을 가리키지 않고,
인증된 API 응답으로 실행 중 생성한 문서 thumbnail object URL을 표시한다.
따라서 별도 배포 자산으로 분류하지 않는다. 반면 로그인 배경 SVG는 아래
`ASSET-LOGIN-EVIDENCE-VISUAL`로 별도 추적한다. test resource의 나머지 세
파일도 실행 설정이며 TXT/PDF 원문 sample은 추적되지 않는다.

## 배포 자산 판정

| ID | 경로·파일 수 | 유형 | Git 이력·출처 근거 | license·재배포 조건 | 상태 | 후속 조치 |
|---|---|---|---|---|---|---|
| `ASSET-SEARCH-CORPUS` | [`corpus.json`](../../src/test/resources/search-evaluation/sample/corpus.json), 1개 | 합성 검색 corpus: 11개 virtual document, 13 pages, 27 evidence anchors | SHA-256 `0E9981C4BFCEA39ED7DFCA3F156EC9BCBF7E425DE9F29E966BB5F6D7D0494D86`; 최초 commit `46e24eff85f055740f7397190bb1e6266aa742a8`, 확장 commit `347d54db406f0377bb443ae7ff42aaf2bfa8e704`; 사용자는 2026-07-24 모든 문장·수치·프로젝트명을 본인과 Codex가 PRIZM용으로 새로 작성했고 외부 자료를 복사·각색하지 않았다고 확인함 | 개인정보·기밀이 없는 합성 자료이며 사용자가 PRIZM과 함께 Apache-2.0으로 공개하는 데 동의함 | `VERIFIED_DIRECT` | Apache-2.0 적용 시 fixture도 동일 배포 범위로 명시 |
| `ASSET-SEARCH-QUESTIONS` | [`questions.jsonl`](../../src/test/resources/search-evaluation/sample/questions.jsonl), 1개 | 검색 질문·정답 label: 30문항, TUNING 20·TEST 10 | SHA-256 `A42A356628E577722BC62A65C8157EC79A9917CA033C6B6CBD1D7BEE80FA07B5`; 최초·확장 commit은 위와 같고 정합성 commit은 `36c8610aabbe5753a823c38f5456d7a5348a8b9e`; 사용자는 corpus와 같은 직접·Codex 보조 제작 범위를 확인함 | 제3자 benchmark·서비스 문구 비파생 및 Apache-2.0 공개 동의를 사용자 확인으로 기록함 | `VERIFIED_DIRECT` | corpus와 같은 배포·고지 범위 유지 |
| `ASSET-LOGIN-EVIDENCE-VISUAL` | [`career-evidence-network.svg`](../../frontend/src/assets/career-evidence-network.svg), 1개 | 로그인 소개 영역의 장식용 문서·근거 연결 SVG | SHA-256 `73286127EB42CDA3C7E667CD3A3711D9DD8ED9E6A5CA8FDD79584599DE03F5A5`; 2026-07-29 사용자의 로그인 배경 요청에 따라 PRIZM용으로 새로 작성했다. 외부 사진·일러스트·아이콘·폰트·로고·CDN을 포함하지 않는다. | Apache-2.0 PRIZM source와 함께 배포 가능한 직접 제작 SVG다. 장식용으로만 표시하며 사용자 문서·실제 경력 정보는 포함하지 않는다. | `VERIFIED_DIRECT` | 외부 시각 자산을 추가하거나 SVG에 third-party 요소를 넣으면 provenance를 다시 감사 |
| `ASSET-GRADLE-WRAPPER` | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, 4개 | 외부 build bootstrap | wrapper는 Gradle `9.5.1`을 지정한다. JAR SHA-256 `497C8C2A7E5031F6AA847F88104AA80A93532EC32EE17BDB8D1D2F67A194A9C7`가 [Gradle 공식 checksum](https://gradle.org/release-checksums/)과 일치하고, JAR manifest·내장 LICENSE 및 두 script header가 `Apache-2.0`을 명시한다. | Apache License 2.0 원문과 저작권 고지 보존. source와 함께 재배포 가능 | `VERIFIED_EXTERNAL` | `NOTICE` coverage에서 포함 여부를 재확인. distribution ZIP checksum pin은 별도 공급망 backlog |

`src/searchEvaluation/resources/application-search-evaluation.yml`과 일반
test application YAML은 외부 data asset이 아니라 PRIZM 실행 설정이다.
직접 작성 source·설정의 outgoing license는 사용자 승인된 Apache-2.0을
따르되, 이 표의 외부·fixture 자산 판정을 대신하지 않는다.

## 저장소에 포함되지 않는 자산

아래 항목은 추적 파일 수가 모두 0이며 현재 source 배포물에는 포함되지
않는다. 사용자 파일을 열거나 삭제하지 않고 Git 경계만 확인했다.

| 범위 | Git·ignore 근거 | 상태 |
|---|---|---|
| `local/`, `outputs/` | 루트 `.gitignore`로 제외, 추적 0 | `NOT_DISTRIBUTED` |
| `build/`, `frontend/dist/`, `frontend/node_modules/` | build·dependency output ignore, 추적 0 | `NOT_DISTRIBUTED` |
| `.env`, `var/`, `uploads/`, `models/` | secret·runtime·upload·model 경로 ignore, 추적 0 | `NOT_DISTRIBUTED` |
| reranker `.venv/`, `model-cache/`, `__pycache__/` | tool별 ignore, 추적 0 | `NOT_DISTRIBUTED` |
| 대회 운영 규정 PDF, 결과보고서 ZIP, 공지·OT·design reference 이미지 | [`2026-source-register.md`](2026-source-register.md)와 이 감사에 URL·hash·최소 설명만 기록하고 원문은 커밋하지 않음 | `NOT_DISTRIBUTED` |
| PostgreSQL·pgvector image, Ollama binary, `bge-m3` weights·cache | 사용자가 upstream에서 직접 설치·pull하며 Git 추적 0 | `NOT_DISTRIBUTED` |

Dockerfile과 Compose는 source 설정만 포함한다. container image, JAR,
frontend `dist`, DB volume, 실제 업로드 문서와 thumbnail은 현재 PRIZM
release artifact로 게시하지 않는다. 다운로드되는 외부 component의
license·version은 [`2026-license-audit.md`](2026-license-audit.md)에
별도로 유지한다.

## 외부 코드·template·문구 표기 검사

- copyright·SPDX header가 확인된 외부 bundled source는 Gradle Wrapper
  script와 JAR뿐이며 위 `VERIFIED_EXTERNAL` 판정에 포함했다.
- 저장소의 다른 source·frontend·문서에는 copied from, adapted from,
  derived from 또는 외부 code·asset attribution header가 없다. 그러나
  header 부재가 독립 제작을 증명하지는 않는다.
- GitHub Spec Kit는 spec·plan·tasks 흐름, uEngine Robo Architect는 spec별
  보조 문서 구조의 개념적 참고 자료다. 사용자는 화면으로 본 repository
  구조를 Codex가 PRIZM에 맞게 정리하도록 지시했으며 code·문구·asset을
  복사했다고 진술하지 않았다. 추적 파일 검색과 upstream template 대조에서도
  비자명한 동일 문구를 찾지 못했다.
- Robo Architect에는 `evidence.md`가 없다. PRIZM의 evidence 분리는 해당
  repository에서 가져온 것이 아니라 PRIZM이 독자적으로 적용한 구조다.
- Gamium은 향후 공개 저장소 정리 때 참고할 계획일 뿐 현재 code·설정·문구에
  반영하지 않았다는 사용자 확인과 문자열 검사 결과를 기록했다.
- 이 결과는 semantic plagiarism 검사나 제3자 권리 보증이 아니다. 향후
  외부 snippet·template·asset을 도입하면 upstream과 license를 추가하고
  이 감사를 다시 수행해야 한다.

### 확인되지 않은 source·template 후보

| ID | 범위 | 확인 근거 | 현재 판단 | 상태 | 후속 조치 |
|---|---|---|---|---|---|
| `SOURCE-INITIAL-ZIP` | 초기 commit `b633f4693f4a1605fa71b3d9aed3958bf9dc37d9`에서 추가된 51개 path | commit message는 `Spring Boot 백엔드 골격`과 `React 프런트엔드 골격`을 명시한다. 사용자는 2026-07-24 본인이 Codex에 명령해 PRIZM용으로 만들었고 Gamium code·설정·문구는 반영하지 않았다고 확인함 | Gradle Wrapper는 위 외부 component로 별도 분리하고, 나머지 PRIZM 고유 source·설정은 사용자 지휘 아래 Codex 보조로 작성한 범위로 확인 | `VERIFIED_DIRECT` | 향후 generator나 외부 template을 새로 사용하면 exact upstream·version·license를 다시 등록 |
| `SOURCE-UI-DESIGN-TOKENS` | [`frontend/src/styles.css`](../../frontend/src/styles.css)의 color·spacing·radius·state token, 1개 file | 역사적으로 사용자가 [oh-my-design Toss reference](https://oh-my-design.kr/design-systems/toss)에서 색상을 가져왔다고 확인했다. 2026-07-24 구현에서 기존 9개 color와 spacing `4/6/8/16/24/32px`, button·card radius `4/8/10/14/16px`, legacy action token을 frontend source에서 제거했다. 새 `--prizm-*` palette와 `5/7/11/17/25/34px` spacing, `5/9/12/15/18/22px` radius는 Career Vault의 문서·근거 관리 맥락과 접근성 대비를 기준으로 독립 선택했다. | 외부 reference 값·문구·asset·code를 현재 frontend 구현에 포함하지 않는다. primary·surface·border·foreground·body·muted·danger·success·focus-visible과 카드·모달·badge·상태 UI가 새 semantic token을 사용한다. 역사적 출처 URL은 감사 증거로만 유지한다. | `VERIFIED_DIRECT` | 별도 권리 조치 없음. 향후 외부 UI kit·token을 도입하면 이 Gate를 다시 연다. |
| `ASSET-MERMAID-DIAGRAMS` | `docs/archive/PRIZM_최종_기획안.md`의 inline Mermaid block, 5개 | 별도 image file 없이 Markdown 안에 diagram source로 저장됨. 사용자는 2026-07-24 본인이 Codex에 PRIZM용으로 새로 만들도록 지시했다고 확인함 | 외부 diagram·template 비파생 및 Apache-2.0 공개 권리를 사용자 확인으로 기록 | `VERIFIED_DIRECT` | 별도 조치 없음 |

### 외부 설계·프로세스 참고

| ID | Upstream | 참고 범위 | license·권리 판정 | 배포 여부 | 상태 |
|---|---|---|---|---|---|
| `REF-SPEC-KIT` | [GitHub Spec Kit](https://github.com/github/spec-kit), 확인 commit `4d3a4281bc63bd2af9f2515bb1036fc38da1294e` | `spec` → `plan` → `tasks` 흐름과 폴더 배치 아이디어 | upstream software는 [MIT](https://github.com/github/spec-kit/blob/main/LICENSE). PRIZM은 원문·template·code를 배포하지 않고 일반적인 작업 흐름만 독자 문서로 작성 | 외부 파일 0개 | `VERIFIED_EXTERNAL` |
| `REF-ROBO-ARCHITECT` | [uEngine Robo Architect repository](https://github.com/uengine-oss/robo-architect), 확인 commit `bb4b24addc301062e06f983e25c8e5f76877b9cd`와 [제품 소개](https://www.uengine.org/contents/roboarchitect.html) | spec별 `checklists`·`contracts`·`manual`·data model·quickstart 등 보조 문서 배치 아이디어. `evidence.md`는 upstream에 없음 | root license 파일과 GitHub license metadata는 없고 README에 `MIT License` 문구만 있어 future copy의 license는 미확정. PRIZM은 screenshot·원문·code·asset을 저장하지 않고 개념만 독자 작성 | 외부 파일 0개 | `NOT_DISTRIBUTED` |
| `REF-GAMIUM` | Gamium 계열 repository | 향후 README·기여·GitHub 정리 참고 계획 | 현재 반영·복사된 code·설정·문구·asset 없음. 실제 참고 구현 시 exact repository·commit·license를 별도 재감사 | 외부 파일 0개 | `NOT_DISTRIBUTED` |
| `REF-OH-MY-DESIGN` | [oh-my-design](https://github.com/kwakseongjae/oh-my-design) Toss reference | 과거 frontend token 출처를 설명하는 감사 이력 | tool 자체는 MIT이나 company reference는 각 회사 소유라고 upstream이 분리한다. PRIZM은 재사용 권리를 가정하지 않고 해당 token을 제거했다. | 외부 파일·token·문구 0개. URL은 역사적 감사 증거로만 유지 | `NOT_DISTRIBUTED` |

Pretendard는 [`frontend/src/styles.css`](../../frontend/src/styles.css)의
CSS family 이름으로만 사용한다. 공식 upstream은
[orioncactus/pretendard](https://github.com/orioncactus/pretendard)이고
[공식 LICENSE](https://github.com/orioncactus/pretendard/blob/main/LICENSE)는
SIL Open Font License 1.1, SPDX
[`OFL-1.1`](https://spdx.org/licenses/OFL-1.1.html)이며 Reserved Font Name은
`Pretendard`다. font binary·npm font package·`@font-face`·`@import`·CDN
요청은 추가하지 않았고, 설치되지 않은 환경은 `system-ui`,
`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `sans-serif` 순으로
대체한다. 따라서 현재 source-only 배포물에는 Font Software가 포함되지 않아
`NOT_DISTRIBUTED`다. 향후 font file을 묶으면 exact version·hash와 OFL 원문·
저작권 고지 전달을 다시 감사한다. test PDF도 Java test가 PDFBox로 메모리
또는 임시 디렉터리에 생성하며 실제 PDF·font sample은 Git에 없다.

## 사용자 확인 결과와 남은 Gate

2026-07-24 사용자는 다음을 확인했다.

1. 검색 corpus·질문·수치·프로젝트명은 본인과 Codex가 PRIZM용으로 새로
   작성했으며 외부 dataset·실제 보고서·책·강의·블로그·타인 문서를
   복사하거나 각색하지 않았다. 두 fixture의 Apache-2.0 공개에 동의했다.
2. 초기 repository ZIP과 PRIZM 골격은 사용자가 Codex에 지시해 만들었고
   Gamium은 아직 code·설정·문구에 반영하지 않았다.
3. Spec Kit와 Robo Architect repository 화면은 spec 문서·폴더 정리
   방식을 참고하도록 Codex에 제공했으며, 외부 원문·code·asset 자체는
   저장소에 넣지 않았다. upstream 대조 결과 PRIZM `evidence.md` 분리는
   Robo Architect 복제가 아닌 독자 적용이다.
4. archive 기획안의 Mermaid diagram 5개는 사용자가 Codex에 PRIZM용으로
   새로 만들도록 지시했다.
5. frontend color token은 oh-my-design의 Toss reference에서 가져왔다.
   기술 대조에서는 같은 commit의 spacing·button radius도 해당 reference와
   다수 일치해 교체 범위를 design token 전체로 정했고, 2026-07-24 해당
   token을 독립 PRIZM 체계로 교체했다.

1~4번은 직접 제작 및 design reference 경계를 해소한다. 5번은 재사용 허용을
추정하지 않고 외부 token 자체를 제거해 해결했다.

- `frontend/src`에는 `toss`, `Toss Product Sans`, `Tossface`,
  `oh-my-design` 문자열과 기존 9개 color 값이 없다.
- layout spacing과 component radius는 새 `--prizm-space-*`,
  `--prizm-radius-*` 체계를 사용한다. 같은 숫자가 typography나 고정
  geometry에 나타나는 경우는 spacing/radius token 재사용으로 보지 않는다.
- sRGB 상대 휘도로 계산한 주요 대비는 다음과 같다.

| 조합 | 대비 |
|---|---:|
| foreground / canvas | 16.34:1 |
| body / canvas | 8.17:1 |
| muted / surface | 4.92:1 |
| primary / on-primary | 6.68:1 |
| primary-text / primary-soft | 7.50:1 |
| danger / surface-raised | 5.80:1 |
| danger / danger-soft | 5.31:1 |
| success / success-soft | 5.44:1 |
| focus-visible / canvas | 5.16:1 |
| focus-visible / surface | 4.79:1 |
| focus-visible / primary-soft | 4.46:1 |

일반 텍스트 조합은 4.5:1 이상이며 focus-visible indicator도 비텍스트
UI 기준 3:1 이상이다.

## Gate 결론

- `VERIFIED_DIRECT`: fixture 2개 + Mermaid block 5개 + 초기 PRIZM source family + UI design token 1개 source family
- `VERIFIED_EXTERNAL`: Gradle Wrapper 4개 + Spec Kit process reference 1개
- `NOT_DISTRIBUTED`: 추적 파일 0개, 위 제외 경로·외부 artifact 군
- `UNKNOWN`: 이번 자산 provenance 범위 0개
- `BLOCKED`: 이번 자산 provenance 범위 0개
- 전체 Gate: `COMPLETE`

Git에 배포되는 이미지·PDF·문서 sample·font·model은 없고 Gradle Wrapper의
외부 provenance와 직접 작성 fixture·Mermaid·초기 source 경계도 확인됐다.
외부 reference에서 유래한 color·spacing·radius token도 제거하고 독립
PRIZM token으로 교체했다. 이에 따라 `BLOCKED_EXTERNAL_DESIGN_RIGHTS`는
해소됐다. 이후 전체 [`2026-license-audit.md`](2026-license-audit.md)의
현재 source-only Gate도 통과해 root `LICENSE`와 `NOTICE`가 적용됐다.
미래 binary·image·model 재배포의 미해결 조건은 별도 release Gate로 남는다.
