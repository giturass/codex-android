package com.hebo.codex.data.remote

import android.content.ContentResolver
import androidx.core.net.toUri
import com.hebo.codex.domain.ProxyConfiguration
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class AppServerClientFactory(
    private val json: Json,
    private val contentResolver: ContentResolver,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val tunnelFactory = ProxyTunnelFactory(::readPrivateKey)

    internal fun create(
        endpoint: String,
        token: String,
        proxy: ResolvedProxyConnection?,
        parentContext: CoroutineContext,
    ): AppServerConnection =
        AppServerConnection(
            webSocketFactory = if (proxy == null) {
                OkHttpAppServerWebSocketFactory(okHttpClient, endpoint, token)
            } else {
                ProxyAppServerWebSocketFactory(
                    endpoint = endpoint,
                    token = token,
                    proxy = proxy,
                    tunnelFactory = tunnelFactory,
                    parentContext = parentContext,
                )
            },
            json = json,
            connectTimeoutMillis = proxy?.profile?.configuration?.proxyConnectTimeoutMillis()
                ?.let { proxyTimeout -> proxyTimeout * 2 + INITIALIZATION_TIMEOUT_MILLIS }
                ?: INITIALIZATION_TIMEOUT_MILLIS,
            parentContext = parentContext,
        )

    private fun readPrivateKey(uri: String): ByteArray {
        val input = contentResolver.openInputStream(uri.toUri()) ?: error("无法打开 SSH 私钥")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PRIVATE_KEY_BYTES) { "SSH 私钥不能超过 1 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private companion object {
        const val MAX_PRIVATE_KEY_BYTES = 1024 * 1024
        const val INITIALIZATION_TIMEOUT_MILLIS = 12_000L
    }
}

private fun ProxyConfiguration.proxyConnectTimeoutMillis(): Long = when (this) {
    is ProxyConfiguration.HttpConnect -> connectTimeoutMillis
    is ProxyConfiguration.Socks5 -> connectTimeoutMillis
    is ProxyConfiguration.Ssh -> connectTimeoutMillis
}
