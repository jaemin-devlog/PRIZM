# PRZ-014 — 거절 처리 계획

> **문서 상태:** `REJECTED`

## 완료된 문서 결정

- 현재 로드맵에서 다중 DB node와 장애전환 단계를 제거한다.
- PRZ-013을 OpenSQL/OpenProxy 단일 Primary 경로의 마지막 대회 Gate로 유지한다.
- 과거에 실행하지 않은 Replica와 장애전환을 `PASS`로 바꾸지 않는다.
- production code, migration, OpenSQL, Patroni와 OpenProxy 설정은 변경하지 않는다.

## 인프라 정리 결과

별도 사용자 승인 후 Node A+B quorum을 먼저 확인하고 C, B member 순으로 제거했다.
Node A 단일 member health와 Primary 상태를 확인한 뒤 B/C의 etcd 자산과 VM 디스크를
삭제했다. Node A의 DB data와 OpenSQL·Patroni·OpenProxy 설정은 변경하지 않았다.
