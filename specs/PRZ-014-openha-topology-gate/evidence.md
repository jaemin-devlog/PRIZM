# PRZ-014 — 대회 OpenHA Topology 시도 거절 Evidence

> **상태:** `REJECTED`
> **기준 소스:** `a65f91d`

## 결정 근거

- 공식 대회 설치 안내: OpenSQL은 고가용성 구성이 아닌 Single 구성으로 한 서버에만
  설치한다.
- 지원 OS는 Rocky Linux 9.7이며 hostname 변경 시 라이선스 재발급이 필요하다.
- 이 지침에 따라 두 번째 OpenSQL DB node와 Replica 구성을 진행하지 않는다.

## 중단 시점의 사실

- 독립 Rocky Linux 9.7 VM B/C의 OS·network·SSH 준비: `PASS`
- 기존 etcd cluster를 learner add·동기화·promote 순서로 3 voting member까지 확장:
  `PASS`
- witness VM의 OpenSQL, PostgreSQL, Patroni, pgvector와 OpenProxy 설치: 0건
- Replica VM의 OpenSQL/Patroni 설치와 Replica bootstrap: `NOT_RUN`
- streaming replication, replication lag와 synthetic write replication: `NOT_RUN`
- Primary 장애, promote, switchover와 자동 장애전환: `NOT_RUN`
- production code와 migration 변경: 0건

## 인프라 롤백 결과

- A+B quorum과 endpoint health 확인 후 C member, B member 순서로 제거했다.
- 최종 etcd member는 Node A의 `etcd1` 하나이며 local endpoint health는 `PASS`다.
- Node A의 Host-only DCS `2379`/`2380` 허용 규칙을 제거했다. 기존 SSH·OpenSQL
  `:5432`·OpenProxy `:6432` 규칙은 유지했다.
- B/C에서 `prizm-etcd` service, 전용 config/data, etcd/etcdctl binary, system account와
  DCS firewall 규칙을 제거했다.
- B/C를 정상 종료한 뒤 VirtualBox 등록과 전용 디스크를 삭제했다.
- Node A `opensql-etcd`, Patroni와 OpenProxy: 모두 `active`
- Node A Patroni Primary endpoint: HTTP `200`
- Windows → Node A SSH, OpenSQL `:5432`, OpenProxy `:6432`: `PASS`
- Windows → Node A etcd `:2379`: 차단 확인

## 최종 판정

`REJECTED` — 대회와 현재 제품 로드맵에서 다중 OpenSQL DB node 및 장애전환을 제거한다.
