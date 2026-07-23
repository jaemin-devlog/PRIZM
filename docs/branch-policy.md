# PRIZM 브랜치 운영 정책

## 원칙

PRIZM의 유일한 장기 브랜치는 `main`이다.

- 기능·실험 브랜치는 작업 중에만 임시로 사용한다.
- 작업이 끝나면 코드, 테스트, 문서와 의사결정 근거를 `main`에 반영한 뒤 로컬·원격 브랜치를 삭제한다.
- `develop`, 영구 `release/*`, 보존용 `archive/*` 브랜치는 만들지 않는다.
- 릴리스 시점은 브랜치가 아니라 서명 또는 주석이 있는 tag와 GitHub Release로 보존한다.
- 이미 끝난 작업을 과거 Issue나 PR처럼 새로 만들지 않는다. 과거 사실은 개발 기록과 commit으로 남긴다.

## 브랜치 종료 절차

브랜치를 지우기 전에 `main`과의 merge base, 고유 commit, 변경 파일, 연결된 PR을 확인하고 다음 셋 중 하나로 판정한다.

1. **통합**: 현재 제품 방향과 호환되는 코드·테스트는 `main`에 병합하거나 forward-port한다.
2. **기록 후 폐기**: 실행 코드는 채택하지 않지만 재현 조건·수치·기술 판단이 가치 있으면 결정 문서만 `main`에 남긴다.
3. **폐기**: 중복, 오래된 설명, 현재 방향과 충돌하는 변경은 근거를 확인한 뒤 가져오지 않는다.

통합 후에는 저장소 필수 검증을 수행하고, `main`을 먼저 원격에 반영한 다음 정확한 브랜치 이름을 지정해 삭제한다. 사용자 파일, 비밀정보, 로컬 평가 데이터와 빌드 산출물은 브랜치 정리 대상에 포함하지 않는다.

## 2026-07-23 정리 판정

| 원격 브랜치 | 고유 tip | 판정 |
|---|---|---|
| `codex/search-evaluation-baseline` | `46e24ef` | 파일럿 브랜치에 포함된 Dense 평가 기반이다. 파일럿 통합으로 보존한다. |
| `test/search-evaluation-pilot` | `347d54d` | 합성 11문서·30질문, 평가 하네스와 단위 테스트를 `main`에 통합한다. |
| `experiment/bge-reranker-evaluation` | `617eacf` | Dense 기반은 앞선 통합으로 보존한다. CPU Reranker 코드는 품질 기준과 비용 조건을 충족하지 않아 제외하고 [실험 결정 기록](experiments/2026-07-14-bge-reranker-evaluation.md)만 보존한다. |
| `portfolio/prizm-showcase` | `377f615` | 오래된 README와 구현 상태는 병합하지 않는다. 유효한 수치·문제 해결 구조만 현재 코드 기준의 [근거 문서](portfolio/metrics-and-evidence.md)와 [사례 문서](portfolio/problem-solving-case-studies.md)로 다시 작성한다. |

정리 완료 조건은 GitHub 기본 브랜치가 `main`이고 로컬·원격 branch 목록에 `main`만 남는 것이다.
