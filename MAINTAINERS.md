# 유지관리 정책

## 현재 유지관리자

- Jaemin Jeong ([@jaemin-devlog](https://github.com/jaemin-devlog))

현재 PRIZM은 단일 유지관리자 프로젝트입니다. 유지관리자는 Issue 분류, 변경 범위
결정, Pull Request 병합, 보안 신고 처리와 릴리스 발행을 담당합니다.

## 변경과 릴리스 결정

- 제품 동작과 보안 경계를 바꾸는 변경은 Issue·Spec·Pull Request와 실행 근거를 남깁니다.
- 보호가 필요한 보안 내용은 공개 Issue 대신 Private Vulnerability Reporting에서 다룹니다.
- 릴리스는 통합된 `main`의 검증 결과를 확인한 뒤 annotated 또는 signed tag와 GitHub Release로 발행합니다.
- Agent 감사는 품질 근거일 뿐 GitHub review를 대신하지 않습니다. 별도 reviewer가 없으면 단독 유지관리자라는 사실을 기록합니다.

유지관리자가 바뀌면 실제 권한 이전을 먼저 완료한 뒤 이 문서와 GitHub 설정을 함께
갱신합니다. 지원 범위와 응답 기준은 [지원 정책](SUPPORT.md)을 따릅니다.
