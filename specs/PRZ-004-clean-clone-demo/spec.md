# PRZ-004 — 안전한 clean-clone demo

## 상태

`VERIFIED`

구현과 필수 `VERIFY`, 독립 최종 `AUDIT`, GitHub PR #25 CI와 `main` 통합을
완료했다. 환경별 실행 기준과 남은 `NOT_RUN` 항목은 [Evidence](evidence.md)에
기록한다.

이 문서는 초기 구현 후보가 만들어진 뒤 범위를 축소해 사후 대조한 conformance
계약이다. 구현 전에 확정된 사전 계획으로 소급해 표현하지 않으며, 자세한 이력은
[Plan](plan.md)과 [Evidence](evidence.md)에 기록한다.

## 목적

처음 PRIZM을 clone한 사용자가 기존 계정이나 Docker volume에 기대지 않고,
PostgreSQL·pgvector와 호스트 Ollama를 이용해 로그인부터 원문 근거 검색까지
안전하게 재현한다.

## 범위

- 도구·포트·Ollama `bge-m3` 사전 확인
- 기본 비활성 one-time demo `USER` bootstrap
- 비밀값을 출력하지 않는 로컬 `.env` 생성과 실행별 Compose 격리
- first-party 합성 TXT/PDF 생성
- 로그인, 업로드, `ACTIVE` 전환, TXT `TEXT_CHUNK`, PDF `PAGE` 검색 검증
- 브라우저에서 로그인·문서 상세·PDF 원문·검색·로그아웃 확인
- 두 개의 독립 clone에서 서로 다른 project·volume·데이터를 사용하는 검증
- 변경으로 발생한 dependency·SBOM·license 기록 동기화

## 요구사항

### PRZ-004-R01 — 안전한 demo USER

- bootstrap은 기본적으로 비활성화한다.
- 공개 회원가입 API를 추가하지 않는다.
- 생성 역할은 항상 `USER`이며 `SYSTEM_ADMIN`으로 바꿀 설정을 제공하지 않는다.
- email을 정규화하고 password는 BCrypt hash로만 저장한다.
- 기존 email이 있으면 계정을 수정하지 않고 시작을 실패시킨다.
- demo와 `SYSTEM_ADMIN` bootstrap을 동시에 활성화하면 어떤 계정도 쓰기 전에
  시작을 실패시킨다.
- bootstrap password는 12자 이상, UTF-8 72 bytes 이하로 제한한다. 로그인도
  BCrypt의 72-byte 경계를 넘는 입력을 hash 비교 전에 거부한다.
- password·JWT·DB 비밀번호·token과 demo email을 application log에 출력하지
  않는다.

### PRZ-004-R02 — 로컬 설정과 Compose 격리

- 기존 `.env`를 덮어쓰지 않고, 생성 파일은 Git ignore 대상이어야 한다.
- 매 실행은 안전한 고유 `COMPOSE_PROJECT_NAME`을 사용한다.
- Compose 실행은 `.env`의 project·`compose.yaml`을 명시해 상위 shell의
  `COMPOSE_PROJECT_NAME`·`COMPOSE_FILE`로 격리 경계가 바뀌지 않게 한다.
- `.env`가 관리하는 project·port·DB/Flyway credential·JWT·bootstrap·Ollama·model·
  CORS 설정은 상위 shell이 덮어쓰지 못하게 한다. 충돌이나 실패 메시지에는
  비밀값과 demo email을 출력하지 않는다.
- CORS 허용 origin은 `.env`의 frontend port로 만든 정확한 localhost origin과
  일치해야 한다. 기본 port가 생략되는 URL 정규화도 처리하며 wildcard, 이전 port,
  상위 shell origin을 허용하지 않는다.
- 사용자는 기존 Compose project나 volume을 삭제하지 않고 host port를 명시적으로
  바꿀 수 있다.
- POSIX에서 새 `.env` mode는 `0600`이어야 한다. Windows에서는 mode bit 대신
  ACL을 사용한다는 한계를 문서화한다.
- 설정 검사는 비밀값을 렌더링하지 않는 `docker compose config --quiet`만 공개
  절차에 사용한다.

### PRZ-004-R03 — 합성 fixture

- TXT와 text-layer PDF는 실제 사람·회사·프로젝트·성과에서 파생하지 않은
  first-party 합성 자료여야 한다.
- 생성 파일과 업로드 원본은 ignored `local/` 아래에만 둔다.
- TXT와 PDF에는 서로 다른 검색 marker와 예상 source type을 기록한다.

### PRZ-004-R04 — API smoke

- verifier는 기본적으로 `localhost`, `127.0.0.1`, `::1`만 허용하고 임의 원격
  주소로 demo credential을 보내지 않는다. HTTP redirect도 따라가지 않고
  fail-closed 한다.
- one-time bootstrap을 비활성화하고 backend를 다시 만든 뒤에만 실행한다.
- 로그인 응답이 enabled `USER`인지 확인한다.
- TXT/PDF를 각각 업로드하고 실패·timeout을 구분하며 해당 version이 `ACTIVE`가
  될 때까지 polling한다.
- polling은 전체 deadline과 최대 시도 횟수를 함께 적용하고, terminal failure를
  즉시 실패로 처리한다. 시간이 비정상적으로 움직여도 최대 시도 횟수를 넘지 않는다.
- 검색 결과 전체가 이번 실행에서 업로드한 document/version 허용 목록에 속해야
  한다. 예상 밖 document/version이 하나라도 있거나 결과가 비어 있으면 실패한다.
- 현재 검증 대상의 marker가 검색 결과에 포함돼야 한다.
- TXT는 `TEXT_CHUNK`, PDF는 `PAGE`와 유효한 page index를 확인한다.
- token을 제거한 요청이 보호 경로에서 `401`인지 확인한다.

### PRZ-004-R05 — 기존 계약 보존

- JWT DB 재검증과 owner-scoped 문서·검색 경계를 우회하지 않는다.
- 실패·미완료 version은 검색 후보나 `ACTIVE`가 되지 않는다.
- 기존 active version, Worker lease·recovery·fencing과 안전한 파일 정리 계약을
  바꾸지 않는다.
- 적용된 Flyway migration을 수정하거나 새 migration을 추가하지 않는다.

### PRZ-004-R06 — 재현성과 공급망 기록

- 최종 source commit을 `--no-hardlinks`로 두 번 clone해 서로 다른 Compose
  project·DB/runtime volume과 새 비밀번호로 같은 흐름을 통과한다.
- 두 번째 환경에는 첫 번째 demo 계정의 DB나 문서가 나타나지 않아야 한다.
- 실제 Ollama version, model name·manifest, 1024 embedding dimension을 기록한다.
  mutable `latest`를 bit-identical 재현으로 표현하지 않는다.
- dependency나 Docker build identity가 바뀌면 실제 필요성을 기록하고 frontend
  SBOM, checksum과 license audit을 함께 갱신한다.

### PRZ-004-R07 — 상태와 Evidence

- 작업 시작 main, 전체 clean-clone source commit, 최종 교정 source commit,
  두 clean clone 결과와 GitHub 통합 상태를 구분한다.
- `PASS`, `FAIL`, `SKIPPED`, `NOT_RUN`, `NOT_VERIFIED`를 서로 바꾸어 쓰지 않는다.
- 실제 Issue·PR·CI·review·merge가 없으면 없다고 기록한다.
- PostgreSQL 결과를 OpenSQL 결과로 표현하지 않는다.

## 보존 계약

- 사용자 ownership, JWT와 DB 사용자 재검증
- immutable version, 이전 active version 보존과 원자적 활성화
- Worker lease·recovery·claim-version fencing
- `bge-m3` 1024차원 검증과 owner-scoped exact cosine 검색
- source-only 배포와 OpenSQL 공급 자산 비재배포 경계

## 제외 범위

- 공개 회원가입과 계정 관리 UI
- frontend 접근성·디자인 교정
- CareerFact, portfolio, MCP와 변경 로그 동기화
- OpenSQL+Ollama 전체 사용자 흐름
- OpenProxy, OpenHA와 DB failover
- model, Docker image, DB volume 또는 OpenSQL 공급 파일의 재배포

## 완료 조건

1. demo bootstrap 보안·BCrypt byte 경계·충돌·중복 계정 단위 테스트가 통과한다.
2. 기존 인증·ownership·실패 version 계약을 포함한 전체 unit·integration test가
   실패 0건으로 통과하며 skip 수를 그대로 기록한다.
3. Node script test, frontend lint·build와 full/prod npm audit이 요구한 상태로
   통과한다.
4. 최종 source commit의 두 fresh clone에서 고유 project·volume·port로 전체
   API smoke와 브라우저 시험표를 통과한다.
5. `.env`, credential, fixture, model, volume과 image가 Git에 포함되지 않는다.
6. SBOM·checksum·license audit과 OSS readiness가 서로 일치한다.
7. Evidence가 환경별 결과와 `NOT_RUN` 경계를 실제 결과 그대로 기록한다.
