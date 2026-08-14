# PRZ-014 — 거절 처리 Tasks

> **현재 상태:** `REJECTED`

- [x] 공식 Single-only 대회 설치 범위를 확인했다.
- [x] 다중 DB node와 장애전환을 현재 로드맵에서 제거했다.
- [x] PRZ-014를 재개 가능한 계획이 아닌 거절 결정으로 기록했다.
- [x] Replica, replication과 장애전환을 `NOT_RUN`으로 유지했다.
- [x] 기존 3-member etcd를 안전하게 Node A 단일 member로 복귀했다.
- [x] B/C의 etcd 자산과 Replica/Witness VM 등록·디스크를 삭제했다.
