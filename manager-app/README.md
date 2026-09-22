# Box & AGH Manager

Standalone Android manager for the integrated **Box + Mihomo + dual AdGuard Home** stack.

## Version

v0.3.0-rc3

## Architecture

The app is a controller only. It does not implement routing rules itself.

- Box actions: `/data/adb/box/scripts/box.service`
- AGH actions: `/data/adb/agh/scripts/tool.sh`
- Mihomo Dashboard: detected from `external-controller` and opened under `/ui/`
- Domestic AGH: DNS 5591 / Web 3000
- Foreign AGH: DNS 5592 / Web 3001
- Mihomo DNS fallback: 1053
- Fail-closed target: 65534

The app never calls `box.iptables enable/disable` for normal Box service control. This preserves the Box `user_stopped` guard and Stage 19 stop-race protection.

## Pages

### Home
Unified health dashboard:
- HEALTHY
- DEGRADED
- FAIL-CLOSED
- STOPPED
- ERROR

Shows actual listener state and active `NAT_DNS_HIJACK` targets.

### Box
- Start / restart / stop Box
- Read core, PID, proxy mode, network mode, DNS hijack mode and IPv6 state
- Show `user_stopped`
- Open embedded Mihomo Dashboard

### AGH
- Start / restart / stop all
- Per-instance Domestic control
- Per-instance Foreign control
- Embedded WebUI on ports 3000 / 3001

### Logs
- Box core log
- Box service log
- Box tool log
- Domestic AGH log
- Foreign AGH log
- AGH module history

### Settings
- 3s / 5s / 10s / disabled auto refresh
- Copy unified diagnostic report

## Coordinated service order

Start all:

1. Start Domestic + Foreign AGH
2. Start Box
3. Box creates DNS routing based on live listeners

Stop all:

1. Stop Box first
2. `box.service stop` creates `user_stopped` before removing hooks/core
3. Stop AGH second

Restart all:

1. Stop Box
2. Restart AGH
3. Start Box

This order is intentional and must not be changed without re-running the Stage 17-20 regression suite.


## WebView process isolation

Domestic (:3000), Foreign (:3001), and Mihomo (:9090) dashboards run in separate Android processes with separate WebView data directories.

The data-directory suffix is configured once per process before WebView initialization. This avoids repeated `WebView.setDataDirectorySuffix()` calls when a dashboard Activity is reopened while its process remains alive, which would otherwise throw `IllegalStateException`.

Cookies are explicitly flushed when leaving a dashboard so login state survives Activity recreation.
