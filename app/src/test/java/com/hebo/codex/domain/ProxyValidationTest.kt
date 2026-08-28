package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyValidationTest {
    @Test
    fun normalizesHttpConnectConfiguration() {
        val result = validateProxyConfiguration(
            ProxyConfiguration.HttpConnect(
                url = "  https://proxy.example:8443/  ",
                authentication = HttpProxyAuthentication.BASIC,
                username = " codex ",
                headers = mapOf(" X-Region " to " office "),
            ),
        ) as ProxyConfiguration.HttpConnect

        assertEquals("https://proxy.example:8443/", result.url)
        assertEquals("codex", result.username)
        assertEquals(mapOf("X-Region" to "office"), result.headers)
    }

    @Test
    fun rejectsReservedConnectHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            parseProxyHeaders("Proxy-Authorization: secret")
        }
    }

    @Test
    fun requiresContentUriForSshPrivateKey() {
        assertThrows(IllegalArgumentException::class.java) {
            validateProxyConfiguration(
                ProxyConfiguration.Ssh(
                    host = "gateway.example",
                    username = "codex",
                    authentication = SshAuthentication.PRIVATE_KEY,
                    privateKeyUri = "file:///tmp/id_ed25519",
                ),
            )
        }
    }

    @Test
    fun keepsProxyDnsResolutionExplicit() {
        val result = validateProxyConfiguration(
            ProxyConfiguration.Socks5(
                host = "proxy.example",
                port = 1080,
                authentication = Socks5Authentication.NONE,
                dnsResolution = Socks5DnsResolution.PROXY,
            ),
        ) as ProxyConfiguration.Socks5

        assertEquals(Socks5DnsResolution.PROXY, result.dnsResolution)
    }
}
