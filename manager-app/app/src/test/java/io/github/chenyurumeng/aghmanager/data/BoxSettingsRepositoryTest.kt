package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.ApplyStrategy
import io.github.chenyurumeng.aghmanager.model.BoxConfig
import io.github.chenyurumeng.aghmanager.model.DnsHijackMode
import io.github.chenyurumeng.aghmanager.model.NetworkMode
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoxSettingsRepositoryTest {
    private val repository = BoxSettingsRepository()

    @Test
    fun stoppedBoxAlwaysUsesSaveOnly() {
        assertEquals(
            ApplyStrategy.SAVE_ONLY,
            repository.resolveStrategy(setOf("network_mode"), boxRunning = false)
        )
    }

    @Test
    fun proxyModeOnlyUsesApplyApps() {
        assertEquals(
            ApplyStrategy.APPLY_APPS,
            repository.resolveStrategy(setOf("proxy_mode"), boxRunning = true)
        )
    }

    @Test
    fun networkOrDnsChangesRequireRestart() {
        assertEquals(
            ApplyStrategy.BOX_RESTART,
            repository.resolveStrategy(setOf("network_mode"), boxRunning = true)
        )
        assertEquals(
            ApplyStrategy.BOX_RESTART,
            repository.resolveStrategy(setOf("dns_hijack_mode"), boxRunning = true)
        )
    }

    @Test
    fun appScopedDnsRejectsTun() {
        val config = BoxConfig(
            proxyMode = RoutingMode.WHITELIST,
            networkMode = NetworkMode.TUN,
            dnsHijackMode = DnsHijackMode.SPLIT_APPS
        )
        assertNotNull(repository.validate(config))
    }

    @Test
    fun appScopedDnsRejectsCoreMode() {
        val config = BoxConfig(
            proxyMode = RoutingMode.CORE,
            networkMode = NetworkMode.TPROXY,
            dnsHijackMode = DnsHijackMode.REDIRECT_APPS
        )
        assertNotNull(repository.validate(config))
    }

    @Test
    fun appScopedDnsAllowsWhitelistTproxy() {
        val config = BoxConfig(
            proxyMode = RoutingMode.WHITELIST,
            networkMode = NetworkMode.TPROXY,
            dnsHijackMode = DnsHijackMode.SPLIT_APPS
        )
        assertNull(repository.validate(config))
    }
}
