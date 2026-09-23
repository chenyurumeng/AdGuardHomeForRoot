# Box & AGH Manager v0.6.0

Box & AGH Manager 0.6.0 is the first feature-complete native Compose release in this development line.

## Highlights

- Native Box status and service control with Current -> Pending -> Apply configuration workflow.
- Core / Whitelist / Blacklist app routing with persistent installed-app cache and explicit apply.
- Mihomo control for dashboard, policy groups, active connections, provider/subscription management, DNS cache and runtime status.
- Independent Domestic and Foreign AdGuard Home control.
- Native AGH DNS settings, filters/subscriptions, user rules, Query Log, clients, rewrites, access control, blocked services, statistics and protection services.
- Blocked-services inactivity scheduling using the current AGH schedule model.
- TLS / encrypted DNS management for HTTPS/DoH, DoT and DoQ with validation-first handling and private-key protections.
- Versioned backup, import preview, selective restore and pre-restore recovery snapshots.
- Native Health Center covering Root, Box, Mihomo, dual AGH, DNS chain, iptables, IPv6, Controller and configuration consistency.
- Release hardening for large lists, duplicate refreshes, pending-state preservation, rapid service-control taps, API error redaction and network-restart recovery.
- Automated unit regressions for routing/fallback/fail-closed and Box apply-strategy behavior.

## Security and privacy

- AGH Basic Auth material, Mihomo controller secrets, subscription URLs/tokens, TLS private keys and Android Keystore material are excluded from diagnostic output.
- Ordinary backups exclude TLS private keys and Android Keystore credentials.
- API error messages are intentionally concise and do not surface server response bodies that may contain sensitive material.
- The release APK is built from GitHub Actions with an external signing keystore supplied only through GitHub Secrets.

## Upgrade notes

This release manages an existing rooted Box + AdGuardHomeForRoot installation. It does not replace the Box or AGH backend modules.

Before changing network, DNS or TLS configuration, keep a known-good backend configuration and use the manager backup feature where appropriate.
