package io.github.chenyurumeng.aghmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemStateRoutingTest {
    @Test
    fun stoppedBoxUsesSystemDns() {
        val state = SystemState(
            box = BoxState(running = false, userStopped = true)
        )
        assertEquals("系统 DNS（Box stopped）", state.domesticDnsTarget())
        assertEquals("系统 DNS（Box stopped）", state.foreignDnsTarget())
    }

    @Test
    fun domesticRouteUsesConfiguredPort() {
        val state = SystemState(
            box = BoxState(running = true),
            domestic = AghInstanceState("Domestic", 5353, 3000, running = true),
            dns = DnsState(port5591 = true, route5591 = true)
        )
        assertEquals("Domestic :5353", state.domesticDnsTarget())
    }

    @Test
    fun foreignRouteUsesConfiguredPort() {
        val state = SystemState(
            box = BoxState(running = true),
            foreign = AghInstanceState("Foreign", 5454, 3001, running = true),
            dns = DnsState(port5592 = true, route5592 = true)
        )
        assertEquals("Foreign :5454", state.foreignDnsTarget())
    }

    @Test
    fun foreignFailureFallsBackToMihomoDns() {
        val state = SystemState(
            box = BoxState(running = true),
            foreign = AghInstanceState("Foreign", 5592, 3001, running = false),
            dns = DnsState(
                port5592 = false,
                port1053 = true,
                route5592 = true,
                route1053 = true
            )
        )
        assertEquals("Mihomo :1053 fallback", state.foreignDnsTarget())
    }

    @Test
    fun foreignAndMihomoFailureUsesFailClosed() {
        val state = SystemState(
            box = BoxState(running = true),
            foreign = AghInstanceState("Foreign", 5592, 3001, running = false),
            dns = DnsState(
                port5592 = false,
                port1053 = false,
                route5592 = true,
                route1053 = true,
                route65534 = true
            )
        )
        assertEquals(":65534 FAIL-CLOSED", state.foreignDnsTarget())
    }

    @Test
    fun routingModeAliasesRemainCompatible() {
        assertEquals(RoutingMode.CORE, RoutingMode.fromRaw("core"))
        assertEquals(RoutingMode.WHITELIST, RoutingMode.fromRaw("white"))
        assertEquals(RoutingMode.BLACKLIST, RoutingMode.fromRaw("black"))
    }
}
