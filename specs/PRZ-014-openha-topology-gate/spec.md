# PRZ-014 — 대회 OpenHA Topology 시도 거절 기록

> **상태:** `REJECTED`
> **유형:** Rejected infrastructure decision
> **선행 문서:** [PRZ-013](../PRZ-013-openproxy-single-primary-gate/spec.md)
> **기준 소스:** `a65f91d`

## 결정

대회 제공 OpenSQL은 공식 안내에 따라 고가용성 구성이 아닌 Single 구성으로 한 서버에만
설치한다. 따라서 다중 OpenSQL DB node, Replica, streaming replication, 장애 주입과
자동 장애전환은 PRIZM의 현재 대회 범위와 제품 로드맵에서 제외한다.

PRZ-013의 다음 단계로 다중 노드 Gate를 두지 않는다. 기존 Node A의 OpenSQL,
Patroni, 로컬 관리 경로와 OpenProxy 단일 Primary 구성은 유지한다.

## 거절 근거

- 공식 대회 설치 안내가 OpenSQL을 Single 구성으로 한 서버에만 설치하도록 명시한다.
- hostname 변경 시 라이선스 재발급이 필요하며, 제공 자산을 다른 DB node에 복제해
  사용하지 않는다.
- 기존 installer의 다중 노드 모드 존재 여부와 무관하게 대회 지침을 우선한다.

## 현재 인프라 경계

- Replica용 OpenSQL과 Patroni는 설치하지 않았다.
- Primary 장애, promote, switchover와 자동 장애전환은 실행하지 않았다.
- 조사 과정에서 확장한 etcd는 member를 하나씩 제거해 Node A 단일 member로
  복귀했다.
- Replica와 Witness VM의 etcd 서비스·데이터·바이너리·방화벽 규칙을 제거한 뒤 두
  VM의 VirtualBox 등록과 디스크를 삭제했다.
- Node A의 OpenSQL, Patroni, local etcd와 OpenProxy는 정상 상태를 유지한다.

## 판정

`REJECTED` — 재개 조건이나 후속 다중 노드 Gate 없음.
