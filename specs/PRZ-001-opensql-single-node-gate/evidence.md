# PRZ-001 Evidence

## Current evidence

| Item | Result | Evidence |
|---|---|---|
| VM licensing policy | PASS | TmaxTibero email received 2026-07-27: VirtualBox Rocky Linux 9 VM permitted; submit guest hostname and vCPU/core/thread; use fixed IP and synchronized time. |
| VirtualBox installation | PASS | Oracle VirtualBox 7.2.12 installed on the laboratory Windows host on 2026-07-27. |
| VM hardware | PASS | `PRIZM-OpenSQL` created at `C:\VM\VirtualBox\PRIZM-OpenSQL`: EFI, 4 vCPU, 12 GiB RAM, dynamic 120 GiB VDI, NAT plus DHCP-disabled Host-only adapter. |
| Rocky Linux installer media | PASS | Official Rocky Linux 9.8 boot ISO SHA-256 verified: `d6eeefdc8437c593d41a3150fcca4a734c55642ed472eecdda99720bb1370881`. |
| Rocky Linux guest | PASS | Rocky Linux 9.8 installed in `PRIZM-OpenSQL`; `prizm-opensql-01` is the static guest hostname. |
| Fixed Host-only network | PASS | `enp0s8` is `192.168.56.10/24`; it reached the Windows Host-only address `192.168.56.1` with 0% packet loss. |
| Time synchronization | PASS | Asia/Seoul configured; `timedatectl` reported system clock synchronized and NTP active; `chronyc tracking` reported Stratum 3 and `Leap status: Normal`. |
| License submission values | READY | Hostname `prizm-opensql-01`; CPU `AMD Ryzen 7 9800X3D 8-Core Processor`; vCPU/logical thread `4`; core `4`; thread(s) per core `1`; socket `1`. |
| Test-license application | SUBMITTED | User submitted the application on 2026-07-27 using the recorded VM values. License issuance and application are still `NOT_RUN`. |
| Post-install boot and administration | PASS | Rocky booted after installer-media removal; `jaemin@prizm-opensql-01` ran `sudo -v` successfully after reboot. |
| OpenSQL installation/license | NOT_RUN | Pending guest evidence and license application. |
| OpenSQL Gate | NOT_RUN | No OpenSQL target or dedicated credentials yet. |
| GitHub Issue / PR | NOT_CREATED | GitHub external write not authorized for this environment setup. |

## Verification boundary

PostgreSQL, Docker, and local Ollama results must be recorded separately from the future
OpenSQL target result. No result in this file proves OpenProxy or OpenHA compatibility.
