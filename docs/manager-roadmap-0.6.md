# Box & AGH Manager 0.6.0 Development Roadmap

> Branch: `feat/manager-compose-v0.5`
>
> Stable rollback branch: `feat/agh-manager-apk`
>
> This document is the long-term implementation order and release gate for the native Android manager. Do not merge into the stable branch until the 0.6.0 release stage.

## Global rules

- Keep the app native: Kotlin + Jetpack Compose + Material 3 + Navigation Compose + ViewModel + StateFlow + Repository.
- Root work must execute off the main thread through `RootShell` / `su -c`.
- Do not modify the Box backend during RC14-RC20 unless a later stage explicitly requires it.
- Do not force-push, merge, or modify `feat/agh-manager-apk` during RC development.
- Configuration flows use: Current -> Pending -> Apply -> fresh read -> verify.
- Before writing externally editable AGH configuration, fresh-fetch current state and reject silent overwrite on baseline conflict.
- Dangerous or destructive operations require confirmation.
- Prefer current official AdGuard Home APIs; do not regress to deprecated APIs when newer endpoints exist.
- Do not expose credentials, private keys, subscription URLs, controller secrets, tokens, Basic Auth, or Keystore material in UI diagnostics or logs.
- Every RC must pass GitHub Actions `:app:lintDebug :app:assembleDebug`, rename, and artifact upload on the final HEAD.
- A stage is not complete until the final GitHub Actions run is SUCCESS and the produced ZIP/APK integrity is verified.
- GitHub Actions is the authoritative build. Local/container Gradle must not be presented as the release build.

## Version sequence

| Stage | Version | Code | Goal | Gate |
|---|---:|---:|---|---|
| RC13 | 0.5.0-rc13 | 23 | Statistics completion | COMPLETE |
| RC14 | 0.5.0-rc14 | 24 | Safe Browsing / Safe Search / Parental Control | COMPLETE |
| RC15 | 0.5.0-rc15 | 25 | Blocked Services Schedule native editing | COMPLETE |
| RC16 | 0.5.0-rc16 | 26 | TLS / DoH / DoT / DoQ management | COMPLETE |
| RC17 | 0.5.0-rc17 | 27 | Backup / Restore / Export / Migration | COMPLETE |
| RC18 | 0.5.0-rc18 | 28 | Diagnostics / Health Center / consistency checks | Required |
| RC19 | 0.5.0-rc19 | 29 | UI / performance / lifecycle / error hardening | Required |
| RC20 | 0.5.0-rc20 | 30 | Feature freeze and full regression | Required |
| Release | 0.6.0 | 31 | Signed release, notes, tag and stable integration | Final |

If encrypted-DNS server management is not required, RC16 may be skipped while keeping later version planning explicit rather than silently reusing its version.

---

## RC13 — Statistics

Status: COMPLETE.

Acceptance scope:

- `/control/stats`
- `/control/stats/config`
- `/control/stats/config/update`
- `/control/stats_reset`
- DNS query count, blocked count/rate, average processing time
- Top queried/blocked domains, clients, upstream responses and upstream average time
- lightweight trend UI
- recent range constrained by retention
- statistics enabled/retention/ignored domains
- destructive reset confirmation
- baseline conflict protection
- final CI and artifact integrity verification

---

## RC14 — Protection Services

Create one native "安全保护 / Protection Services" center for each AGH instance.

Scope:

- Safe Browsing
- Safe Search
- Parental Control
- service/current status
- Current -> Pending -> Apply workflow
- current official AGH OpenAPI/schema verification before implementation
- use official APIs instead of direct YAML editing
- expose supported Safe Search details only when present in the official schema
- fresh-fetch before save and reject concurrent WebUI overwrite
- re-read server state after apply

Acceptance:

- Domestic and Foreign instances are independent.
- Initial state is loaded from AGH, not assumed.
- No immediate mutation from toggles; Apply is explicit.
- API errors are concise and do not leak HTTP auth material.
- Final HEAD passes full GitHub Actions and artifact verification.

---

## RC15 — Blocked Services Schedule

Extend existing blocked-services support with native schedule editing.

Requirements:

- read the actual AGH schedule schema
- per-day enabled state
- start/end times
- services and schedule may be committed together
- unchanged schedule must not be rewritten unnecessarily
- fresh baseline before save
- reject overwrite when WebUI changed the schedule
- cross-midnight semantics follow AGH API exactly
- templates (Every day / Weekdays / Weekend / Night) only fill the draft and never auto-apply

---

## RC16 — TLS / Encrypted DNS

Optional unless AGH itself needs to expose encrypted DNS.

Target:

- TLS enabled
- server name
- HTTPS port
- TLS/DoT port
- QUIC/DoQ port where supported
- certificate
- private key

Security:

- only current official TLS endpoints/schema
- private key never enters logs, diagnostics, Snackbar text, ordinary SharedPreferences, or backups by default
- private key field hidden by default
- validation errors summarized without secret content
- preserve baseline before submission
- avoid leaving a partial configuration on failure

---

## RC17 — Backup / Restore / Migration

Introduce a versioned manager backup format.

Suggested logical schema:

```text
BoxAghBackup
version: 1

Box:
  settings
  package list
  subscription metadata

AGH:
  domestic config
  foreign config
  filtering
  user rules
  clients
  rewrites
  access control
  blocked services
  statistics config
```

Requirements:

- export
- backup summary
- import preview / diff
- explicit confirmation
- per-module restore
- automatic local pre-restore snapshot
- rollback on failed restore
- no blind one-click overwrite
- AGH plaintext password excluded by default
- Android Keystore material is not portable
- TLS private keys excluded from ordinary backup
- a future secret-inclusive backup requires a separately designed encrypted container

---

## RC18 — Diagnostics / Health Center

Native health model:

```text
Root
Box
Mihomo
Domestic AGH
Foreign AGH
DNS Chain
iptables
IPv6
Controller
Configuration Consistency
```

Checks include:

- module presence
- Domestic/Foreign process state
- configured ports vs actual listeners
- AGH settings vs Box DNS ports
- Mihomo DNS :1053
- Controller :9090 or configured value
- NAT_DNS_HIJACK
- BOX_DNS6_REJECT
- user_stopped
- explicit mismatch reporting instead of only UP/DOWN

Diagnostic reports must redact:

- AGH passwords / Basic Auth
- Mihomo subscription URLs
- tokens / secrets
- private keys
- Android Keystore material
- sensitive custom headers

Only narrowly scoped repair actions are allowed initially, such as reapplying app rules, restarting a selected AGH instance, rebuilding dashboard webroot, or reapplying DNS hooks.

---

## RC19 — UI / Performance / Lifecycle Hardening

Feature freeze begins for business features.

Review all major screens for:

- flicker and scroll/top jumps
- duplicate loading / duplicate API calls
- navigation state restoration
- process-death restoration
- configuration changes / rotation
- stable LazyColumn keys
- large query-log lists
- 1000+ installed apps
- 1000+ user rules
- large filter/client/rewrite/blocked-service data
- unified Loading / Empty / Error states
- unified Snackbar and dialog behavior
- unified pending-changes UX
- Material 3 spacing consistency

Cleanup:

- legacy Java/activity code
- unused resources/imports/repositories
- deprecated APIs
- debug shell output

---

## RC20 — Final Release Candidate

No new features.

Regression matrix includes:

- Android 16 / Magisk / SELinux Enforcing
- Domestic only / Foreign only / Box only / all running
- Box stopped / AGH stopped / Mihomo DNS stopped / Controller stopped
- Core / Whitelist / Blacklist
- IPv4 / IPv6 / dual stack
- TUN / TPROXY / Redirect

DNS-chain regression:

```text
Whitelist app -> Foreign -> :1053 -> proxy
Foreign failure -> :1053
Foreign + :1053 failure -> :65534 fail-closed
Non-whitelist -> Domestic
```

Also test:

- boot
- watchdog
- user_stopped
- restart races
- rapid repeated taps
- root-command timeout
- AGH 401 / timeout
- damaged YAML
- Wi-Fi <-> cellular
- IPv6 network changes

Release gate:

- GitHub Actions SUCCESS
- no known P0/P1 issues
- lint cannot be disabled or globally suppressed to ship

---

## 0.6.0 — Signed Stable Release

Add formal release build:

- `:app:lintRelease`
- `:app:assembleRelease`
- `:app:test` when practical
- signing keystore stored outside git
- signing alias/password supplied via GitHub Secrets
- automatic SHA256
- GitHub Release artifact
- output name: `Box-AGH-Manager-v0.6.0.apk`
- release notes covering Box, app routing, Mihomo, subscriptions, connections, dual AGH, DNS, filters, query log, clients, rewrites, access control, blocked services, statistics, diagnostics and backup

Only after release qualification:

- integrate Compose branch
- tag 0.6.0
- create GitHub Release
- retain the prior stable branch as a rollback point

---

## Post-0.6.0

Move to a 0.7.x feature cycle rather than extending the RC series.

Candidates:

- dashboard/home redesign
- notification center
- DNS/proxy fault notifications
- state-change alerts
- profiles and multiple network policies
- import/export profiles
- automation by Wi-Fi / cellular / SSID / charging / boot
- Quick Settings Tile
- home-screen widget
- richer Mihomo rules/provider management

QS Tile, widgets, and automatic scenario switching are intentionally deferred until after 0.6.0 because they materially increase lifecycle, background-permission, root-control, and state-synchronization complexity.
