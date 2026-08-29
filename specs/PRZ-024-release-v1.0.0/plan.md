# PRZ-024 Plan

1. 기존 tag·Release, 최종 `main`, CI, 버전, SBOM과 PRZ-002 첫 배포 Gate를 확인한다.
2. PRZ-024 범위와 source-only 완료 조건을 Registry에 연결한다.
3. Private Vulnerability Reporting을 활성화하고 실제 상태를 다시 조회한다.
4. SECURITY·SUPPORT·유지관리 정책과 최소 Issue/PR template을 정리한다.
5. backend·frontend 버전을 `1.0.0`으로 맞추고 SBOM·checksum을 재생성한다.
6. OSS readiness 필수 파일 Gate와 회귀 테스트를 보강한다.
7. backend·frontend·dependency·SBOM·문서·민감정보 검사를 실행하고 diff를 재감사한다.
8. 릴리스 준비 파일만 commit·push하고 PR·CI·merge를 완료한 뒤 브랜치를 정리한다.
9. 병합 commit에 annotated `v1.0.0` tag를 push하고 GitHub Release를 공개·검증한다.
10. 실제 tag·Release 근거를 별도 문서 전용 통합으로 Registry와 evidence에 확정한다.

tag 생성 전 Gate가 실패하면 tag와 Release를 만들지 않는다. tag push 뒤 GitHub Release
생성만 실패하면 tag를 이동하거나 삭제하지 않고 같은 tag로 생성을 다시 시도한다.
