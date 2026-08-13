# PRZ-009 — 경력 키워드 맵 Evidence

## 판정

`IMPLEMENTED_UNVERIFIED`

- 시작 기준 source: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`
- 구현 source: `d52c6d01a3bef916e80a3c983a43c7b1fad1139b`
- `main` 통합 merge: `5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`
- 검증일: `2026-08-10`
- 환경: Windows PowerShell, Java 17, Gradle 9.5.1, Node/npm,
  Docker Desktop, PostgreSQL+pgvector Testcontainers, Codex In-app Browser

정규화·category·세 순위 기준, 상위 15개 구름과 순위 밖 목록, 문서별 근거 묶기,
TXT/PDF 원본 위치 이동까지 구현했다. 전체 PostgreSQL integration suite와 최종 감사도
완료했다. 다만 OpenSQL opt-in integration은 전용 대상이 구성되지 않아 `NOT_RUN`이며,
PostgreSQL 결과를 OpenSQL 증적으로 확장하지 않기 위해 전체 상태는 `VERIFIED`로
판정하지 않는다.

## 검증한 수직 흐름

```text
PostgreSQL의 owner·ACTIVE 이력서·포트폴리오 조회
↓
overlap 제거와 canonical keyword 집계
↓
category·세 정렬 기준 API 응답
↓
브라우저 keyword 선택과 문서별 근거 확인
↓
TXT 강조·PDF page/search 원본 이동
```

OpenSQL opt-in integration과 browser는 `NOT_RUN`이므로 전체 상태는
`IMPLEMENTED_UNVERIFIED`다.

## 구현 근거

- owner·active·문서 유형 SQL:
  [`CareerKeywordRepository`](../../src/main/java/com/prizm/careerkeyword/repository/CareerKeywordRepository.java)
- 별칭·Java 버전 정규화, category, 실제 표기 보존:
  [`CareerKeywordExtractor`](../../src/main/java/com/prizm/careerkeyword/service/CareerKeywordExtractor.java),
  [`CareerKeywordCategory`](../../src/main/java/com/prizm/careerkeyword/model/CareerKeywordCategory.java)
- 빈도·문서 수 집계와 source 근거:
  [`CareerKeywordService`](../../src/main/java/com/prizm/careerkeyword/service/CareerKeywordService.java)
- category filter, 언급 수·문서 수·균형 점수, 문서별 근거와 위치 viewer:
  [`App.tsx`](../../frontend/src/App.tsx),
  [`styles.css`](../../frontend/src/styles.css)
- owner-scoped TXT/PDF original:
  [`DocumentThumbnailService`](../../src/main/java/com/prizm/document/service/DocumentThumbnailService.java),
  [`DocumentThumbnailController`](../../src/main/java/com/prizm/document/controller/DocumentThumbnailController.java)
- PostgreSQL owner·active·별칭 집계:
  [`CareerKeywordDatabaseIntegrationTest`](../../src/integrationTest/java/com/prizm/infrastructure/CareerKeywordDatabaseIntegrationTest.java)

Flyway migration과 dependency는 추가하거나 변경하지 않았다. keyword 결과는 요청 시
active chunk에서 계산하며 별도 영구 keyword table이나 생성형 모델을 사용하지 않는다.

## 실행 결과

- **명령·검증:** `.\gradlew.bat test --no-daemon`
  - 결과: `PASS`
  - 실제 범위: 323개 중 308 pass, 기존 조건부 15 skip, 실패·오류 0
- **명령·검증:** `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.CareerKeywordDatabaseIntegrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS`
  - 실제 범위: 실제 PostgreSQL+pgvector에서 owner·active·문서 유형 격리와 canonical 별칭 집계
- **명령·검증:** 전체 `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS`
  - 실제 범위: 71개 중 68 pass, 조건부 3 skip, 실패·오류 0. OpenSQL opt-in 1개와 `SecureDirectoryStream` 미지원 시 fail-closed 계약을 확인하는 cleanup 2개가 조건부 skip
- **명령·검증:** `npm run lint` (`frontend`)
  - 결과: `PASS`
  - 실제 범위: ESLint 오류 0
- **명령·검증:** `npm run build` (`frontend`)
  - 결과: `PASS`
  - 실제 범위: TypeScript와 Vite production build 성공
- **명령·검증:** `docker compose config --quiet`
  - 결과: `PASS`
  - 실제 범위: Compose 구성 검증 성공. sandbox의 사용자 Docker 설정 파일 접근 경고는 결과에 영향 없음
- **명령·검증:** `docker compose up -d --build`
  - 결과: `PASS`
  - 실제 범위: backend/frontend 이미지 build와 db health, 컨테이너 재기동 성공
- **명령·검증:** synthetic browser 검증
  - 결과: `PASS`
  - 실제 범위: 25개 기술·10 category, 세 순위 기준, 상위 15개, 문서별 3개 PDF 근거 접기, TXT 강조 2개, PDF `#page=5&search=백엔드`, browser warning/error 0
- **명령·검증:** OpenSQL opt-in integration·browser
  - 결과: `NOT_RUN`
  - 실제 범위: `RUN_OPENSQL_TESTS`가 활성화되지 않았고 전용 대상이 구성되지 않음. PostgreSQL 결과와 분리함
- **명령·검증:** `git diff --check`와 최종 diff 감사
  - 결과: `PASS`
  - 실제 범위: whitespace 오류 0, 금지 경로 0, migration·dependency·license 변경 0, blocking finding 0

브라우저 검증에는 전용 synthetic `USER`, active TXT 1개, 3페이지 PDF source 1개를
사용했다. 검증 후 계정 1개, 문서 2개, version 2개, chunk 4개와 원본 fixture 2개를
정확한 식별자·경로로 확인해 모두 제거했다.

## 요구사항 추적

- **요구사항:** `R1` owner active 이력서·포트폴리오만 사용
  - 근거: repository SQL, 전용 PostgreSQL integration
  - 현재 판정: `PASS`
- **요구사항:** `R2` overlap 제거 빈도·문서 수
  - 근거: assembler·extractor·service unit test
  - 현재 판정: `PASS`
- **요구사항:** `R3` source와 active 원본 version 연결
  - 근거: service/controller test, synthetic browser
  - 현재 판정: `PASS`
- **요구사항:** `R4` PDF·TXT 보안 원본
  - 근거: original service/controller test, browser
  - 현재 판정: `PASS`
- **요구사항:** `R5` 상태·반응형·키보드 화면
  - 근거: React source, lint·build, browser
  - 현재 판정: 범위 검증 `PASS`; 자동 UI test 없음
- **요구사항:** `R6` 기존 검색·처리 계약 보존
  - 근거: 전체 unit·PostgreSQL integration test
  - 현재 판정: unit·전체 PostgreSQL integration `PASS`
- **요구사항:** `R7` canonical 별칭과 source 표기 보존
  - 근거: extractor/service/integration test
  - 현재 판정: `PASS`
- **요구사항:** `R8` category와 세 순위 기준
  - 근거: React source, browser
  - 현재 판정: `PASS`
- **요구사항:** `R9` document/version 근거 묶기
  - 근거: React source, browser 3개 근거 접기·펼치기
  - 현재 판정: `PASS`
- **요구사항:** `R10` PDF/TXT 위치 이동
  - 근거: browser TXT mark, PDF page/search fragment
  - 현재 판정: `PASS`

## 남은 Gate

표준 PostgreSQL 구현 범위의 전체 테스트와 감사는 완료했다. 전체 상태를 OpenSQL 범위까지
`VERIFIED`로 올리려면 별도로 구성한 OpenSQL 대상에서 opt-in integration과 필요한 browser
검증을 수행해야 한다. 현재 PostgreSQL 성공을 OpenSQL 검증으로 대체하지 않는다.
