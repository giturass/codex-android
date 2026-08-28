package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointValidationTest {
    @Test
    fun acceptsWebSocketEndpoints() {
        assertEquals(
            "ws://10.150.99.201:8390",
            validateWebSocketEndpoint("  ws://10.150.99.201:8390  "),
        )
        assertEquals("wss://codex.example.com/app", validateWebSocketEndpoint("wss://codex.example.com/app"))
    }

    @Test
    fun rejectsHttpEndpoints() {
        assertThrows(IllegalArgumentException::class.java) {
            validateWebSocketEndpoint("https://codex.example.com")
        }
    }

    @Test
    fun rejectsInvalidPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            validateWebSocketEndpoint("ws://codex.example.com:70000")
        }
    }

    @Test
    fun rejectsCapabilityTokenHeaderInjection() {
        assertThrows(IllegalArgumentException::class.java) {
            validateCapabilityToken("secret\r\nX-Injected: true")
        }
    }
}
