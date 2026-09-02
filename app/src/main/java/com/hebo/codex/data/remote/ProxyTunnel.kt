package com.hebo.codex.data.remote

import android.annotation.SuppressLint
import com.hebo.codex.domain.HttpProxyAuthentication
import com.hebo.codex.domain.ProxyConfiguration
import com.hebo.codex.domain.ProxyProfile
import com.hebo.codex.domain.ProxyTlsPolicy
import com.hebo.codex.domain.Socks5Authentication
import com.hebo.codex.domain.Socks5DnsResolution
import com.hebo.codex.domain.SshAuthentication
import com.hebo.codex.domain.SshHostKeyRecord
import com.jcraft.jsch.Channel
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketImpl
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.SocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

internal data class ResolvedProxyConnection(
    val profile: ProxyProfile,
    val credential: String?,
)

internal data class WebSocketTarget(
    val host: String,
    val port: Int,
    val secure: Boolean,
    val hostHeader: String,
) {
    companion object {
        fun parse(endpoint: String): WebSocketTarget {
            val uri = URI(endpoint)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "ws" || scheme == "wss") { "WebSocket URL 必须使用 ws 或 wss 协议" }
            val secure = scheme == "wss"
            val port = if (uri.port >= 0) uri.port else if (secure) 443 else 80
            val defaultPort = if (secure) 443 else 80
            val host = requireNotNull(uri.host) { "WebSocket URL 缺少主机名" }
            val authorityHost = if (host.contains(':')) "[$host]" else host
            return WebSocketTarget(
                host = host,
                port = port,
                secure = secure,
                hostHeader = if (port == defaultPort) authorityHost else "$authorityHost:$port",
            )
        }
    }
}

internal sealed class SshHostKeyVerificationException(
    message: String,
    val observed: SshHostKeyRecord,
) : IOException(message) {
    class Unknown(observed: SshHostKeyRecord) : SshHostKeyVerificationException(
        "SSH 主机密钥尚未确认：${observed.sha256Fingerprint}",
        observed,
    )

    class Changed(
        val previous: SshHostKeyRecord,
        observed: SshHostKeyRecord,
    ) : SshHostKeyVerificationException(
        "SSH 主机密钥已变化，原指纹 ${previous.sha256Fingerprint}，新指纹 ${observed.sha256Fingerprint}",
        observed,
    )
}

internal class ProxyTunnelFactory(
    private val readPrivateKey: (String) -> ByteArray,
) {
    fun open(target: WebSocketTarget, proxy: ResolvedProxyConnection): Socket =
        when (val configuration = proxy.profile.configuration) {
            is ProxyConfiguration.HttpConnect -> openHttpConnect(target, configuration, proxy.credential)
            is ProxyConfiguration.Socks5 -> openSocks5(target, configuration, proxy.credential)
            is ProxyConfiguration.Ssh -> openSsh(
                target,
                configuration,
                proxy.credential,
                proxy.profile.sshHostKey,
            )
        }

    private fun openHttpConnect(
        target: WebSocketTarget,
        configuration: ProxyConfiguration.HttpConnect,
        credential: String?,
    ): Socket {
        val proxyUri = URI(configuration.url)
        val proxyPort = if (proxyUri.port >= 0) proxyUri.port else if (proxyUri.scheme == "https") 443 else 80
        var socket: Socket = connectedSocket(
            proxyUri.host,
            proxyPort,
            configuration.connectTimeoutMillis,
        )
        try {
            if (proxyUri.scheme == "https") {
                socket = wrapTls(
                    socket = socket,
                    host = proxyUri.host,
                    port = proxyPort,
                    allowInvalidCertificate = configuration.tlsPolicy == ProxyTlsPolicy.ALLOW_INVALID_CERTIFICATE,
                )
            }
            socket.soTimeout = configuration.connectTimeoutMillis.toInt()
            performHttpConnectHandshake(
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                authority = target.hostHeader,
                configuration = configuration,
                credential = credential,
            )
            socket.soTimeout = 0
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun openSocks5(
        target: WebSocketTarget,
        configuration: ProxyConfiguration.Socks5,
        credential: String?,
    ): Socket {
        val socket = connectedSocket(configuration.host, configuration.port, configuration.connectTimeoutMillis)
        try {
            socket.soTimeout = configuration.connectTimeoutMillis.toInt()
            performSocks5Handshake(
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                targetHost = target.host,
                targetPort = target.port,
                configuration = configuration,
                credential = credential,
            )
            socket.soTimeout = 0
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun openSsh(
        target: WebSocketTarget,
        configuration: ProxyConfiguration.Ssh,
        credential: String?,
        expectedHostKey: SshHostKeyRecord?,
    ): Socket {
        val jsch = JSch()
        when (configuration.authentication) {
            SshAuthentication.PRIVATE_KEY -> {
                val uri = requireNotNull(configuration.privateKeyUri) { "SSH 私钥未配置" }
                val key = readPrivateKey(uri)
                val passphrase = credential?.toByteArray(StandardCharsets.UTF_8)
                try {
                    require(key.size <= MAX_PRIVATE_KEY_BYTES) { "SSH 私钥不能超过 1 MiB" }
                    jsch.addIdentity(
                        uri,
                        key,
                        null,
                        passphrase,
                    )
                } finally {
                    key.fill(0)
                    passphrase?.fill(0)
                }
            }

            SshAuthentication.PASSWORD -> Unit
        }

        val hostKeys = PinnedHostKeyRepository(jsch, configuration, expectedHostKey)
        val session = jsch.getSession(configuration.username, configuration.host, configuration.port)
        session.setHostKeyRepository(hostKeys)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig(
            "PreferredAuthentications",
            when (configuration.authentication) {
                SshAuthentication.PRIVATE_KEY -> "publickey"
                SshAuthentication.PASSWORD -> "password"
            },
        )
        val sshPassword = if (configuration.authentication == SshAuthentication.PASSWORD) {
            requireNotNull(credential) { "SSH 密码未配置" }.toByteArray(StandardCharsets.UTF_8)
                .also(session::setPassword)
        } else {
            null
        }
        session.serverAliveInterval = configuration.keepAliveIntervalMillis
        session.serverAliveCountMax = configuration.keepAliveMaxFailures

        try {
            session.connect(configuration.connectTimeoutMillis.toInt())
        } catch (error: Throwable) {
            session.disconnect()
            val observed = hostKeys.observed
            if (observed != null) {
                if (expectedHostKey == null) throw SshHostKeyVerificationException.Unknown(observed)
                if (!hostKeys.matchesExpected) {
                    throw SshHostKeyVerificationException.Changed(expectedHostKey, observed)
                }
            }
            throw IOException("SSH 连接失败：${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            sshPassword?.fill(0)
        }

        var channel: Channel? = null
        try {
            channel = session.getStreamForwarder(target.host, target.port)
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(configuration.connectTimeoutMillis.toInt())
            return StreamSocket(
                input = input,
                output = output,
                remoteHost = target.host,
                remotePort = target.port,
                closeAction = {
                    channel.disconnect()
                    session.disconnect()
                },
            )
        } catch (error: Throwable) {
            channel?.disconnect()
            session.disconnect()
            throw IOException("SSH 隧道建立失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }
}

internal class ProxyTunnelSocketFactory(
    private val target: WebSocketTarget,
    private val proxy: ResolvedProxyConnection,
    private val tunnelFactory: ProxyTunnelFactory,
) : SocketFactory() {
    override fun createSocket(): Socket {
        val rawSocket = tunnelFactory.open(target, proxy)
        return try {
            wrapTargetTls(rawSocket, target)
        } catch (error: Throwable) {
            rawSocket.close()
            throw error
        }
    }

    override fun createSocket(host: String, port: Int): Socket = unsupported()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = unsupported()

    override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("代理隧道只支持无参数 Socket 创建方式")
}

internal fun performHttpConnectHandshake(
    input: InputStream,
    output: OutputStream,
    authority: String,
    configuration: ProxyConfiguration.HttpConnect,
    credential: String?,
) {
    val request = buildString {
        append("CONNECT ").append(authority).append(" HTTP/1.1\r\n")
        append("Host: ").append(authority).append("\r\n")
        append("Proxy-Connection: Keep-Alive\r\n")
        configuration.headers.forEach { (name, value) ->
            append(name).append(": ").append(value).append("\r\n")
        }
        when (configuration.authentication) {
            HttpProxyAuthentication.NONE -> Unit
            HttpProxyAuthentication.BASIC -> {
                val username = requireNotNull(configuration.username)
                val secret = requireNotNull(credential) { "HTTP 代理密码未配置" }
                val encoded = Base64.getEncoder().encodeToString("$username:$secret".toByteArray())
                append("Proxy-Authorization: Basic ").append(encoded).append("\r\n")
            }

            HttpProxyAuthentication.BEARER -> append("Proxy-Authorization: Bearer ")
                .append(requireNotNull(credential) { "HTTP 代理令牌未配置" })
                .append("\r\n")
        }
        append("\r\n")
    }
    output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
    output.flush()

    val response = readHttpHeaders(input)
    val statusLine = response.lineSequence().firstOrNull().orEmpty()
    val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
        ?: throw IOException("HTTP 代理返回了无效响应")
    if (statusCode !in 200..299) {
        throw ConnectionException("HTTP 代理拒绝 CONNECT：HTTP $statusCode", rejected = true)
    }
}

internal fun performSocks5Handshake(
    input: InputStream,
    output: OutputStream,
    targetHost: String,
    targetPort: Int,
    configuration: ProxyConfiguration.Socks5,
    credential: String?,
) {
    val authMethod = when (configuration.authentication) {
        Socks5Authentication.NONE -> SOCKS_NO_AUTH
        Socks5Authentication.USERNAME_PASSWORD -> SOCKS_USERNAME_PASSWORD
    }
    output.write(byteArrayOf(SOCKS_VERSION, 1, authMethod))
    output.flush()
    requireByte(input, SOCKS_VERSION, "SOCKS5 版本无效")
    val selectedMethod = readByte(input)
    if (selectedMethod != (authMethod.toInt() and 0xff)) {
        throw ConnectionException("SOCKS5 代理不接受所选认证方式", rejected = true)
    }

    if (configuration.authentication == Socks5Authentication.USERNAME_PASSWORD) {
        val username = requireNotNull(configuration.username).toByteArray(StandardCharsets.UTF_8)
        val password = requireNotNull(credential) { "SOCKS5 密码未配置" }.toByteArray(StandardCharsets.UTF_8)
        try {
            require(username.size in 1..255 && password.size in 1..255) { "SOCKS5 用户名或密码过长" }
            output.write(1)
            output.write(username.size)
            output.write(username)
            output.write(password.size)
            output.write(password)
            output.flush()
            requireByte(input, 1, "SOCKS5 认证响应版本无效")
            if (readByte(input) != 0) throw ConnectionException("SOCKS5 用户名或密码错误", rejected = true)
        } finally {
            password.fill(0)
        }
    }

    output.write(byteArrayOf(SOCKS_VERSION, SOCKS_CONNECT, 0))
    val address = socksTargetAddress(targetHost, configuration.dnsResolution)
    output.write(address)
    output.write(targetPort ushr 8)
    output.write(targetPort and 0xff)
    output.flush()

    requireByte(input, SOCKS_VERSION, "SOCKS5 连接响应版本无效")
    val reply = readByte(input)
    readByte(input)
    val addressType = readByte(input)
    consumeSocksAddress(input, addressType)
    readFully(input, 2)
    if (reply != 0) throw IOException(socksReplyMessage(reply))
}

internal fun wrapTargetTls(socket: Socket, target: WebSocketTarget): Socket =
    if (target.secure) wrapTls(socket, target.host, target.port, allowInvalidCertificate = false) else socket

private fun connectedSocket(host: String, port: Int, timeoutMillis: Long): Socket = Socket().apply {
    connect(InetSocketAddress(host, port), timeoutMillis.toInt())
    tcpNoDelay = true
}

private fun wrapTls(
    socket: Socket,
    host: String,
    port: Int,
    allowInvalidCertificate: Boolean,
): SSLSocket {
    val factory: SSLSocketFactory = if (allowInvalidCertificate) {
        insecureSslSocketFactory()
    } else {
        SSLSocketFactory.getDefault() as SSLSocketFactory
    }
    val sslSocket = factory.createSocket(socket, host, port, true) as SSLSocket
    val parameters = sslSocket.sslParameters
    if (!allowInvalidCertificate) parameters.endpointIdentificationAlgorithm = "HTTPS"
    if (!isIpAddress(host)) parameters.serverNames = listOf(SNIHostName(host))
    sslSocket.sslParameters = parameters
    sslSocket.startHandshake()
    if (!allowInvalidCertificate && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sslSocket.session)) {
        sslSocket.close()
        throw IOException("TLS 证书与主机名不匹配：$host")
    }
    return sslSocket
}

@SuppressLint("CustomX509TrustManager")
private fun insecureSslSocketFactory(): SSLSocketFactory {
    val trustAll = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    })
    return SSLContext.getInstance("TLS").apply {
        init(null, trustAll, SecureRandom())
    }.socketFactory
}

internal fun readHttpHeaders(input: InputStream): String {
    val bytes = ArrayList<Byte>()
    var matched = 0
    while (bytes.size < MAX_HTTP_HEADER_BYTES) {
        val value = input.read()
        if (value < 0) throw IOException("HTTP 代理在响应完成前关闭连接")
        bytes += value.toByte()
        matched = when {
            matched == 0 && value == '\r'.code -> 1
            matched == 1 && value == '\n'.code -> 2
            matched == 2 && value == '\r'.code -> 3
            matched == 3 && value == '\n'.code -> 4
            value == '\r'.code -> 1
            else -> 0
        }
        if (matched == 4) return bytes.toByteArray().toString(StandardCharsets.ISO_8859_1)
    }
    throw IOException("HTTP 代理响应头超过限制")
}

private fun socksTargetAddress(host: String, resolution: Socks5DnsResolution): ByteArray {
    val address = parseNumericAddress(host) ?: if (resolution == Socks5DnsResolution.LOCAL) {
        InetAddress.getAllByName(host).firstOrNull() ?: throw IOException("无法解析目标主机：$host")
    } else {
        null
    }
    if (address is Inet4Address) return byteArrayOf(SOCKS_IPV4) + address.address
    if (address is Inet6Address) return byteArrayOf(SOCKS_IPV6) + address.address
    val domain = host.toByteArray(StandardCharsets.UTF_8)
    require(domain.size in 1..255) { "SOCKS5 目标域名过长" }
    return byteArrayOf(SOCKS_DOMAIN, domain.size.toByte()) + domain
}

private fun parseNumericAddress(host: String): InetAddress? = when {
    host.contains(':') -> runCatching { InetAddress.getByName(host) }.getOrNull()
    host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) -> runCatching {
        val parts = host.split('.').map(String::toInt)
        require(parts.all { it in 0..255 })
        InetAddress.getByAddress(parts.map(Int::toByte).toByteArray())
    }.getOrNull()
    else -> null
}

private fun consumeSocksAddress(input: InputStream, addressType: Int) {
    when (addressType) {
        SOCKS_IPV4.toInt() -> readFully(input, 4)
        SOCKS_IPV6.toInt() -> readFully(input, 16)
        SOCKS_DOMAIN.toInt() -> readFully(input, readByte(input))
        else -> throw IOException("SOCKS5 响应包含未知地址类型：$addressType")
    }
}

private fun socksReplyMessage(reply: Int): String = when (reply) {
    1 -> "SOCKS5 代理报告常规故障"
    2 -> "SOCKS5 代理规则不允许连接"
    3 -> "SOCKS5 代理报告网络不可达"
    4 -> "SOCKS5 代理报告目标主机不可达"
    5 -> "SOCKS5 代理报告连接被拒绝"
    6 -> "SOCKS5 代理报告 TTL 过期"
    7 -> "SOCKS5 代理不支持 CONNECT"
    8 -> "SOCKS5 代理不支持目标地址类型"
    else -> "SOCKS5 代理连接失败：$reply"
}

private fun requireByte(input: InputStream, expected: Byte, message: String) {
    if (readByte(input) != (expected.toInt() and 0xff)) throw IOException(message)
}

private fun requireByte(input: InputStream, expected: Int, message: String) {
    if (readByte(input) != expected) throw IOException(message)
}

private fun readByte(input: InputStream): Int = input.read().also {
    if (it < 0) throw IOException("代理在握手完成前关闭连接")
}

private fun readFully(input: InputStream, length: Int): ByteArray = ByteArray(length).also { bytes ->
    var offset = 0
    while (offset < length) {
        val count = input.read(bytes, offset, length - offset)
        if (count < 0) throw IOException("代理在握手完成前关闭连接")
        offset += count
    }
}

private fun isIpAddress(host: String): Boolean = parseNumericAddress(host) != null

private class PinnedHostKeyRepository(
    private val jsch: JSch,
    private val configuration: ProxyConfiguration.Ssh,
    private val expected: SshHostKeyRecord?,
) : HostKeyRepository {
    var observed: SshHostKeyRecord? = null
        private set
    var matchesExpected: Boolean = false
        private set

    override fun check(host: String, key: ByteArray): Int {
        val parsed = HostKey(host, key)
        val current = SshHostKeyRecord(
            host = configuration.host,
            port = configuration.port,
            algorithm = parsed.type,
            sha256Fingerprint = parsed.getFingerPrint(jsch),
            confirmedAtMillis = 0,
        )
        observed = current
        matchesExpected = expected != null &&
            expected.host == current.host &&
            expected.port == current.port &&
            expected.algorithm == current.algorithm &&
            expected.sha256Fingerprint == current.sha256Fingerprint
        return when {
            matchesExpected -> HostKeyRepository.OK
            expected == null -> HostKeyRepository.NOT_INCLUDED
            else -> HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "Codex Android"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

private class StreamSocket(
    private val input: InputStream,
    private val output: OutputStream,
    private val remoteHost: String,
    private val remotePort: Int,
    private val closeAction: () -> Unit,
) : Socket(null as SocketImpl?) {
    private var closed = false
    private var timeout = 0

    override fun getInputStream(): InputStream {
        if (closed) throw SocketException("Socket is closed")
        return input
    }

    override fun getOutputStream(): OutputStream {
        if (closed) throw SocketException("Socket is closed")
        return output
    }

    override fun isConnected(): Boolean = !closed
    override fun isBound(): Boolean = !closed
    override fun isClosed(): Boolean = closed
    override fun getPort(): Int = remotePort
    override fun getRemoteSocketAddress(): SocketAddress = InetSocketAddress.createUnresolved(remoteHost, remotePort)
    override fun setSoTimeout(timeout: Int) { this.timeout = timeout }
    override fun getSoTimeout(): Int = timeout
    override fun setTcpNoDelay(on: Boolean) = Unit
    override fun getTcpNoDelay(): Boolean = true
    override fun setReuseAddress(on: Boolean) = Unit
    override fun getReuseAddress(): Boolean = false
    override fun setKeepAlive(on: Boolean) = Unit
    override fun getKeepAlive(): Boolean = true
    override fun shutdownInput() = input.close()
    override fun shutdownOutput() = output.close()

    override fun close() {
        if (closed) return
        closed = true
        runCatching { closeAction() }
    }
}

private const val MAX_HTTP_HEADER_BYTES = 32 * 1024
private const val MAX_PRIVATE_KEY_BYTES = 1024 * 1024
private const val SOCKS_VERSION: Byte = 5
private const val SOCKS_NO_AUTH: Byte = 0
private const val SOCKS_USERNAME_PASSWORD: Byte = 2
private const val SOCKS_CONNECT: Byte = 1
private const val SOCKS_IPV4: Byte = 1
private const val SOCKS_DOMAIN: Byte = 3
private const val SOCKS_IPV6: Byte = 4
