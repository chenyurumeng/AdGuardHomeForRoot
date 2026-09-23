# RC20 Final Regression Matrix

Version: 0.5.0-rc20 (code 30)

Status: IN PROGRESS until the Android 16 rooted-device matrix is confirmed.

## Automated gate

GitHub Actions must run all of the following on the final HEAD:

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- APK rename and artifact upload
- artifact ZIP digest verification
- APK ZIP integrity verification

Unit regression coverage includes:

- Core / Whitelist / Blacklist parsing compatibility
- Domestic configured DNS routing
- Foreign configured DNS routing
- Foreign -> Mihomo :1053 fallback
- Foreign + Mihomo failure -> :65534 fail-closed
- Box stopped / user_stopped -> system DNS presentation
- Box save-only / apply-apps / restart strategy
- invalid app-scoped DNS combinations
- network restart result preservation and Box recovery outcome

## Rooted Android 16 device gate

Use the RC20 APK on the target Magisk + SELinux Enforcing device.

| Area | Cases | Expected |
|---|---|---|
| Service combinations | Domestic only / Foreign only / Box only / all running | UI and Health Center match actual processes/listeners |
| Stop states | Box stopped / AGH stopped / Mihomo :1053 stopped / Controller stopped | No false Healthy state; fallback shown accurately |
| Proxy mode | Core / Whitelist / Blacklist | app routing and DNS destination match mode |
| IP stack | IPv4 / IPv6 / dual stack | no unintended IPv6 breakage; DNS guard consistent |
| Network mode | TUN / TPROXY / Redirect | supported combinations apply once and survive refresh |
| DNS chain | whitelist -> Foreign -> :1053 -> proxy | normal path succeeds |
| DNS fallback | Foreign down -> :1053 | proxy DNS remains available |
| Fail closed | Foreign down + :1053 down -> :65534 | proxy-app DNS fails closed instead of leaking |
| Direct path | non-whitelist -> Domestic | Domestic resolver used |
| Boot | reboot with services enabled | expected services and hooks return |
| Stop guard | manual Box stop | `user_stopped` remains respected |
| Rapid taps | repeatedly tap start/restart/stop | only one control action executes at a time |
| Root timeout | force/observe long root command | UI leaves busy state and reports failure |
| AGH auth | wrong credentials / HTTP 401 | concise credential error; no Basic Auth material |
| AGH timeout | stop/blackhole AGH Web endpoint | bounded failure; UI remains usable |
| Damaged config | invalid/partial YAML test copy | Health Center reports error/mismatch; no blind overwrite |
| Network switch | Wi-Fi <-> cellular | state refreshes without duplicate control actions |
| IPv6 change | IPv6 acquire/loss/change | DNS/health state refreshes without stale success |

## P0 / P1 gate

Do not advance to 0.6.0 if any of these remain:

- service control can execute twice from rapid taps
- restart failure can leave Box stopped without a recovery attempt
- proxy DNS can leak instead of using fail-closed behavior
- pending configuration is silently overwritten
- credentials, controller secrets, private keys, or subscription URLs appear in diagnostics/errors
- final GitHub Actions run fails
