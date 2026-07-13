# PRIZM

> 개인의 커리어 문서를 쌓아 두고, 새로운 지원 기회에 활용할 실제 경험과 원문 근거를 찾는 문서 플랫폼

PRIZM은 이력서, 포트폴리오, 프로젝트 보고서, 학교 과제, 대외활동 기록처럼 사용자가 직접 등록한 자료를 검색 가능한 기반으로 정리하는 프로젝트입니다.

AI가 새로운 경력, 기술, 성과나 수치를 만들어 내는 것이 아니라, 등록된 문서 안에서 활용 가능한 경험과 그 근거를 찾는 것을 목표로 합니다. 근거를 찾지 못하면 사실 여부를 단정하지 않고 **현재 PRIZM에 등록된 자료에서는 근거를 찾지 못했다**고 안내해야 합니다.

## 현재 개발 중인 기능

- TXT 문서 업로드와 로컬 원본 파일 저장
- SHA-256 해시를 포함한 문서·버전 메타데이터 관리
- 비동기 청크 분할과 Ollama `bge-m3` 1024차원 임베딩
- pgvector exact cosine 기반 자연어 검색
- 처리 완료된 활성 버전만 검색하는 구조
- Worker 재시도, lease 복구, claim-version fencing
- JWT 로그인과 DB 기반 사용자 상태·역할 재검증

현재 처리 흐름은 다음과 같습니다.

```text
TXT 업로드
→ QUARANTINED
→ 비동기 처리
→ 청크·임베딩 생성
→ ACTIVE
→ 원문 근거 검색
```

새 버전을 처리하다 실패해도 기존 ACTIVE 버전은 계속 검색되며, 새 버전의 모든 청크가 준비된 뒤에만 검색 대상을 원자적으로 교체합니다.

## 현재 단계

PRIZM은 아직 플랫폼 기반을 만드는 개발 단계입니다. 현재는 TXT 등록과 검색 경로만 구현되어 있으며, 프런트엔드는 프로젝트 방향을 안내하는 초기 화면입니다.

다음 단계에서는 사용자별 문서 소유권과 데이터 격리를 먼저 추가할 예정입니다. 이후 PDF·DOCX·PPTX 처리, 커리어 근거 카드, 채용공고와 실제 경험의 연결 기능을 작은 단위로 확장합니다.

## 기술 기반

- Java 17, Spring Boot, React, TypeScript, Vite
- PostgreSQL 16, pgvector 0.8.2, Flyway
- Ollama `bge-m3`, Docker Compose, Testcontainers

PostgreSQL·pgvector와 실제 Ollama 환경에서 자동 통합 테스트로 검증했습니다. OpenSQL, OpenProxy, OpenHA는 별도 실제 환경 검증이 아직 필요합니다.

## 실행과 검증

```powershell
Copy-Item .env.example .env
ollama pull bge-m3
docker compose up -d
.\gradlew.bat bootRun
```

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
```

개발 과정과 검증 기록은 [development-log.md](docs/development-log.md)에 남깁니다.
