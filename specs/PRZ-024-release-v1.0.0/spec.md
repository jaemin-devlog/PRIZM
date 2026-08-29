# PRZ-024 PRIZM v1.0.0 소스 릴리스

## 상태

- lifecycle: `VERIFIED`
- baseline main: `8ba056b5c94228d1782e306a1310b84a8a063493`
- release source commit: `76a87482a70d89b3bb5c7dabed69dff4764e04bb`
- branch: `PRZ-024-release-v1.0.0` — 병합 뒤 로컬·원격 삭제
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT → INTEGRATE → RELEASE`

## 목적

프로젝트 Closeout이 끝난 PRIZM을 첫 정식 버전 `v1.0.0`으로 공개한다. 제품 기능을
바꾸지 않고 애플리케이션·SBOM 버전, 첫 외부 배포에 필요한 보안·지원 운영 경계와
GitHub Release를 일치시킨다.

## 범위

- backend와 frontend 버전을 `1.0.0`으로 맞추고 SBOM·checksum을 재생성한다.
- GitHub Private Vulnerability Reporting을 활성화하고 실제 비공개 신고 경로를 문서화한다.
- 최소 지원·유지관리 정책과 `버그 보고`·`기능 제안`·`문서 개선` Issue Form, PR template을 추가한다.
- OSS readiness가 첫 외부 배포 필수 파일을 계속 검사하도록 보강한다.
- 릴리스 준비 PR을 `main`에 병합한 정확한 commit에 annotated tag `v1.0.0`을 만든다.
- GitHub Release `PRIZM v1.0.0 — 첫 번째 소스 릴리스`를 정식 공개한다.

## 비범위

- Production Java·React 기능, API, 검색 알고리즘과 UI 변경
- Flyway migration, DB·VM·OpenSQL·OpenProxy·Ollama 설정 변경
- JAR, frontend `dist`, container image, Ollama binary, 모델 가중치와 OpenSQL 자산 배포
- 과거 OpenSQL 근거 재실행 또는 PostgreSQL 결과로 대체
- signed tag, package registry 배포와 자동 릴리스 workflow 추가

## 보존 계약

- Apache-2.0 source-only 배포 경계를 유지한다.
- 비밀정보·개인정보·업로드 원본·DB volume·모델 파일을 릴리스에 포함하지 않는다.
- `main`을 유일한 장기 브랜치로 유지하며 임시 브랜치는 병합 뒤 안전 확인 후 삭제한다.
- 기존 PRZ 상태와 `NOT_RUN`·historical evidence를 소급 변경하지 않는다.

## 완료 조건

1. backend·frontend·두 CycloneDX root component가 모두 `1.0.0`이고 옛 개발 버전이 릴리스 메타데이터에 남지 않는다.
2. SBOM 재생성·checksum·dependency audit와 OSS readiness가 통과한다.
3. Private Vulnerability Reporting이 실제로 활성화되고 SECURITY가 공개 Issue 대신 비공개 경로를 안내한다.
4. SUPPORT·유지관리 정책과 Issue/PR template이 존재하고 서로 모순되지 않는다.
5. backend check와 frontend unit·lint·typecheck·build가 실패 없이 끝난다.
6. blocking correctness·security·license finding이 0건이고 Production source·migration 변경이 0건이다.
7. 릴리스 준비 PR의 최종 CI·OSS Readiness가 통과하고 `main`에 병합된다.
8. annotated `v1.0.0` tag가 병합된 정확한 source commit을 가리킨다.
9. GitHub Release가 draft·prerelease가 아닌 source-only 정식 릴리스로 공개된다.
