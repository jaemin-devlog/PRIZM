# PRIZM 로컬 빠른 시작

이 문서는 Windows PowerShell에서 PRIZM을 실행하고, 내 TXT/PDF 문서를 업로드해
경력 근거를 검색하는 가장 짧은 경로를 안내합니다. 일반 사용자 흐름을 먼저
설명하고, MCP 연결과 유지관리자용 새 설치 환경 검증은 뒤에서 다룹니다.

현재 저장소는 소스와 실행 설정만 배포합니다. 컨테이너 이미지, Ollama 실행 파일과
AI 모델은 포함하지 않습니다. 구현·검증 범위는 [현재 구현 현황](project-status.md)을
확인하세요.

## 가장 빠르게 시작하기

다음 도구가 필요합니다.

- Git
- Docker Desktop과 Docker Compose. 명령만 설치된 상태가 아니라 Docker Engine도 실행 중이어야 함
- 호스트에서 실행 중인 Ollama와 `bge-m3`

Compose 빌드 이미지가 Java 17과 Node `22.17.0`을 사용하므로 일반 사용 흐름에서는
호스트 Java와 Node를 따로 설치하지 않아도 됩니다. 현재 감사된 실행 기준은 Ollama
`0.32.3`과 manifest가 확인된 `bge-m3:latest`입니다. 다른 Ollama·모델 버전을 같은
결과로 간주하지 않습니다.

### 1. 저장소 받기

```powershell
git clone https://github.com/jaemin-devlog/PRIZM.git
Set-Location PRIZM
```

### 2. 로컬 환경 설정

예제 파일을 복사합니다.

```powershell
if (Test-Path -LiteralPath .env) { throw '.env already exists' }
Copy-Item .env.example .env
```

`.env`에서 최소한 다음 세 값을 각기 다른 로컬 비밀값으로 바꿉니다.

- `PRIZM_JWT_SECRET`: 32자 이상
- `PRIZM_DB_PASSWORD`
- `PRIZM_FLYWAY_PASSWORD`

기본 포트를 바꾼다면 `SERVER_PORT`, `PRIZM_FRONTEND_PORT`, `PRIZM_DB_PORT`와
`PRIZM_CORS_ALLOWED_ORIGINS`도 함께 맞춥니다. `PRIZM_JWT_SECRET`은 서버가 JWT를
서명하는 값이고 두 DB 비밀번호는 애플리케이션과 Flyway가 DB에 접속하는 값입니다.
셋 다 브라우저에서 가입할 이메일·비밀번호가 아닙니다.

`.env`는 Git ignore 대상입니다. 비밀번호, JWT와 `.env` 내용을 저장소, 이슈, 로그나
스크린샷에 올리지 마세요.

기존 Docker 환경과 데이터까지 분리해서 실행해야 할 때만 `.env`의
`COMPOSE_PROJECT_NAME`을 다른 고유한 값으로 바꿀 수 있습니다. 일반 Quickstart에서는
기본값을 그대로 사용해도 됩니다. 분리한 환경을 `down` 뒤 다시 올릴 때는 같은 값을
유지해야 기존 volume의 계정과 문서를 다시 사용합니다.

### 3. Ollama 준비

Ollama를 시작한 뒤 모델을 준비합니다.

```powershell
ollama pull bge-m3
```

### 4. PRIZM 실행

`config --quiet`은 치환된 비밀값을 화면에 출력하지 않고 Compose 구성을 검사합니다.

```powershell
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

health 응답의 `status`가 정확히 `UP`이 될 때까지 잠시 기다렸다가 마지막 명령을
다시 실행합니다. `UP` 전에 회원가입이나 업로드를 시작하지 마세요.

`docker` 또는 `ollama`를 명령으로 찾지 못하면 해당 도구의 설치 여부를 확인하고,
설치 뒤 PowerShell을 다시 연 다음 PATH가 반영됐는지 `docker --version` 또는
`ollama --version`으로 확인합니다. `docker` 명령은 있으나 서버에 연결할 수 없으면
Docker Desktop을 열어 Docker Engine이 실행 중인지 확인합니다. `ollama` 명령은
있으나 모델 요청이 실패하면 Ollama가 실행 중인지 확인한 뒤 다시 시도합니다.
유지관리자용 격리 검증에서는 아래 [새 설치 환경 검증](#재현-가능한-새-설치-환경-검증)의
사전 검사와 Compose wrapper로 PATH 밖의 일반 Docker 설치 위치도 확인할 수 있습니다.

### 5. 회원가입하고 로그인하기

브라우저에서 `http://localhost:5173`을 엽니다. 이메일과 비밀번호로 일반 `USER`
계정을 만든 뒤 같은 계정으로 로그인합니다. 웹에서는 로그인 뒤 발급되는 JWT를
직접 복사하거나 관리할 필요가 없습니다.

PRIZM은 로그인한 사용자를 DB에서 다시 확인하고, 그 사용자에게 속한 문서와 검색
결과만 반환합니다. 회원가입은 자체 호스팅용 최소 기능이며 이메일 인증, 비밀번호
재설정, 계정 복구와 공개 SaaS 운영 보호는 포함하지 않습니다.

### 6. 문서 업로드하기

왼쪽 메뉴의 **문서 업로드**에서 UTF-8 TXT 또는 텍스트가 포함된 PDF를 등록합니다.
업로드 뒤 왼쪽 메뉴의 **문서 보관함**을 열고, 업로드할 때 선택한 문서 유형 폴더에서
문서를 확인합니다. 처리가 끝나면 목록 카드에는 **검색 준비 완료**, 문서 상세에는
**검색에 사용 중**, 현재 version 행에는 **현재 · ACTIVE**가 표시됩니다. 세 표시는
모두 처리가 완료되어 그 version이 현재 검색 대상이라는 같은 상태를 서로 다른
화면에서 표현합니다. 새 version 처리에 실패하면 이전 `ACTIVE` version을 유지합니다.

### 7. 경력 근거 검색하기

왼쪽 메뉴의 **내 경험 찾기**에서 찾을 내용을 입력합니다. PRIZM은 현재 사용자의
`ACTIVE` 문서에서 관련 원문을 찾고, 문서명과 TXT 구간 또는 PDF 페이지를 함께
보여 줍니다. 결과의 **문서에서 보기**를 누르면 TXT는 해당 문서·version의 기존
문서 상세로, PDF는 해당 문서·version의 페이지 원문으로 이동합니다. 관련 원문을
찾지 못하면 경험이 없다고 판정하지 않고 결과 없음으로 표시합니다.

### 8. 종료하기

```powershell
docker compose --env-file .env down
```

이 명령은 현재 Compose project의 컨테이너와 network를 중지하며 volume은 삭제하지
않습니다. 같은 volume으로 다시 실행하면 계정과 문서가 유지됩니다.

## MCP로 경력 근거 검색하기

웹 사용자는 JWT를 직접 다루지 않습니다. MCP client를 연결할 때만 기존 로그인
API에서 활성 `ROLE_USER`의 JWT를 받은 뒤 Bearer header에 넣습니다.

PowerShell에서는 비밀번호를 명령 기록에 남기지 않도록 자격 증명 입력창을 사용할 수
있습니다.

```powershell
$credential = Get-Credential -Message 'PRIZM USER 계정'
$loginBody = @{
  email = $credential.UserName
  password = $credential.GetNetworkCredential().Password
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body $loginBody

$userJwt = $login.accessToken
```

MCP client마다 설정 이름은 다를 수 있지만 연결 값은 다음과 같습니다.

```json
{
  "transport": "streamable-http",
  "url": "http://127.0.0.1:8080/mcp",
  "headers": {
    "Authorization": "Bearer <USER_JWT>"
  }
}
```

- 요청 주소(endpoint): `POST /mcp`
- 통신 규격(protocol): `2025-11-25`
- 호출할 도구(tool): `search_career_evidence`
- 입력값: `{"query":"..."}`
- 인증: `POST /api/auth/login`에서 받은 활성 `ROLE_USER` JWT

MCP client는 연결 초기화(initialize)와 도구 목록 조회(`tools/list`)를 마친 뒤
도구를 호출해야 합니다. MCP는 별도 검색 정책이나 데이터 경로를 두지 않고 기존
경력 근거 검색을 읽기 전용으로 사용합니다. 따라서 현재 사용자에게 속한
`ACTIVE` 버전만 검색합니다.

현재 사용하는 Spring AI/MCP Java SDK `2.0.0` stateless server는 정상적인
`notifications/initialized`를 처리한 뒤에도 등록된 notification handler가 없다는
warning을 남길 수 있습니다. initialize, `tools/list`와 도구 호출이 성공했다면 이
warning 자체는 요청 실패를 뜻하지 않습니다. SDK를 올리거나 warning을 숨길 때는
별도 dependency 검증이 필요합니다.

기본 backend 포트를 바꿨다면 로그인과 MCP URL의 `8080`도 같은 값으로 바꿉니다.
JWT를 설정 파일, shell history, 로그나 문서에 저장하지 마세요.

단일 서버 OpenSQL·OpenProxy 환경에서는 Flyway direct `:5432`, 애플리케이션 실행 경로인
OpenProxy `:6432/opensql`, Ollama `bge-m3`, 공식 Java MCP Client를 사용해 REST/MCP
결과 일치와 사용자별·`ACTIVE` 버전 격리를 검증했습니다. 자세한 근거는
[PRZ-015 검증 기록](../specs/PRZ-015-mcp-career-evidence-search/evidence.md)을
확인하세요. 이 결과의 범위는 OpenSQL 단일 서버와 OpenProxy single-Primary
연결 경로입니다.

## 재현 가능한 새 설치 환경 검증

이 절차는 일반 사용법이 아니라 기여자와 유지관리자가 격리된 새 환경에서
PRIZM의 핵심 동작을 재현할 때 사용합니다. 과거 두 새 설치 환경의 명령, 환경과 결과는
[PRZ-004 검증 기록](../specs/PRZ-004-clean-clone-demo/evidence.md)에 보존돼 있습니다.

### 이 절차로 확인하는 것

- 고유한 Compose project와 새 PostgreSQL·pgvector volume 사용
- 검증 때마다 새 `USER` 회원가입과 기존 JWT 로그인
- PRIZM 자체 합성 TXT와 텍스트 레이어가 있는 PDF 업로드
- Ollama `bge-m3` 임베딩과 두 문서의 `ACTIVE` 전환
- TXT `TEXT_CHUNK`, PDF `PAGE`와 페이지 번호 검색
- 브라우저 로그인, 문서 상세, PDF 원문, 검색과 로그아웃

### 검증 결과를 해석하는 범위

- 이 절차의 DB 결과는 PostgreSQL 16+pgvector 새 설치 환경에 해당합니다.
- OpenSQL direct 연결과 OpenProxy single-Primary 결과는 이 절차에서 다시 실행하지
  않으며 각 PRZ의 기록된 source·환경·명령을 기준으로 확인합니다.
- MCP 연결 값은 안내하지만, OpenSQL·OpenProxy MCP E2E의 현재 재실행 절차는
  아닙니다.

PostgreSQL·pgvector 성공을 OpenSQL 성공으로 기록하면 안 됩니다. OpenSQL direct
`5432` 검증 결과는
[PRZ-005 검증 기록](../specs/PRZ-005-opensql-ollama-e2e/evidence.md)을 따릅니다.

### 사전 준비

- Git
- Docker Desktop과 Docker Compose
- Java 17
- Node `22.17.0`과 npm `10.9.2`
- 호스트에서 실행 중인 Ollama `0.32.3`
- 감사된 manifest의 `bge-m3:latest`

모델 이름에 `latest`가 들어가므로 이름만 같다고 byte 단위로 동일하다고 가정하지
않습니다. 아래 사전 검사는 manifest identity와 임베딩 1024차원을 함께 확인합니다.

### 1. 도구·모델·포트 확인

아래 포트는 예시입니다. 이미 사용 중이면 다른 빈 포트 세 개를 고르고 이후 모든
명령에서 같은 값을 사용하세요.

```powershell
node scripts/check-clean-clone-prerequisites.mjs `
  --db-port 15433 `
  --backend-port 18081 `
  --frontend-port 15174
```

검사는 설치된 버전, Docker Engine·Compose, 기존 PRIZM 이름의 project·container·
volume, Ollama와 모델 identity, 선택한 포트를 읽기만 합니다. 소프트웨어를 설치하거나
PATH를 바꾸거나 기존 Docker 데이터를 삭제하지 않습니다.

뒤에서 사용하는 Compose wrapper도 같은 탐색 방법을 쓰므로 Docker가 PATH 밖의
일반 설치 위치에 있어도 시스템 PATH를 바꾸지 않습니다.

### 2. 안전한 검증 환경 만들기

```powershell
node scripts/prepare-clean-clone-demo-env.mjs `
  --db-port 15433 `
  --backend-port 18081 `
  --frontend-port 15174
```

이 명령은 다음 작업만 수행합니다.

- 무작위 suffix를 붙인 고유 `COMPOSE_PROJECT_NAME` 생성
- JWT와 두 DB 비밀번호를 안전한 난수로 생성
- 생성 비밀값을 터미널에 출력하지 않음
- 기존 `.env`가 있으면 덮어쓰지 않고 실패

`.env`와 뒤에서 만들 `local/`은 Git ignore 대상입니다. POSIX 파일시스템에서는
`.env`를 mode `0600`으로 만듭니다. Windows는 POSIX mode bit가 아니라 NTFS ACL을
사용하므로 같은 mode 값을 보장하지 않습니다. `.env`를 Git에 추가하거나 공유하지
마세요.

### 3. 합성 테스트용 TXT·PDF 만들기

```powershell
node scripts/generate-clean-clone-demo-fixtures.mjs
```

생성물은 ignored `local/clean-clone-demo/` 아래에만 저장됩니다. 실제 인물·회사·
경력·성과에서 가져오지 않은 first-party 합성 자료이며, manifest의 SHA-256으로 업로드
전 변조 여부를 확인합니다.

### 4. Compose 구성 확인과 기동

일반 `docker compose config`는 치환된 비밀번호를 화면에 표시할 수 있습니다. 검증
절차에서는 출력 없이 유효성만 확인하는 `--quiet`를 사용합니다.

```powershell
node scripts/run-clean-clone-compose.mjs config --quiet
node scripts/run-clean-clone-compose.mjs up -d --build
node scripts/run-clean-clone-compose.mjs ps
Invoke-RestMethod http://127.0.0.1:18081/actuator/health
```

wrapper는 현재 PowerShell의 `COMPOSE_PROJECT_NAME`이나 `COMPOSE_FILE`보다 `.env`에
생성된 고유 project, 저장소의 `compose.yaml`과 `.env`를 명시적으로 사용합니다.
project·file·env file을 덮어쓰는 옵션은 거부합니다.

health 응답의 `status`가 정확히 `UP`이 될 때까지 잠시 기다렸다가 다시 확인합니다.
서버 기동만으로 사용자 계정을 만들지는 않습니다.

### 5. API 전체 흐름 검증

```powershell
node scripts/verify-clean-clone-demo.mjs
```

검증 도구는 loopback 주소(`localhost`, `127.0.0.1`, `::1`)만 허용하며 HTTP redirect를
따라가지 않습니다. 실행할 때마다 안전한 임시 이메일·비밀번호를 메모리에서 만들고
`POST /api/auth/signup`으로 새 `USER`를 가입시킨 뒤 로그인합니다. 이 자격 증명과 JWT는
`.env`에 저장하거나 출력하지 않습니다. 가입 이메일이 이미 존재하면 과거 DB volume을
재사용한 것으로 보고 실패합니다. 성공하면 다음을 실제로 확인한 것입니다.

1. 새 계정이 `USER` 역할로 가입되고 로그인됨
2. 로그인 직후 소유 문서 목록이 비어 있음
3. TXT와 PDF 업로드
4. 각 새 버전의 `ACTIVE` 전환
5. TXT `TEXT_CHUNK` 표시 문자열 검색
6. PDF `PAGE` 표시 문자열과 유효한 페이지 번호 검색
7. token 없이 보호 경로를 요청했을 때 `401` 응답

비밀번호와 JWT는 성공·실패 출력에 포함하지 않습니다.

### 6. 브라우저 UI 확인

브라우저에서 `http://localhost:15174`를 엽니다. 기본 화면에서 이메일·비밀번호와
비밀번호 확인을 입력해 가입합니다. 성공하면 로그인 화면으로 전환되며, 같은
이메일·비밀번호로 로그인하면 기존 JWT를 발급한 뒤 문서 보관함으로 이동합니다.

5단계의 API 자동 검증 계정은 브라우저에서 직접 만든 계정과 별개입니다. 검증 도구가
실행 중에만 임시 자격 증명을 보유하므로 이를 복사하거나 관리할 단계는 없습니다.
따라서 새 브라우저 계정에서는 helper가 업로드한 문서나 그 검색 결과가 보여서는
안 됩니다. 이는 실패가 아니라 owner isolation의 음성 검증입니다.

브라우저에서 양성 흐름도 확인하려면 새 계정으로 로그인한 채 **문서 업로드**에서
`local/clean-clone-demo/prizm-clean-clone-synthetic.txt`와
`local/clean-clone-demo/prizm-clean-clone-synthetic.pdf`를 각각 직접 업로드합니다.
각 문서는 선택한 문서 유형 폴더에서 확인하고 현재 version이 `ACTIVE`가 된 뒤
검색합니다. 자동 API 검증과 수동 브라우저 검증이 동일한 파일을 사용하더라도 문서
소유권은 각 업로드를 수행한 USER에게 따로 유지됩니다.

다음 항목을 순서대로 확인합니다.

- 최초 화면이 회원가입이며 성공 뒤 로그인 화면으로 전환되는가
- 로그인 화면에서 회원가입으로 돌아갈 수 있고 기존 로그인 뒤 보관함으로 들어가는가
- 로그인 직후 문서와 두 합성 표시 문자열의 검색 결과가 모두 0건인가
- 브라우저 USER가 직접 업로드한 합성 TXT와 PDF가 해당 문서 유형 폴더에 보이는가
- 목록의 **검색 준비 완료**, 상세의 **검색에 사용 중**, version의 **현재 · ACTIVE**를 확인할 수 있는가
- TXT 검색 결과에서 해당 document/version의 문서 상세를 열 수 있는가
- PDF 검색 결과에서 해당 document/version의 페이지 원문을 열 수 있는가
- 두 합성 표시 문자열을 경력 근거 검색에서 찾을 수 있는가
- 로그아웃 뒤 보호 화면이 다시 로그인으로 돌아가는가

### 7. 안전한 종료

```powershell
node scripts/run-clean-clone-compose.mjs down
```

이 명령은 현재 고유 project의 컨테이너와 network만 중지합니다. DB와 실행용 volume은
삭제하지 않습니다. 기존 volume이나 다른 PRIZM project를 삭제하는 명령은 이 문서에
포함하지 않습니다.

## 유지관리자 추가 검증

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

Windows에서 안전한 파일 삭제 primitive인 `SecureDirectoryStream` 관련 test가 환경
조건으로 건너뛰면 결과는 `SKIPPED`입니다. `PASS`로 바꾸지 않습니다. 통합 test의
PostgreSQL·pgvector 결과도 OpenSQL 결과가 아닙니다.

## 현재 제한사항

- 기본 Compose는 `127.0.0.1`에만 바인딩된 자체 호스팅 환경을 전제로 합니다.
- 계정과 문서는 브라우저 저장소가 아니라 PostgreSQL과 Docker volume에 저장됩니다.
- Compose는 Ollama나 `bge-m3`를 설치하지 않습니다. 모델 가중치와 cache도 PRIZM
  배포물에 포함되지 않습니다.
- 회원가입은 JWT를 발급하지 않습니다. 로그인 뒤 기존 인증과 사용자별 데이터 분리 방식이 적용됩니다.

## 공개·배포 경계

PRIZM은 현재 Apache-2.0으로 소스코드와 실행 설정만 배포합니다. 자세한 범위는
[LICENSE](../LICENSE), [NOTICE](../NOTICE)와 [SBOM 안내](../sbom/README.md)를
따릅니다.
