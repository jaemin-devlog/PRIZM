# PRZ-023 PRIZM Project Closeout

## 상태

- lifecycle: `VERIFIED`
- final baseline main: `6e966e571793e17d7a8d8b7f6900a6094cc90961`
- branch: `PRZ-023-project-closeout`
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT → INTEGRATE`

## 목적

새 기능을 추가하지 않고 현재 `main`의 제품 범위, 구현, 검증 근거, 오픈소스 배포
준비 상태와 문서 정합성을 마지막으로 감사한다. 남은 dependency advisory와 문서 오류만
최소 범위로 정리하고, PRIZM 개발 종료 여부를 재현 가능한 결과로 판정한다.

## 범위

- `frontend/package-lock.json`의 `nanoid 3.3.16` advisory와 실제 dependency tree 확인
- 안전한 patch로 해결할 수 있을 때만 최소 lockfile 정리와 SBOM 동기화
- README, Quickstart, Architecture, Project Status, Roadmap, Spec Registry의 현재 source 정합성
- PRZ-000부터 PRZ-023까지 lifecycle, 링크, 내부 Markdown anchor와 PRZ-022 결과 경로 확인
- backend, frontend, PostgreSQL integration, clean-clone tooling, OSS readiness와 SBOM 재검증
- 인증·owner isolation·ACTIVE 보호·Worker·cleanup·Linux storage·MCP 근거의 최종 감사
- 기존 OpenSQL/OpenProxy 근거와 final main 변경 이력 비교 및 안전한 환경이 있을 때만 제한적 재실행
- 알려진 한계와 최종 Closeout 판정 기록

## 비범위

- Production 기능, 검색 알고리즘과 검색 성능 조정
- migration, DB·VM·OpenProxy·Ollama 구성 변경
- 과거 `FAIL`, `NO_GO`, `IN_PROGRESS`, `REJECTED` 판정의 소급 변경
- 이미지 전용·암호화 PDF, 이메일 인증, 비밀번호 재설정, refresh token·OIDC
- 공개 SaaS 보호, 전체 브라우저 E2E 자동화, OpenHA multi-node failover
- tag, GitHub Release와 PR merge

## 보존 계약

- USER-only JWT 인증, owner isolation과 현재 `ACTIVE` version만 검색하는 경계를 유지한다.
- Worker lease·heartbeat·fencing·recovery와 cleanup fail-closed 계약을 바꾸지 않는다.
- PostgreSQL 결과를 OpenSQL 근거로 대신하지 않고, 과거 근거와 현재 재실행을 구분한다.
- source-only Apache-2.0 배포 경계와 비밀정보·개인정보·로컬 절대 경로 비추적 원칙을 유지한다.

## 완료 조건

1. blocking correctness·security finding이 각각 0건이다.
2. 현재 제품 범위의 source, migration, test와 문서 설명이 일치한다.
3. `nanoid` advisory가 최소 안전 patch로 해결되고 lockfile·SBOM이 일치하거나, 수정 불가 이유를 명시한다.
4. backend check·unit·PostgreSQL integration과 frontend unit·lint·typecheck·build가 실패 없이 끝난다.
5. clean-clone tooling, OSS readiness, SBOM, Markdown link·anchor, JSON, diff와 민감정보 검사가 통과한다.
6. OpenSQL/OpenProxy는 실제 재실행 결과 또는 `HISTORICAL_VERIFIED_NOT_RERUN_ON_FINAL_MAIN`으로 정확히 기록한다.
7. 알려진 한계와 실행하지 않은 검사를 공개하고 최종 판정을 네 허용값 중 하나로 남긴다.
8. `PRIZM_PROJECT_COMPLETE`일 때만 Closeout 파일을 커밋·push하고 `main` 대상 PR을 만들며 merge하지 않는다.
