# PRZ-023 Plan

1. final `main` SHA, working tree, 열린 PR·Issue, CI와 Registry 다음 번호를 확정한다.
2. 제품 핵심 흐름과 인증·ownership·ACTIVE·Worker·cleanup·storage·MCP source와 기존 Evidence를 감사한다.
3. `nanoid` advisory와 dependency tree를 확인해 안전한 patch만 lockfile과 SBOM에 반영한다.
4. 실제 heading·파일·결과 경로를 기준으로 필요한 문서와 Registry만 최소 수정한다.
5. 현재 환경에서 backend, frontend, PostgreSQL, 도구, OSS, SBOM, 링크·anchor와 민감정보 검사를 실행한다.
6. OpenSQL/OpenProxy는 승인된 기존 환경을 안전하게 재실행할 수 있는지 확인하고, 불가능하면 역사 근거로 한정한다.
7. 전체 diff를 독립적으로 재감사하고 blocking finding 0건일 때만 Evidence와 최종 판정을 확정한다.
8. Closeout 관련 파일만 커밋·push하고 `main` 대상 PR을 만든 뒤 CI를 확인한다. merge·tag·release는 하지 않는다.

실패한 필수 검사는 고치지 않은 채 통과로 기록하지 않는다. 기능·migration·환경 변경이 필요해지면
Closeout 범위를 넘은 것으로 보고 `CLOSEOUT_NEEDS_ADJUSTMENT` 또는 해당 defect 판정으로 중단한다.
