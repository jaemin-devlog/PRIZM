# 지원 정책

## 지원 대상

최신 GitHub Release로 공개한 PRIZM 소스 코드를 가능한 범위에서 지원합니다. `main`은
다음 변경을 준비하는 개발 기준선일 수 있으며, 이전 릴리스와 임의 수정본에는 같은
수준의 지원을 보장하지 않습니다. 응답과 수정 시간을 보장하는 SLA는 없습니다.

## 문의 경로

- 재현 가능한 버그는 `버그 보고` Issue Form을 사용합니다.
- 새 기능은 `기능 제안` Issue Form에서 제안합니다.
- 문서 오류는 `문서 개선` Issue Form으로 알려 주세요.
- 취약점과 민감한 보안 정보는 공개 Issue가 아니라 [보안 정책](SECURITY.md)의
  비공개 신고 경로를 사용합니다.
- 기여와 Pull Request 절차는 [기여 안내](CONTRIBUTING.md)를 따릅니다.

계정 정보, JWT, 비밀번호, 실제 경력 문서, 업로드 원본, DB dump와 개인 식별 정보를
공개 Issue에 첨부하지 마세요.

## 지원하지 않는 범위

- 공개 SaaS 운영과 인터넷 노출 환경의 보안 구성
- 사용자별 커스텀 배포·클라우드·네트워크 운영
- PostgreSQL·pgvector, Ollama와 OpenSQL 공급사 자체 제품 지원
- 이미지 전용·암호화 PDF, OpenHA multi-node failover
- 별도 감사를 거치지 않은 JAR·frontend bundle·container image·모델 배포물

현재 기능과 검증 경계는 [프로젝트 상태](docs/project-status.md), 실행 방법은
[빠른 시작](docs/quickstart.md)을 확인해 주세요.
