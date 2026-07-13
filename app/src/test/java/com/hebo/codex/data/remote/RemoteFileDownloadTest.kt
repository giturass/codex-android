package com.hebo.codex.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64

class RemoteFileDownloadTest {
    @Test
    fun decodesLargePayloadAcrossChunkBoundaries() {
        val source = ByteArray(100_003) { index -> (index % 251).toByte() }
        val encoded = Base64.getEncoder().encodeToString(source)
        val output = TrackingOutputStream()

        val written = decodeRemoteFileBase64(encoded, output)

        assertEquals(source.size.toLong(), written)
        assertArrayEquals(source, output.toByteArray())
        assertEquals(24 * 1024, output.maximumWriteSize)
    }

    @Test
    fun decodesEmptyPayload() {
        val output = ByteArrayOutputStream()

        assertEquals(0L, decodeRemoteFileBase64("", output))
        assertEquals(0, output.size())
    }

    @Test
    fun rejectsInvalidBase64Payload() {
        assertThrows(ProtocolException::class.java) {
            decodeRemoteFileBase64("not valid base64", ByteArrayOutputStream())
        }
    }

    private class TrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var maximumWriteSize = 0
            private set

        override fun write(value: Int) {
            delegate.write(value)
            maximumWriteSize = maxOf(maximumWriteSize, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            delegate.write(buffer, offset, length)
            maximumWriteSize = maxOf(maximumWriteSize, length)
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()
    }
}
