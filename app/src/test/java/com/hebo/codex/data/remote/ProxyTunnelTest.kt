package com.hebo.codex.data.remote

import com.hebo.codex.domain.HttpProxyAuthentication
import com.hebo.codex.domain.ProxyConfiguration
import com.hebo.codex.domain.Socks5Authentication
import com.hebo.codex.domain.Socks5DnsResolution
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

class ProxyTunnelTest {
    @Test
    fun httpConnectSendsProxyAuthenticationOnlyToProxy() {
        val output = ByteArrayOutputStream()
        performHttpConnectHandshake(
            input = ByteArrayInputStream("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()),
            output = output,
            authority = "codex.example:443",
            configuration = ProxyConfiguration.HttpConnect(
                url = "http://proxy.example:8080",
                authentication = HttpProxyAuthentication.BASIC,
                username = "mobile",
                headers = mapOf("X-Region" to "office"),
            ),
            credential = "secret",
        )

        val request = output.toString(StandardCharsets.ISO_8859_1.name())
        val encoded = Base64.getEncoder().encodeToString("mobile:secret".toByteArray())
        assertTrue(request.startsWith("CONNECT codex.example:443 HTTP/1.1\r\n"))
        assertTrue(request.contains("Proxy-Authorization: Basic $encoded\r\n"))
        assertTrue(request.contains("X-Region: office\r\n"))
        assertTrue(!request.contains("Authorization: Bearer"))
    }

    @Test
    fun socks5UsesUsernamePasswordAndProxyDns() {
        val response = byteArrayOf(
            5, 2,
            1, 0,
            5, 0, 0, 1, 127, 0, 0, 1, 0x1f, 0x90.toByte(),
        )
        val output = ByteArrayOutputStream()
        performSocks5Handshake(
            input = ByteArrayInputStream(response),
            output = output,
            targetHost = "internal.example",
            targetPort = 8390,
            configuration = ProxyConfiguration.Socks5(
                host = "proxy.example",
                port = 1080,
                authentication = Socks5Authentication.USERNAME_PASSWORD,
                username = "u",
                dnsResolution = Socks5DnsResolution.PROXY,
            ),
            credential = "p",
        )

        val expected = byteArrayOf(
            5, 1, 2,
            1, 1, 'u'.code.toByte(), 1, 'p'.code.toByte(),
            5, 1, 0, 3, 16,
        ) + "internal.example".toByteArray() + byteArrayOf(0x20, 0xc6.toByte())
        assertArrayEquals(expected, output.toByteArray())
    }
}
