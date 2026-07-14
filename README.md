# PRIZM

개인 커리어 문서에서 실제 경험의 원문 근거를 찾는 문서 플랫폼입니다. 등록되지 않은 경력·기술·성과를 만들지 않습니다.

현재 TXT·텍스트 PDF 업로드, 비동기 색인, 사용자별 pgvector 검색, Career Vault 기본 UI를 제공합니다.

```text
업로드 → 비동기 추출·청킹·임베딩 → ACTIVE 전환 → 원문·출처 검색
```

## 실행

```powershell
Copy-Item .env.example .env
ollama pull bge-m3
docker compose up -d
.\gradlew.bat bootRun
```

프런트엔드:

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

## 검증

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

## 문서

- [현재 구현 현황](docs/project-status.md)
- [개발 기록](docs/development-log.md)
- [장기 기획안](docs/PRIZM_최종_기획안.md)
- [OpenSQL 기술 Gate](docs/opensql-gate.md)
