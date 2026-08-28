# PRIZM Spec Registry 도입 전 구현 이력

이 문서는 Spec Registry가 생기기 전에 실제 source code, Flyway migration과 실행
가능한 test로 만들어진 PRIZM의 구현 순서를 선별해 보여 줍니다. 현재 제품 상태나
새 기능 계획을 정의하는 Spec이 아닙니다. 현재 상태는
[현재 구현 현황](../docs/project-status.md)과 [Spec Registry](README.md)를 따릅니다.

## Git 경계

- 구현 이력 시작: 2026-07-11
  [`b633f469`](https://github.com/jaemin-devlog/PRIZM/commit/b633f4693f4a1605fa71b3d9aed3958bf9dc37d9)
- Pre-Spec source cut: 2026-07-23
  [`e995a5f`](https://github.com/jaemin-devlog/PRIZM/commit/e995a5fdecc63afbd383157dd5a8b6d74b607e3f)
- Spec Registry 최초 도입: 2026-07-23
  [`3233bad7`](https://github.com/jaemin-devlog/PRIZM/commit/3233bad7e24747c63a52cd96b295fe3cb66d1158)
- 최초 Registry 기준선: [PRZ-000 Spec](PRZ-000-platform-baseline/spec.md),
  [PRZ-000 Evidence](PRZ-000-platform-baseline/evidence.md)
- 당시 계획·실험·실패를 포함한 날짜별 원문:
  [전체 개발 기록](../docs/archive/development-log-full-history.md)

`e995a5f` tree에는 `specs/` 경로가 없습니다. 그 commit의 직계 자식
`3233bad7`이 `specs/README.md`와 PRZ-000 Spec·Evidence를 처음 추가했습니다.
따라서 날짜 제목이 아니라 이 Git 경계로 Pre-Spec과 Registry 이후를 구분합니다.

## 읽는 기준

- 아래 `구현`은 표시한 commit의 source, migration 또는 test diff로 확인했습니다.
- 당시 실행 결과는 이번 정리에서 다시 실행한 결과가 아닙니다. PRZ-000이
  `HISTORICAL_PASS_NOT_RERUN` 또는 `NOT_RUN`으로 남긴 환경 경계를 유지합니다.
- 문서에만 존재했던 제품 구상과 채택하지 않은 실험은 구현 단계로 올리지 않습니다.
  해당 원문과 당시 판정은 전체 개발 기록에 보존합니다.
- 존재하지 않았던 과거 Spec, Issue, PR, review를 소급해 만들지 않습니다.

## 2026-07-11 — 애플리케이션과 데이터 기반

- **근거:** [`b633f469`](https://github.com/jaemin-devlog/PRIZM/commit/b633f4693f4a1605fa71b3d9aed3958bf9dc37d9)
- **구현:** Spring Boot·Gradle backend, React/Vite frontend, PostgreSQL 16과
  pgvector Compose, Flyway V1, CI와 PostgreSQL·pgvector 통합 검증 기반을
  추가했습니다.
- **경계:** 조건부 외부 DB test 파일의 존재는 실제 외부 환경 검증 결과가
  아닙니다.

## 2026-07-13 — 최소 벡터 검색과 문서 등록

- **근거:**
  [`a3e5a636`](https://github.com/jaemin-devlog/PRIZM/commit/a3e5a6362cd13cefe3bf286eb73cf5b472aea890),
  [`b9f01b04`](https://github.com/jaemin-devlog/PRIZM/commit/b9f01b04c418a6160655b1c1e3fcc29d918fdf1b)
- **구현:** V2 `document_chunks`, Ollama embedding adapter, pgvector exact cosine
  검색과 최소 API를 만들었습니다. V3에서는 document와 immutable version을
  분리하고 TXT 원본과 `QUARANTINED` version 등록·조회를 연결했습니다.
- **경계:** 당시 PostgreSQL·pgvector와 로컬 Ollama 결과는 역사 기록이며 Registry
  직전 source cut에서 재실행하지 않았습니다.

## 2026-07-13 — 비동기 TXT 색인과 Worker 복구

- **근거:**
  [`a8616769`](https://github.com/jaemin-devlog/PRIZM/commit/a86167692b56858fab18af75c3c2f87091a3d0bc),
  [`6ae4ac5f`](https://github.com/jaemin-devlog/PRIZM/commit/6ae4ac5f1d3227738b1ff1514f0fa2771a84a9fa)
- **구현:** V4 processing job, `FOR UPDATE SKIP LOCKED` claim, TXT 추출·청킹·임베딩,
  청크 저장과 `ACTIVE` 전환을 연결했습니다. V5는 lease, retry/recovery와
  `claim_version` fencing을 추가했습니다.
- **보존 계약:** 외부 파일·Ollama 처리는 긴 DB lock 밖에서 수행하고, 처리가
  끝난 version만 검색 대상으로 활성화합니다.

## 2026-07-13 — JWT와 자동 문서 처리 전환

- **근거:**
  [`595cc54a`](https://github.com/jaemin-devlog/PRIZM/commit/595cc54a771aa87a71df9353ee2165e13d9e1bd5),
  [`89ff2507`](https://github.com/jaemin-devlog/PRIZM/commit/89ff25075aef94cbfb0bed5a9ede475283dfd48f),
  [`624cceb4`](https://github.com/jaemin-devlog/PRIZM/commit/624cceb46bdb58c74bce363d8ba65d8eb66d11b4),
  [`2687d3ae`](https://github.com/jaemin-devlog/PRIZM/commit/2687d3ae616d24afe69be4f69c9bc01a96a26f7a)
- **구현:** V6 users, BCrypt login, JWT 검증과 요청별 DB 사용자 상태·email·role
  재확인을 추가했습니다. issuer·만료·비밀키·Bearer 처리와 기본 접근 거부를
  보강했고, V7에서 업로드된 version이 자동 processing job으로 이어지도록 상태
  계약을 전환했습니다.

## 2026-07-14 — 사용자 소유권과 문서 유형

- **근거:**
  [`a341e216`](https://github.com/jaemin-devlog/PRIZM/commit/a341e2161344d0b4fb029ccd89754bd2b1894645),
  [`9b048ac0`](https://github.com/jaemin-devlog/PRIZM/commit/9b048ac0dad20f4791cd97272a48a11c58a003c2),
  [`d199ce94`](https://github.com/jaemin-devlog/PRIZM/commit/d199ce9496e359f3bba5de76c9d94eb65222d7f8),
  [`feae4c80`](https://github.com/jaemin-devlog/PRIZM/commit/feae4c80ef8bdf6aa5658cd491e69e3c0b834e7a)
- **구현:** V8에서 document, version, processing job과 chunk의 owner 관계를
  연결하고 API·repository·검색 후보에 같은 owner 조건을 적용했습니다. V9에서
  12개 `DocumentType`, 기본 `OTHER`와 owner-scoped 유형 필터를 추가했습니다.

## 2026-07-14 — Career Vault와 TXT/PDF 원문 위치

- **근거:**
  [`01a420c2`](https://github.com/jaemin-devlog/PRIZM/commit/01a420c211dca122f1ff746632e9a0b0dd74332f),
  [`5777a9f0`](https://github.com/jaemin-devlog/PRIZM/commit/5777a9f0164472ec034678aaa269397dbedfb3cc),
  [`edb8c632`](https://github.com/jaemin-devlog/PRIZM/commit/edb8c6323b1cc1097dbc19694f43cad201c68e41),
  [`fff3ef00`](https://github.com/jaemin-devlog/PRIZM/commit/fff3ef00f9c6893ae915ad540e41680a0aeb7c45),
  [`478d56b5`](https://github.com/jaemin-devlog/PRIZM/commit/478d56b5cdd4c1666e7c847d147ab289351d9209)
- **구현:** frontend login, owner 문서 목록·유형 필터와 TXT/PDF 업로드를
  연결했습니다. V10은 TXT `TEXT_CHUNK` 위치를, V11은 text-layer PDF의 1-based
  `PAGE` 위치를 저장했습니다.

## 2026-07-14 — Career Evidence와 입력 안전성

- **근거:**
  [`a569e577`](https://github.com/jaemin-devlog/PRIZM/commit/a569e57774f7f3051825586c289df98da09bbf1f),
  [`0a603c7d`](https://github.com/jaemin-devlog/PRIZM/commit/0a603c7db60c853698f0caeec919d520d3ae74f3),
  [`b2222ce9`](https://github.com/jaemin-devlog/PRIZM/commit/b2222ce93476bf58725de7fad6d46fb18f48407e),
  [`1ae77c70`](https://github.com/jaemin-devlog/PRIZM/commit/1ae77c70e5380389918c337407e4c34199712d55)
- **구현:** owner의 `ACTIVE` version에서 원문 근거를 최대 5개 반환하는 API와 UI,
  embedding의 차원·finite·non-zero norm 검증, PDF 페이지·추출 문자 수 제한을
  추가했습니다.

## 2026-07-14~16 — Worker 안정성과 orphan-file cleanup

- **근거:**
  [`76a7fdf5`](https://github.com/jaemin-devlog/PRIZM/commit/76a7fdf5f56bcf719477c3eeb2f7b1a2efec04de),
  [`50ffe49b`](https://github.com/jaemin-devlog/PRIZM/commit/50ffe49b37b1243e639b3be89aba0e1f1bd622a9),
  [`86387e7c`](https://github.com/jaemin-devlog/PRIZM/commit/86387e7c227ede3be96c538aafc48b0205bc5e18)
- **구현:** indexing lease heartbeat와 파일 오류 분류, V12 cleanup job, V13
  cleanup claim·lease·fencing·retry/recovery를 추가했습니다. 지원 filesystem에서는
  descriptor-relative 삭제를 사용하고 안전한 primitive가 없으면 fail-closed합니다.

## 2026-07-14 작업·2026-07-23 통합 — 검색 평가 기반

- **근거:**
  [`46e24eff`](https://github.com/jaemin-devlog/PRIZM/commit/46e24eff85f055740f7397190bb1e6266aa742a8),
  [`347d54db`](https://github.com/jaemin-devlog/PRIZM/commit/347d54db406f0377bb443ae7ff42aaf2bfa8e704)
- **구현:** 별도 `searchEvaluation` source set, 합성 corpus·질문 fixture,
  loader·metric·report와 test를 만들고 합성 문서 11개·질문 30개로 확장했습니다.
- **경계:** 이 source는 `e995a5f`의 병합 parent를 통해 들어왔습니다. 당시 실행
  기록은 보존하지만 source cut에서 평가 환경을 다시 실행하지 않았습니다.

## 2026-07-21 — OpenSQL 검증 harness 준비

- **근거:** [`dd2807a8`](https://github.com/jaemin-devlog/PRIZM/commit/dd2807a891409a3bc2976361dec92aea3153f9a8)
- **구현:** PostgreSQL Testcontainers와 opt-in 외부 DB가 V1~V13, 1024차원 vector,
  indexing·cleanup claim과 recovery를 같은 assertion으로 검사하도록 준비했습니다.
- **경계:** 실제 OpenSQL 환경은 이 단계에서 `NOT_RUN`입니다. harness의 존재를
  호환성 성공으로 바꾸지 않습니다.

## 2026-07-22~23 — 문서 관리·새 version·PDF 열람

- **근거:**
  [`53e917a3`](https://github.com/jaemin-devlog/PRIZM/commit/53e917a38d61fc6d2f3c07c8aa6441ece78a85e3),
  [`8b810d03`](https://github.com/jaemin-devlog/PRIZM/commit/8b810d03496d04e8aa13e42c41ce154fbff3c0e2)
- **구현:** owner-scoped 문서 상세·metadata 수정·terminal 문서 삭제, immutable
  다음 version 등록, PDF thumbnail과 원본 열람 API/UI를 추가했습니다. 새 version의
  처리가 성공하기 전에는 기존 `active_version_id`를 유지했습니다. backend·frontend
  container build 파일도 이 기준선에 들어왔습니다.

## 2026-07-23 — Registry 직전 source cut

- **근거:** [`e995a5f`](https://github.com/jaemin-devlog/PRIZM/commit/e995a5fdecc63afbd383157dd5a8b6d74b607e3f)
- **통합:** 문서 관리 기준선과 검색 평가 source·test를 하나의 tree로 모았습니다.
  이 tree에는 아직 `specs/`가 없습니다.
- **당시 자동 검증:** PRZ-000 Evidence는 backend test 242건 중 228건 성공,
  환경 조건 14건 skip, frontend lint와 production build 성공을 기록합니다.
- **환경 경계:** PostgreSQL 통합·문서 관리·Dense 평가는
  `HISTORICAL_PASS_NOT_RERUN`, 이 capture의 Compose·Ollama·OpenSQL은 `NOT_RUN`입니다.

## 2026-07-23 — PRZ-000 Registry 전환

[`3233bad7`](https://github.com/jaemin-devlog/PRIZM/commit/3233bad7e24747c63a52cd96b295fe3cb66d1158)
`docs: add AS_BUILT spec baseline`은 직전 부모 `e995a5f`의 구현을 PRZ-000
`AS_BUILT_BASELINE`으로 기록했습니다. 이 commit부터 이후 기능과 검증 이력은
[Spec Registry](README.md)의 실제 PRZ 문서와 source·migration·test evidence를
따라 확인합니다.
