package io.github.chenyurumeng.aghmanager.data

class DiagnosticRepository {
    suspend fun generate(): ShellResult {
        val command =
            "echo 'Box & AGH Manager v0.5.0-rc9'; " +
                "echo '===== BOX STATUS ====='; " +
                StatusRepository.BOX_SERVICE + " status 2>&1 || true; " +
                "echo '===== BOX STOP GUARD ====='; " +
                "[ -f " + StatusRepository.BOX_STOP_GUARD + " ] && echo present || echo clear; " +
                "echo '===== AGH STATUS ====='; " +
                StatusRepository.AGH_TOOL + " status 2>&1 || true; " +
                "echo '===== LISTENERS ====='; " +
                "ss -lntup 2>/dev/null | grep -E 'AdGuardHome|:1053|:9090' || true; " +
                "echo '===== BOX SETTINGS ====='; " +
                "grep -E '^(bin_name|proxy_mode|network_mode|dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port|ipv6|proxy_tcp|proxy_udp|dns_hijack_tcp|dns_hijack_udp|quic|mihomo_dns_forward)=' " +
                StatusRepository.BOX_SETTINGS + " 2>/dev/null || true; " +
                "echo '===== NAT_DNS_HIJACK ====='; " +
                "iptables -t nat -nvL NAT_DNS_HIJACK --line-numbers 2>/dev/null || true; " +
                "echo '===== IPv6 DNS GUARD ====='; " +
                "ip6tables -t filter -S OUTPUT 2>/dev/null | grep BOX_DNS6_REJECT || true"

        return RootShell.exec(command, 60)
    }
}
