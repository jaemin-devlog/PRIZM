# PRZ-001: OpenSQL single-node verification environment

## Status

`IN_PROGRESS`

## Purpose

Create one Rocky Linux 9 VirtualBox guest on the laboratory Windows host as the
dedicated OpenSQL verification target for PRIZM. The environment implements the
vendor-confirmed test-license conditions: use the guest hostname and vCPU/core/thread
values in the application, assign a static (non-DHCP) address, and verify time
synchronization.

## Evaluation connection

- Primary: `EVAL-R1-01` — a reproducible, isolated OpenSQL verification target.
- Secondary: `EVAL-R1-03` — documented setup, commands, and environment evidence.

## Requirements

1. The host uses Oracle VirtualBox and contains exactly one initial OpenSQL guest:
   `PRIZM-OpenSQL` / `prizm-opensql-01`.
2. The guest is Rocky Linux 9 x86_64 with 4 vCPU, 12 GiB RAM, and a 120 GiB
   dynamically allocated virtual disk.
3. The guest has NAT for package access and a Host-only adapter with a fixed address.
   The OpenSQL service is not exposed to the public internet.
4. Before license submission, record the guest's hostname and actual CPU/core/thread
   values. Keep the submitted configuration stable unless TmaxTibero instructs otherwise.
5. Confirm guest time synchronization before applying the license.
6. The first executable gate uses a fresh, dedicated OpenSQL database or schema and
   verifies Flyway, vector(1024), cosine search, ownership/active-version constraints,
   and worker SQL compatibility. It must not claim OpenProxy or OpenHA compatibility.

## Preserved contracts

- PostgreSQL/pgvector remains the laptop local-development environment.
- `compose.yaml` remains the PostgreSQL local-development configuration.
- Runtime JDBC and Flyway JDBC endpoints remain separate inputs; they must not be
  assumed equal until proven by the target environment.
- Source code, migrations, and executable tests remain implementation truth.

## Exclusions

- `prizm-app-01`, OpenProxy, OpenHA, multi-node failover, Worker/Ollama application
  integration, and an OpenSQL Compose override are outside this slice.
- This setup does not itself prove OpenSQL compatibility; that requires the later gate.

## Acceptance criteria

1. VirtualBox is installed and the guest configuration matches requirements 1–3.
2. The guest output records hostname, CPU/core/thread, static IP, and synchronized time.
3. The license form uses the recorded guest values, not the Windows-host values.
4. `OpenSqlInfrastructureTest` is run only after OpenSQL and dedicated test credentials
   exist; until then its evidence is `NOT_RUN`.
