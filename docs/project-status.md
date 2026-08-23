# PRIZM 현재 구현 현황

> 현재 상태 기준일: 2026-08-24
>
> PRZ-011 검증 source commit: `fbb3481626a3cba6f36f070845ffae502511569e`
>
> PRZ-011 GitHub 통합: [PR #41](https://github.com/jaemin-devlog/PRIZM/pull/41),
> merge commit `e46d55f0c889bf570fa6fd796cb780b738ab75d7`
>
> PRZ-010 검증 source commit: `26c546b16eb9ea42d98460dd6e5aa0bf0752212a`
>
> PRZ-010 GitHub 통합: [PR #39](https://github.com/jaemin-devlog/PRIZM/pull/39),
> merge commit `d616dac95b5d29c6f45babb51435d95d20f39fa8`
>
> PRZ-010 GitHub Actions: CI run `31510048694`, OSS Readiness run
> `31510048703` 모두 `success`; review 제출 0건으로 `REVIEW_NOT_AVAILABLE_SOLO`
>
> PRZ-007 검증 source commit: `2b8b60069c37eea91e485bffe2c54e62cd2117ab`
>
> PRZ-007 GitHub 통합: [PR #33](https://github.com/jaemin-devlog/PRIZM/pull/33),
> merge commit `f1fb34145a7cb4a8d5025365764c11dac4516527`
>
> PRZ-005 검증 source commit: `eab32c870f06237d37048b6b8de1287e5e18ae66`
>
> PRZ-005 GitHub 통합: [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26),
> merge commit `6dc982227bafe94f0879c22bf4381a6e47adf925`
>
> PRZ-004 GitHub 통합 merge commit: `1f9a5ad964778a2e72de9949a0fadae042008392`
>
> 전체 clean-clone 검증 source commit: `25d09e9eee9837cf4a63d7461699825ff22743e2`
>
> 최종 Windows·Linux 경로 교정·CI source commit:
> `aff3e87a9a912e44fcf217291a45328cf451cfc9`
>
> 기존 구현 기준선: `PRZ-000 AS_BUILT_BASELINE`
>
> 최종 판단 기준: 소스 코드(source code), Flyway 마이그레이션(migration),
> 실행 가능한 테스트(test)
>
> PRZ-004는 자동 검증, 두 clean clone, 독립 감사와 GitHub PR #25 CI를 통과해
> `main`에 통합됐습니다.
>
> PRZ-005는 실제 OpenSQL 직접 `5432` API·브라우저 E2E, 두 사용자 격리,
> OpenSQL opt-in integration test와 전체 회귀를 통과했습니다. PR #26의 GitHub
> check 6건이 성공했고 review는 없어 `REVIEW_NOT_AVAILABLE_SOLO`로 기록합니다.

## 한눈에 보는 현재 상태

| 구분 | 현재 상태 |
|---|---|
| 현재 제품 | Spring Boot와 React Career Vault로 구현한 자동화된 AI 문서 관리 플랫폼 |
| 구현됨 | 자체 호스팅 회원가입, 로그인, 사용자별 문서 격리, TXT/PDF 업로드, 변경 불가능한 버전 관리, ChangeLog 기반 비동기 색인·복구, Ollama 자동 임베딩, pgvector 근거 검색, Career Vault 문서 관리, 읽기 전용 MCP Career Evidence 검색 |
| 현재 단계 | 소스 전용 공개 준비, clean-clone, 실제 OpenSQL direct 기준선과 PRZ-013 OpenProxy 단일 Primary SQL Gate 검증 완료. PRZ-010 변경 로그 동기화, PRZ-011 문서 처리 상태 UX와 PRZ-015 MCP 검색은 `VERIFIED`; PRZ-016은 PR #48로 `main`에 통합됐고 P10은 `VERIFIED`, P11은 `PARTIAL_PASS`, P11.1~P14는 `PASS`이나 P15의 인증된 PDF 페이지 이동이 `NOT_VERIFIED`여서 `IN_PROGRESS`; PRZ-008 검색 개선은 `IN_PROGRESS`; PRZ-009 사용자 관리형 Document Tag는 `VERIFIED` (`AUDIT Gate: PASS`, PR #51 merge `d44f30e`); PRZ-012 검색 근거 표현 품질은 `IMPLEMENTED_UNVERIFIED` |
| 계획된 미구현 | CareerFact, 근거 기반 portfolio, `/api/v1`, 독립 Engine 패키지 |
| 명시적 범위 제외 | 다중 OpenSQL DB node, DB 장애전환, OpenProxy 이중화·VIP와 서비스 연속성 보장 |

PRIZM의 장기 목표는 재사용 가능한 Career Intelligence Engine과 Reference App을
제공하는 것입니다. 현재 저장소는 아직 독립 Engine 패키지가 아니며, 하나의
Spring Boot 애플리케이션에 주요 기능이 모여 있습니다.

현재 대회 제품 초점은 문서를 업로드하면 ChangeLog가 색인 작업을 전달하고,
Ollama가 자동 임베딩한 뒤 안전하게 `ACTIVE`로 전환해 사용자별 원문 근거 검색을
제공하는 자동화된 AI 문서 관리 플랫폼입니다.

## 현재 사용자 흐름

```text
회원가입 → 로그인
→ 내 문서 목록 확인
→ UTF-8 TXT 또는 텍스트가 포함된 PDF 업로드
→ 원본·새 문서 버전·ChangeLog를 함께 저장
→ Dispatcher가 기존 색인 ProcessingJob을 생성 또는 재사용
→ Worker가 텍스트 추출·분할·임베딩 수행
→ 처리가 끝난 버전을 검색 대상으로 전환
→ 내 문서에서 원문 위치와 함께 검색 결과 확인
→ 문서에 연결한 태그 집계와 태그 이름으로 찾은 ACTIVE 원문 evidence 확인(PRZ-009 P4)
```

새 버전 처리가 실패하면 이전 검색 대상 버전을 유지합니다. 다른 사용자의 문서와
검색 결과는 이 흐름에 포함하지 않습니다. 현재 자체 호스팅 사용자는 일반 `USER`
계정을 직접 만들 수 있습니다. PRZ-004는 한 번만 켜는 demo `USER`와 합성 TXT/PDF로
이 흐름을 재현합니다.
검증 source commit의 두 fresh clone에서 이 흐름을 확인하고 독립 감사와
GitHub 통합을 완료했습니다.

PRZ-005에서는 Spring Boot와 Ollama `bge-m3`를 실제 OpenSQL `5432`에 직접
연결해 같은 흐름의 API와 브라우저 E2E를 검증했습니다. 두 사용자의 문서 목록,
상세와 검색 결과가 서로 노출되지 않는 것도 확인했습니다.

## 구현된 기능

### 로그인과 사용자 격리

- 이메일·비밀번호로 활성 일반 `USER`를 만드는 자체 호스팅 회원가입. 성공 응답은
  JWT를 포함하지 않으며 사용자는 기존 로그인 화면으로 이동
- 이메일·비밀번호 로그인과 JWT 인증
- 요청마다 DB에서 사용자 활성 상태·이메일·역할 재확인
- 사용자별 문서·버전·처리 작업·검색 결과 격리
- 일반 `USER`와 관리 역할인 `SYSTEM_ADMIN`의 API 권한 분리

### 문서와 버전 관리

- UTF-8 TXT와 비암호화 텍스트 PDF 업로드
- 문서 목록·필터·상세·수정·전체 삭제·과거 버전 삭제와 PDF 열람
- 원본 파일, SHA-256 해시와 변경 불가능한 버전(immutable version) 보존
- 새 버전 등록과 처리 완료 뒤 검색 대상 버전(active version) 전환

### 색인과 검색

- Ollama `bge-m3`를 이용한 1024차원 임베딩
- PostgreSQL pgvector 기반 원문 근거 검색
- TXT 텍스트 구간과 PDF 페이지 위치 반환
- 단일 검색 결과와 최대 5개의 Career Evidence 결과 제공. Career Evidence는 전체
  원문을 보존하면서 hard-wrap-aware, claim-complete 연속 원문 1–3문장을
  핵심 근거로 먼저 표시하고 출처,
  버전·관련도와 전체 원문 펼치기를 제공
- GENERAL Career Evidence는 기본 dense `0.50`을 유지하고, 결과가 비어 있는 단일
  2–4자 exact-token 질의에만 `0.49` 이상 후보 한 건을 제한적으로 복구. 완료
  배포·출시 검색과 Claim Gate에는 적용하지 않음
- 검색 가능한 청크가 없어 빈 Career Evidence 결과가 반환되면 등록 문서에서
  찾지 못했다고 안내

### MCP 읽기 전용 검색

- 요청 주소(endpoint)는 `POST /mcp`, 통신 규격(protocol)은 `2025-11-25`
- 연결 상태를 서버에 저장하지 않는(stateless) Streamable HTTP 사용
- `search_career_evidence` 도구 하나와 `{"query":"..."}` 입력
- Bearer JWT와 활성 `ROLE_USER` 필요
- 기존 Career Evidence Search를 재사용해 사용자별 데이터 격리(owner isolation)와
  현재 `ACTIVE` 버전만 검색하는 규칙(ACTIVE isolation) 유지

### 구현됐으나 최종 Gate가 남은 기능

- SYSTEM 추천 tag와 owner-scoped USER tag 생성·검색
- 최초 document upload와 document detail의 tag 연결·추가·제거
- 실제 document-tag 연결만 집계하는 경력 키워드 목록과 기존 Career Evidence Search를
  재사용하는 tag 상세
- 정규화 이름 기반 owner별 중복 방지와 다른 owner USER tag 미노출
- DocumentType folder 보관함과 우측 세로 기술 분류 rail, 공통 Soft Minimal Career Vault UI
- active·처리 중 version을 보호하는 과거 version별 삭제와 사용자 중심 처리 상태 문구

PRZ-009 P4 source `1c1d8d2`는 backend unit 578건 중 558건이 통과했고
기존 skip 20건, 실패·오류 0이다. frontend unit 45개와 lint·typecheck·build도 통과했다.
PostgreSQL integration은 116건 중 108건이 통과했고 실패·오류
0, 기존 skip 8건이다. 인증된 upload/detail/경력 키워드 tag 흐름은 사용자가 정상 동작을
확인했다(`USER_CONFIRMED`). 독립 재감사는 blocking finding 0건으로 통과해 상태는
`VERIFIED`, AUDIT Gate `PASS`이며 PR #51 merge `d44f30e`로 `main`에 통합했다. OpenSQL opt-in은 `NOT_RUN`이다. 상세 범위는
[PRZ-009 Evidence](../specs/PRZ-009-career-keyword-map/evidence.md)를 따른다.

### 비동기 처리와 파일 정리

Worker가 중단돼도 만료된 작업을 다시 처리할 수 있습니다. 오래된 Worker가 최신
결과를 덮어쓰지 못하도록 보호하며, DB 처리와 원본 파일 정리가 어긋난 경우에는
별도 정리 작업으로 복구를 시도합니다.

PRZ-011은 문서 처리의 파일 읽기·텍스트 추출·청크 생성·실제 임베딩 n/N·저장
단계를 ProcessingJob에 기록한다. 전체 청크 수를 모를 때는 퍼센트를 만들지 않고,
확정 뒤 실제 완료/전체 수로만 계산한다. 문서 목록과 상세는 비종료 상태에서 약 2초
간격으로 갱신하며 종료 상태에서 멈춘다. 기존 retry 횟수와 `next_retry_at`을 그대로
보여 주고, 내부 예외 대신 제한된 Ollama/model/GPU·일반 처리 실패 메시지를 표시한다.

구성 요소와 내부 보호 방식은 [Architecture](architecture.md), 설계 선택의 배경과
트레이드오프는 [대표 문제 해결 사례](showcase/problem-solving-case-studies.md)에서
확인할 수 있습니다.

## 부분 검증과 환경별 상태

| 대상 | 상태 | 최근 기록 |
|---|---|---|
| Backend `test` task | `PASS` | 2026-08-05 source `2b8b600`: 전체 268개 중 253 pass, 15 skip, 실패·오류 0건 |
| Frontend lint·typecheck·build | `PASS` | 2026-08-05 source `2b8b600`: lint·typecheck·production build 통과. 공식 unit test 명령은 없어 `NOT_RUN` |
| 기본 integration 회귀 | `PASS` | 2026-08-05 source `2b8b600`: 전체 70개 중 67 pass, 3 skip, 실패·오류 0건. 기본 실행에서 OpenSQL opt-in test skip은 정상 |
| PRZ-007 자체 호스팅 회원가입 | `VERIFIED` | PostgreSQL signup·BCrypt·활성 `USER`, 기존 login·JWT 보호 API, 두 사용자 격리, local-demo 제거, bootstrap 유지와 `http://localhost:5173` 브라우저 흐름 통과 |
| Dense 검색 평가 | `HISTORICAL_PASS_NOT_RERUN` | 2026-07-14 합성 기준선 보존 |
| Docker Compose | `PASS` — PRZ-004 | 2026-08-01 서로 다른 project·port·volume의 두 독립 clone에서 구성·빌드·기동과 demo `USER` 전체 흐름 확인 |
| Ollama `bge-m3` | `VERIFIED` — PostgreSQL·OpenSQL 범위 구분 | 2026-08-02 Ollama 0.32.3, `bge-m3:latest` digest와 1024차원·0이 아닌 임베딩을 확인. 실제 OpenSQL 직접 `5432` E2E에도 사용 |
| OpenSQL 단일 SQL Gate | `PASS` | 2026-07-30 Rocky Linux 9.7 single-node OpenSQL에서 Flyway·vector·검색·소유권·Worker SQL 통과 |
| PRZ-005 OpenSQL+Ollama E2E | `VERIFIED` | 실제 OpenSQL 직접 `5432`에서 API·브라우저 E2E, TXT/PDF 원문 검색, 두 사용자 격리와 격리 DB의 OpenSQL opt-in integration test 통과 |
| OpenProxy 단일 Primary | `VERIFIED` | PRZ-013에서 Windows TCP `:6432`, `prizm_app` SQL SELECT/WRITE, Flyway direct/runtime proxy 분리, TXT/PDF·Ollama focused E2E 통과. 재시작 후 새 SQL 연결은 PASS이며, 지속 application continuity는 Single-only 제품 범위에 포함하지 않음 |
| PRZ-015 MCP Career Evidence 검색 | `VERIFIED` | 공식 Java MCP Client와 protocol `2025-11-25`, 실제 USER JWT를 사용. Flyway는 OpenSQL `:5432`에 직접 연결하고 애플리케이션은 OpenProxy `:6432/opensql`을 거쳐 실행. Ollama `bge-m3` 전체 흐름(E2E), REST와 MCP 결과 일치, 사용자별 격리와 `ACTIVE` 버전 격리 통과 |
| 대회 OpenSQL 구성 | `SINGLE_ONLY` | 공식 안내에 따라 단일 서버 설치만 사용. PRZ-014 다중 노드 구성은 `REJECTED` |
| PRZ-004 demo `USER` clean-clone | `VERIFIED` | `25d09e9`에서 자동 검증 `339 PASS / 18 SKIP / 0 FAIL`과 두 독립 clone 통과. `aff3e87` 경로 교정 뒤 Windows·Linux Node test와 GitHub CI 6건 통과, PR #25 merge `1f9a5ad`. 두 번째 빈 목록 UI 직접 관찰은 `NOT_RUN` |
| PRZ-008 검색 근거 신뢰성 | `IN_PROGRESS` | 2026-08-13 source `2190d47`, PR #40 merge `9b24808`: 기본 profile, v2 상태, 제한적 exact-token rescue와 OpenSQL direct `5432` API·UI Gate를 통합. 의미 단위 청킹·batch embedding·PDF 중복 최적화의 제품 적용 Gate는 남음 |
| PRZ-009 사용자 관리형 Document Tag | `VERIFIED` (`AUDIT Gate: PASS`, PR #51 merge `d44f30e`) | 기존 자동 keyword 구현을 P4 source `1c1d8d2`에서 V16 `tags`/`document_tags`, owner-scoped API와 upload/detail Tag Modal로 교체. 목록 count는 tag metadata, 상세는 기존 Career Evidence Search로 owner ACTIVE 전체 문서 evidence를 조회한다. backend unit 578 total·558 pass·20 skip·0 fail/error, frontend unit 45·lint·typecheck·build pass, PostgreSQL integration 108 pass·8 skip·0 fail/error, Search Production diff 0. 인증 브라우저는 `USER_CONFIRMED`, OpenSQL은 `NOT_RUN`; 독립 재감사 blocking finding 0 |
| PRZ-010 변경 로그 동기화 | `VERIFIED` | 2026-08-12 source `26c546b`: PostgreSQL ChangeLog integration, 실제 OpenSQL direct `5432` V14 SQL Gate, 실제 OpenSQL+Ollama `bge-m3` V1→V2 E2E와 실패 시 V1 보존, 전체 integration `104 completed / 7 skipped / 0 failures`, backend test, frontend lint/build, Compose와 diff 감사 통과 |
| PRZ-011 문서 처리 상태 UX | `VERIFIED` | 2026-08-13 source `fbb3481`: backend unit 464개 중 449 pass·15 skip, integration 112개 중 105 pass·7 skip, frontend unit 5개·lint·build, Compose V15 적용, PostgreSQL+pgvector·Ollama `bge-m3` 문서 처리/검색과 browser polling·retry 표시 통과. AUDIT blocking 2건 수정 뒤 재-AUDIT PASS, PR #41로 `main` 통합 |
| PRZ-012 검색 근거 표현 품질 | `IMPLEMENTED_UNVERIFIED` | 질문 관련 원문 1–3문장 선택과 근거 중심 UI, PRZ-008 평가 15개 결과 불변, backend unit·integration과 frontend 검증 통과. 실제 개인 문서 대표 7개 Before/After는 owner·authentication 경계 안에서 실행하지 못해 `NOT_RUN`, VERIFY Gate `FAIL` |
| PRZ-016 P10 Evidence Localization | `VERIFIED` — 통합 Gate | frozen P8.1 Judge displayed/localization 68.75% → 87.5%, Stress 65/60% → 100/100%; Dense/selection/FPR 회귀 0, owner/ACTIVE isolation PASS. PR #48 merge `154b9c8`로 통합 |
| PRZ-016 P11 Source Consolidation | `PARTIAL_PASS` — 통합 Gate | 실제 이력서 same-page distinct evidence retention은 개선되고 frozen 품질·FPR·localization·isolation은 유지됐으나 Stress 1건에서 final 3→5와 duplicate snippet +2가 발생. P11은 `VERIFIED`가 아님 |
| PRZ-016 P11.1 Duplicate Evidence Consolidation | `PASS` — 통합 Gate | P11 source identity를 유지한 QEV repeated-evidence 축약으로 Stress final 5→3, duplicate extras 5→3 및 P10 exact final result를 복구. 실제 이력서 retention 4/4/3/3, P8.1/P9/P10 metric·FPR·localization·isolation 유지 |
| PRZ-016 P12 Simple Tech Usage Eligibility | `PASS` — 통합 Gate | P9 simple `USE` query가 같은 candidate의 project-scoped technology declaration 또는 직접 usage를 근거로 인정하도록 최소 보완. PostgreSQL eligibility/final 0/0→2/2, OAuth2 0/0→1/1; P8.1/P9/P10/P11.1 metric·FPR·localization·duplicate·owner/ACTIVE isolation 유지 |
| PRZ-016 P12.1 Direct-Support Floor Bypass Contract | `PASS` — 통합 Gate | evaluator가 `SUPPORTED` 및 `directSupport=true`로 판정한 claim 질문은 action/numeric requirement 공백만으로 dense floor에서 제거되지 않도록 최소 보완. FCM chunk 108이 eligibility/final로 복구됐고 P10 frozen metric·FPR·localization·owner/ACTIVE isolation 및 기존 direct-anchor fallback 계약은 유지 |
| PRZ-016 P13 Evidence Expansion Safety | `PASS` — 통합 Gate | selected chunk의 직접 ASCII query anchor를 local evidence 우선 조건으로 보존하고 cross-chunk expansion 후보도 이를 유지하도록 제한. FCM `108→106` anchor loss는 `108→108`으로 복구됐으며 P10 frozen metric·FPR·localization·owner/ACTIVE isolation은 유지 |
| PRZ-016 P14 Claim-Complete Snippet | `PASS` — 통합 Gate | 해결 질문의 extractive scorer가 action/problem-result complete contiguous 1–3문장 window를 우선하도록 보완. Q9 result/evidence chunk 106과 P13 safety는 유지되고 frozen P10 metric·FPR·localization·owner/ACTIVE isolation은 유지 |
| PRZ-016 P15 PDF Document Confirmation UX | `IMPLEMENTED_UNVERIFIED` | PR #48 merge `154b9c8`로 통합. frontend unit·lint·build·Docker와 비인증 렌더링은 통과했으나, 실제 로그인 세션과 PDF fixture가 없어 인증된 PDF 페이지 이동은 `NOT_VERIFIED` |

세부 실행 환경과 명령은 [PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md),
[PRZ-002 Evidence](../specs/PRZ-002-open-source-readiness/evidence.md),
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md),
[PRZ-004 Evidence](../specs/PRZ-004-clean-clone-demo/evidence.md),
[PRZ-005 Evidence](../specs/PRZ-005-opensql-ollama-e2e/evidence.md),
[PRZ-010 Evidence](../specs/PRZ-010-change-log-sync/evidence.md)에서
확인합니다. PostgreSQL·pgvector 결과를 OpenSQL 결과로 바꾸어 표현하지 않습니다.
PRZ-011의 검증·통합 결과는
[PRZ-011 Evidence](../specs/PRZ-011-document-processing-status-ux/evidence.md)에
기록합니다.

## 계획된 미구현 기능

- CareerFact 후보·확인·거절과 `INSUFFICIENT_EVIDENCE`
- 검증된 CareerFact를 이용한 JSON·Markdown portfolio와 source manifest
- `/api/v1`, OpenAPI, webhook/outbox
- 독립 Engine artifact와 기관용 workspace·권한

## 명시적 범위 제외

- 다중 OpenSQL DB node와 DB 장애전환
- OpenProxy 이중화·VIP
- 다중 노드 서비스 연속성 보장

## 알려진 한계

- PRZ-004 두 번째 환경의 빈 문서 목록은 API로 확인했으며 브라우저에서 직접
  관찰하지는 않았습니다.
- 전체 처리 시간과 버전당 최대 chunk 수를 제한하지 않습니다.
- 기본값 `source-dedup-evidence-signals-v1`은 의미상 근거 없음과 검색 문서 없음을
  구분하고 동일 출처 위치·본문 중복을 축약합니다. `legacy-dense-v1`은 명시적
  `PRIZM_SEARCH_PROFILE` rollback override로 유지합니다.
- 과거 자동 keyword·category Browse 구현과 확장 UI는 source `d52c6d0`, `3af28492`로
  `main`에 통합됐지만, 현재 P4 source `1c1d8d2`에서는 사용자 관리형 Document Tag로
  대체했습니다. 현재 목록 count는 tag metadata이고 상세는 tag 이름으로 기존 Career Evidence
  Search를 호출합니다. PostgreSQL·frontend 검증은 통과했고 인증 브라우저는
  `USER_CONFIRMED`이고 독립 재감사는 통과했으나 GitHub 통합은 남아 있습니다. OpenSQL opt-in은
  `NOT_RUN`이므로 OpenSQL 검증 근거로 확대하지 않습니다.
- 프런트엔드 회귀 테스트는 presentation과 component 표시·상태·navigation 계약을
  검증하지만 실제 브라우저 E2E 자동화는 없습니다. PRZ-009 인증 흐름은 사용자가 직접
  확인한 `USER_CONFIRMED` 근거로 구분합니다.
- V13의 일부 제약과 기존 데이터 보정 전용 회귀 테스트가 없습니다.
- 일부 JavaDoc이 TXT/PDF 공통 동작을 TXT 전용으로 설명합니다.
- 일부 파일시스템에서는 안전 조건을 충족하지 못해 자동 파일 정리를 중단합니다.
- 실제 OpenSQL 직접 `5432`의 API·브라우저 흐름과 OpenProxy `:6432`
  단일 Primary focused runtime E2E는 검증했습니다. 대회 OpenSQL 범위는 단일
  서버 설치로 고정하며, 다중 노드 서비스 연속성은 제품 범위에 포함하지 않습니다.

## 다음 우선순위

제품 개발 순서는 [개발 로드맵](roadmap.md)을 따릅니다. 현재
[PRZ-008 검색 근거 신뢰성](../specs/PRZ-008-search-evidence-reliability/spec.md)은
통합된 제품 범위 이후 남은 최적화 Gate를, [PRZ-009 사용자 관리형 Document Tag](../specs/PRZ-009-career-keyword-map/spec.md)는
독립 재감사를 통과했고 PR #51 merge `d44f30e`로 통합했습니다. [PRZ-012 검색 근거 표현 품질](../specs/PRZ-012-search-evidence-presentation/spec.md)은
실제 개인 문서 대표 질의 검증을 남겨 두었습니다. PRZ-009는 `VERIFIED`
(`AUDIT Gate: PASS`, PR #51 merge `d44f30e`), PRZ-012는 `IMPLEMENTED_UNVERIFIED`입니다. OpenProxy 단일 Primary SQL
routing은 PRZ-013에서 검증했습니다. 대회 제공 OpenSQL의 Single-only 지침에 따라
다중 노드 구성과 장애 전환은 다음 작업이나 후속 검증 대상이 아닙니다. PRZ-015의
읽기 전용 MCP Career Evidence 검색은 실제 OpenSQL/OpenProxy 환경의 P2 전체
흐름(E2E)까지 통과해 `VERIFIED`입니다.
