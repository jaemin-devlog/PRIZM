# PRZ-001 Plan

## Authority and external dependency

TmaxTibero confirmed by email on 2026-07-27 that OpenSQL test licensing may be used
inside a VirtualBox Rocky Linux 9 VM, that the form must use the guest hostname and
vCPU/core/thread values, and that the guest must use a fixed (non-DHCP) IP with time
synchronization.

## Environment plan

| Item | Value |
|---|---|
| Windows host role | Development, Gradle gate runner, and Ollama/RTX host |
| VM name | `PRIZM-OpenSQL` |
| Guest hostname | `prizm-opensql-01` |
| Guest OS | Rocky Linux 9 x86_64 |
| Compute | 4 vCPU, 12 GiB RAM |
| Storage | 120 GiB dynamically allocated virtual disk |
| Adapter 1 | NAT for guest package access |
| Adapter 2 | Host-only, fixed IP chosen after adapter creation |

## Environment selection rationale

- **Supported Linux target:** Rocky Linux 9 x86_64 was selected because it is in the
  supplied OpenSQL 3.0 support matrix. This records an installation target, not a
  successful OpenSQL installation or compatibility result.
- **Isolated VM:** VirtualBox keeps the OpenSQL target separate from the laboratory
  Windows development environment. An installation, license-application, or database
  recovery failure can therefore be investigated without changing the ordinary PRIZM
  PostgreSQL development setup.
- **Functional verification baseline:** 1 socket x 4 cores x 1 thread (4 total vCPU),
  12 GiB RAM, and a dynamically allocated 120 GiB disk were chosen as a bounded
  single-node baseline for migration, vector, and Worker SQL compatibility checks.
  They are not an OpenSQL vendor sizing recommendation, a performance benchmark, or an
  OpenHA multi-node capacity claim.
- **Network separation:** NAT is limited to guest package/vendor-install access. The
  fixed Host-only network gives the Windows host a repeatable private route to the VM
  without public port forwarding. `192.168.56.10/24` is environment evidence only; no
  public Quickstart or deployment contract depends on it.
- **Future HA prerequisite:** Asia/Seoul and time synchronization are verified now so
  that a later, separately scoped OpenHA experiment can start from a known time-sync
  baseline. They do not prove OpenHA configuration or failover behavior.

## Steps

1. Install and verify Oracle VirtualBox on the laboratory Windows host.
2. Obtain the official Rocky Linux 9 x86_64 ISO, verify its checksum, and create the
   guest with the stated fixed hardware allocation.
3. Install Rocky Linux, assign the hostname, configure the Host-only fixed IP, and
   validate `chronyc tracking` or equivalent time synchronization.
4. Record `hostnamectl --static`, `lscpu`, and `nproc --all` for license submission.
5. Submit only the recorded guest values; never commit the license, passwords, or `.env`.
6. Install OpenSQL only after the license asset and vendor installation procedure are
   available. Create a dedicated verification database/schema.
7. Run the documented OpenSQL Gate from Windows with explicit runtime and Flyway URLs.

## Security and recovery

- Bind database access only to the Host-only network; do not create public port forwards.
- Keep the OpenSQL guest as the single source of test evidence; laptop PostgreSQL data
  is not copied into it.
- A VM snapshot is an operator recovery option, not a claim about licensing. Follow
  vendor instructions if they impose a restriction.

## Git plan

Work occurs on `codex/PRZ-001-opensql-single-node-gate`. A real GitHub Issue or PR is
created only when that external write is authorized. No historical artifact is invented.
