# PRIZM 문서 안내

이 디렉터리는 현재 상태, 앞으로의 계획, 검증 자료와 역사 기록을 분리한다.
문서와 구현이 다르면 source code, Flyway migration과 실행 가능한 test를 우선한다.

## 먼저 읽을 문서

| 알고 싶은 내용 | 문서 |
|---|---|
| 지금 실제로 되는 기능 | [현재 구현 현황](project-status.md) |
| 앞으로 개발할 순서 | [개발 로드맵](roadmap.md) |
| 대회 제출 전 우선순위 | [2026 티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md) |
| 지금까지 진행한 작업 | [개발 기록](development-log.md) |
| 기존 구현의 코드·test 근거 | [PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md) |

Agent가 지켜야 할 규칙은 [AGENTS.md](../AGENTS.md), 기능별 spec 상태는
[Spec Registry](../specs/README.md)를 기준으로 한다.

## 보조 문서

- [OpenSQL 기술 Gate](opensql-gate.md): 실제 OpenSQL 환경 검증 전 체크리스트
- [검색 품질 평가](evaluation/search-evaluation.md): Dense 검색 평가 방법과 합성 기준선
- [대표 문제 해결 사례](showcase/problem-solving-case-studies.md): 대회·포트폴리오용 기술 설명

## 보관 문서

`archive/`는 과거 기획, 종료된 실험과 초기 검증 기록이다. 현재 구현이나 현재
개발 순서를 판단하는 기준으로 사용하지 않는다.

- [과거 PRIZM 종합 기획안](archive/PRIZM_최종_기획안.md)
- [과거 오픈소스 전환 상세 계획](archive/oss-transition-execution-plan.md)
- [BGE Reranker 비채택 실험](archive/experiments/2026-07-14-bge-reranker-evaluation.md)
- [초기 문서 등록 검증](archive/verification/2026-07-13-minimal-document-registration.md)
- [초기 벡터 검색 검증](archive/verification/2026-07-13-minimal-vector-search.md)
