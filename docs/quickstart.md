# PRIZM 로컬 Quickstart

이 문서는 GitHub에서 처음 clone한 사용자가 기존 계정이나 Docker volume에
기대지 않고 PRIZM의 기본 사용자 흐름을 재현하는 절차의 단일 원본입니다.
구현·검증 상태는 [현재 구현 현황](project-status.md)과
[PRZ-004 Evidence](../specs/PRZ-004-clean-clone-demo/evidence.md)를 함께 확인하세요.

현재 저장소는 소스와 실행 설정만 배포하는 소스 전용(source-only) 범위입니다.
컨테이너 이미지, Ollama 실행 파일과 AI 모델은 저장소에 포함하지 않습니다.

## 인증 흐름

PRIZM은 새 설치에서도 인증과 소유권 검사를 유지하며 다음 순서로 진입합니다.

| 단계 | 동작 |
|---|---|
| 회원가입 | 이메일·비밀번호로 활성 일반 `USER` 계정을 만듭니다. JWT는 발급하지 않습니다. |
| 로그인 | 가입한 계정으로 기존 JWT 로그인을 수행합니다. |
| Career Vault | JWT의 사용자를 DB에서 다시 확인하고 그 사용자의 문서와 검색 결과만 반환합니다. |

회원가입은 자체 호스팅용 최소 기능입니다. 이메일 인증·비밀번호 재설정·계정 복구와
공개 SaaS 운영 보호는 포함하지 않습니다. 자동화 검증용 one-time demo `USER`
bootstrap은 회원가입과 별도로 유지됩니다.

이 절차는 전체 흐름 검증 commit
`25d09e9eee9837cf4a63d7461699825ff22743e2`의 서로 다른 두 clean clone에서
검증했습니다. 전체 흐름을 실행한 commit의 자동 검증은
`339 PASS / 18 SKIP / 0 FAIL`이었습니다. 두 번째 환경에서는 빈 문서 목록을
API로 확인했지만 브라우저로 직접 관찰하지 않아 해당 UI 항목은 `NOT_RUN`입니다.
이후 Windows·Linux 경로 처리 교정과 GitHub CI를 통과해 PR #25로 `main`에
통합했습니다. commit별 검증 범위는
[PRZ-004 Evidence](../specs/PRZ-004-clean-clone-demo/evidence.md)를 확인하세요.

## 이 절차로 확인하는 것

- 고유한 Compose project와 새 PostgreSQL·pgvector volume을 사용합니다.
- 브라우저에서 회원가입 뒤 기존 로그인으로 정상 JWT를 발급하는지 확인할 수 있습니다.
- 한 번만 활성화하는 demo `USER`를 만든 뒤 bootstrap을 다시 끕니다.
- 실제 사람이 아닌 PRIZM 자체 합성 TXT와 text-layer PDF를 업로드합니다.
- 호스트 Ollama의 `bge-m3`로 임베딩한 뒤 두 문서가 검색 대상 버전
  (active version)으로 전환되는지 확인합니다.
- TXT 결과의 `TEXT_CHUNK`, PDF 결과의 `PAGE`와 페이지 번호를 확인합니다.
- 브라우저에서 로그인, 문서 상세, PDF 원문, 검색과 로그아웃을 확인합니다.

## 이 절차가 확인하지 않는 것

- 이메일 인증, 비밀번호 재설정, refresh token과 OIDC
- 외부 네트워크에 공개하는 운영용 multi-user Compose와 공개 SaaS 보호
- 이 PostgreSQL Quickstart에서 OpenSQL과 Ollama를 함께 사용하는 전체 사용자 흐름
- 실제 OpenSQL·OpenProxy 단일 서버 환경과 대회 범위에서 제외한 다중 노드 구성
- MCP, CareerFact, portfolio 생성

이 Quickstart의 PostgreSQL·pgvector 성공을 OpenSQL 성공으로 기록하면 안 됩니다.
별도 OpenSQL direct `5432` 검증 결과는
[PRZ-005 Evidence](../specs/PRZ-005-opensql-ollama-e2e/evidence.md)를 따릅니다.

## 사전 준비

- Git
- Docker Desktop과 Docker Compose
- Java 17
- Node `22.17.0`과 npm `10.9.2`
- 호스트에서 실행 중인 Ollama `0.32.3`
- 감사된 manifest의 `bge-m3:latest`

`bge-m3`가 없다면 사용자가 Ollama upstream에서 직접 준비합니다.

```powershell
ollama pull bge-m3
```

모델 이름에 `latest`가 들어가므로 이름만 같다고 byte 단위로 동일하다고 가정하지
않습니다. 다음 사전 검사는 실제 manifest identity와 임베딩 1024차원을 확인합니다.

## 1. 도구·모델·포트 확인

아래 포트는 예시입니다. 이미 사용 중이면 다른 빈 포트 세 개를 고르되 이후 모든
명령에서 같은 값을 사용하세요.

```powershell
node scripts/check-clean-clone-prerequisites.mjs `
  --db-port 15433 `
  --backend-port 18081 `
  --frontend-port 15174
```

검사는 설치된 버전, Docker Engine·Compose, 기존 PRIZM 이름의 project·container·
volume, Ollama와 모델 identity, 선택한 포트를 읽기만 합니다. 소프트웨어를
설치하거나 PATH를 바꾸거나 기존 Docker 데이터를 삭제하지 않습니다.

뒤에서 사용하는 Compose wrapper도 같은 탐색 방법을 쓰므로 Docker가 PATH 밖의
일반 설치 위치에 있어도 시스템 PATH를 바꾸지 않습니다.

## 2. 안전한 로컬 설정 생성

```powershell
node scripts/prepare-clean-clone-demo-env.mjs `
  --db-port 15433 `
  --backend-port 18081 `
  --frontend-port 15174
```

이 명령은 다음 작업만 수행합니다.

- 무작위 suffix를 붙인 고유 `COMPOSE_PROJECT_NAME`을 만듭니다.
- JWT, DB와 demo 비밀번호를 안전한 난수로 생성합니다.
- demo 역할을 설정할 수 없도록 고정된 `USER` bootstrap을 한 번만 켭니다.
- 생성 비밀값을 터미널에 출력하지 않습니다.
- 기존 `.env`가 있으면 덮어쓰지 않고 실패합니다.

`.env`와 뒤에서 만들 `local/`은 Git ignore 대상입니다. POSIX 파일시스템에서는
`.env`를 mode `0600`으로 만듭니다. Windows는 POSIX mode bit가 아니라 NTFS ACL을
사용하므로 같은 mode 값을 보장하지 않습니다. `.env`를 Git에 추가하거나
공유하지 마세요.

## 3. 합성 TXT·PDF 만들기

```powershell
node scripts/generate-clean-clone-demo-fixtures.mjs
```

생성물은 ignored `local/clean-clone-demo/` 아래에만 저장됩니다. 실제 인물·회사·
경력·성과에서 가져오지 않은 first-party 합성 자료이며, manifest의 SHA-256으로
업로드 전 변조 여부를 확인합니다.

## 4. Compose 구성 확인과 최초 기동

일반 `docker compose config`는 치환된 비밀번호를 화면에 표시할 수 있습니다.
공개 절차에서는 출력 없이 유효성만 확인하는 `--quiet`를 사용합니다.

```powershell
node scripts/run-clean-clone-compose.mjs config --quiet
node scripts/run-clean-clone-compose.mjs up -d --build
node scripts/run-clean-clone-compose.mjs ps
Invoke-RestMethod http://127.0.0.1:18081/actuator/health
```

wrapper는 현재 PowerShell의 `COMPOSE_PROJECT_NAME`이나 `COMPOSE_FILE`보다
`.env`에 생성된 고유 project, 저장소의 `compose.yaml`과 `.env`를 명시적으로
사용합니다. 사용자가 project·file·env file을 덮어쓰는 옵션은 거부합니다.

health가 아직 준비되지 않았다면 몇 초 기다린 뒤 다시 확인합니다. 최초 기동에서
demo `USER`가 생성됩니다. 고정된 email은 합성 로컬 계정이며 비밀번호는 ignored
`.env`에만 있습니다. `SYSTEM_ADMIN` bootstrap과 동시에 켜면 애플리케이션이
계정을 쓰기 전에 시작을 거부합니다.

## 5. One-time bootstrap 끄기

계정이 생성된 뒤 즉시 bootstrap을 끄고 backend를 새 설정으로 다시 만듭니다.

```powershell
node scripts/prepare-clean-clone-demo-env.mjs --disable-bootstrap
node scripts/run-clean-clone-compose.mjs up -d --no-deps --force-recreate backend
Invoke-RestMethod http://127.0.0.1:18081/actuator/health
```

검증 도구는 이 값이 실제로 `false`가 아니면 로그인 정보를 전송하지 않습니다.

## 6. API 전체 흐름 검증

```powershell
node scripts/verify-clean-clone-demo.mjs
```

검증 도구는 loopback 주소(`localhost`, `127.0.0.1`, `::1`)만 허용하며 HTTP
redirect를 따라가지 않습니다. 로그인 직후 문서 목록이 비어 있는지도 확인하므로
과거 DB volume을 실수로 재사용한 환경은 실패합니다. 성공하면 다음을 실제로
확인한 것입니다.

1. demo 계정이 `USER` 역할로 로그인됨
2. TXT와 PDF 업로드
3. 각 새 version의 `ACTIVE` 전환
4. TXT `TEXT_CHUNK` marker 검색
5. PDF `PAGE` marker와 유효한 page number 검색
6. token 없는 보호 경로 요청의 `401`

비밀번호와 JWT는 성공·실패 출력에 포함하지 않습니다.

## 7. 브라우저 UI 확인

브라우저에서 `http://localhost:15174`를 엽니다. 기본 화면에서 이메일·비밀번호와
비밀번호 확인을 입력해 가입합니다. 성공하면 로그인 화면으로 전환되며, 같은
이메일·비밀번호로 로그인하면 기존 JWT를 발급한 뒤 문서 보관함으로 이동합니다.

6단계의 API 자동 검증은 별도의 `demo@prizm.local` bootstrap 계정을 사용합니다.
ignored `.env`의 demo email과 password를 자동 로그인 검증에 사용할 때에도
비밀번호를 명령 출력, 스크린샷이나 Evidence에 복사하지 마세요.

다음 시험표를 순서대로 확인합니다.

- 최초 화면이 회원가입이며 성공 뒤 로그인 화면으로 전환되는가
- 로그인 화면에서 회원가입으로 돌아갈 수 있고 기존 로그인 뒤 보관함으로 들어가는가
- 합성 TXT와 PDF 두 문서가 목록에 보이는가
- 두 문서의 상세와 `ACTIVE` version을 확인할 수 있는가
- PDF 원문을 열 수 있는가
- 두 합성 marker를 Career Evidence에서 검색할 수 있는가
- 로그아웃 뒤 보호 화면이 다시 로그인으로 돌아가는가

## 현재 제한사항

- 기본 Compose는 `127.0.0.1`에만 바인딩된 자체 호스팅 환경을 전제로 합니다.
- 계정과 문서는 브라우저 저장소가 아니라 PostgreSQL과 Docker volume에
  저장됩니다. 같은 volume을 다시 사용하면 이전 데이터도 유지됩니다.
- 회원가입에는 이메일 인증·비밀번호 재설정·계정 복구와 공개 서비스용 abuse
  protection이 없습니다.
- Compose가 Ollama나 `bge-m3`를 설치하지 않습니다. 호스트에서 별도로 준비해야
  하며, 모델 가중치와 cache는 PRIZM 배포물에 포함되지 않습니다.
- 회원가입은 JWT를 발급하지 않습니다. 기존 로그인 뒤 보안·소유권 계약이 적용됩니다.

## 8. 안전한 종료

```powershell
node scripts/run-clean-clone-compose.mjs down
```

이 명령은 현재 고유 project의 컨테이너와 network만 중지합니다. DB와 runtime
volume은 삭제하지 않습니다. 기존 volume이나 다른 PRIZM project를 삭제하는
명령은 이 Quickstart에 포함하지 않습니다.

## 추가 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
node --test scripts/clean-clone-demo.test.mjs
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend audit --json
npm.cmd --prefix frontend audit --omit=dev --json
node scripts/run-clean-clone-compose.mjs config --quiet
node scripts/verify-oss-readiness.mjs
node scripts/verify-sbom.mjs
git diff --check
```

Windows에서 안전한 파일 삭제 primitive인 `SecureDirectoryStream` 관련 test가
환경 조건으로 건너뛰면 결과는 `SKIPPED`입니다. `PASS`로 바꾸지 않습니다.
통합 test의 PostgreSQL·pgvector 결과도 OpenSQL 결과가 아닙니다.

## 공개·배포 경계

PRIZM은 현재 Apache-2.0 소스 전용(source-only) 배포입니다. 자세한 범위는
[LICENSE](../LICENSE), [NOTICE](../NOTICE)와 [SBOM 안내](../sbom/README.md)를
따릅니다.
