# AGH Manager

Standalone Android manager for the dual-instance AdGuardHomeForRoot integration.

## Features

- Requests Magisk/root access through `su -c`.
- Detects `/data/adb/agh/scripts/tool.sh`.
- Live status refresh for Domestic (:5591 / Web :3000) and Foreign (:5592 / Web :3001).
- Start / stop / restart all instances.
- Start / stop / restart Domestic independently.
- Start / stop / restart Foreign independently.
- Embedded AdGuard Home WebView for ports 3000 and 3001.
- Quick log viewer for both AGH instances and the module history log.

The app deliberately does not implement DNS routing itself. All control operations are delegated to the module's `tool.sh`, keeping Box/AGH failover semantics in one place.
