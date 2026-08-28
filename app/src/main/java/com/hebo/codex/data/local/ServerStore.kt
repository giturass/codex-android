package com.hebo.codex.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hebo.codex.domain.HttpProxyAuthentication
import com.hebo.codex.domain.ProxyConfiguration
import com.hebo.codex.domain.ProxyLastTest
import com.hebo.codex.domain.ProxyProfile
import com.hebo.codex.domain.ProxyTlsPolicy
import com.hebo.codex.domain.ServerAuthentication
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.Socks5Authentication
import com.hebo.codex.domain.Socks5DnsResolution
import com.hebo.codex.domain.SshAuthentication
import com.hebo.codex.domain.SshHostKeyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.serverDataStore by preferencesDataStore(name = "servers")

data class ServerSettings(
    val servers: List<ServerProfile>,
    val proxies: List<ProxyProfile>,
    val activeServerId: String?,
)

class ServerStore(
    private val context: Context,
    private val credentialCipher: CredentialCipher,
    private val json: Json,
) {
    val settings: Flow<ServerSettings> = context.serverDataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]
            ?.let { json.decodeStoredSettings(it) }
            ?.toDomain()
            ?: ServerSettings(servers = emptyList(), proxies = emptyList(), activeServerId = null)
    }

    suspend fun addServer(profile: ServerProfile, token: String) {
        val encryptedToken = encrypt(token)
        update { current ->
            require(current.servers.none { it.id == profile.id }) { "服务器 ID 已存在" }
            require(profile.proxyId == null || current.proxies.any { it.id == profile.proxyId }) { "代理不存在" }
            current.copy(
                servers = current.servers + profile.toStored(encryptedToken),
                activeServerId = profile.id,
            )
        }
    }

    suspend fun updateServer(profile: ServerProfile, token: String) {
        val encryptedToken = encrypt(token)
        update { current ->
            require(current.servers.any { it.id == profile.id }) { "服务器不存在" }
            require(profile.proxyId == null || current.proxies.any { it.id == profile.proxyId }) { "代理不存在" }
            current.copy(
                servers = current.servers.map { server ->
                    if (server.id == profile.id) profile.toStored(encryptedToken) else server
                },
            )
        }
    }

    suspend fun removeServer(serverId: String) {
        update { current ->
            val remaining = current.servers.filterNot { it.id == serverId }
            current.copy(
                servers = remaining,
                activeServerId = when (current.activeServerId) {
                    serverId -> remaining.firstOrNull()?.id
                    else -> current.activeServerId
                },
            )
        }
    }

    suspend fun selectServer(serverId: String) {
        update { current ->
            require(current.servers.any { it.id == serverId }) { "服务器不存在" }
            current.copy(activeServerId = serverId)
        }
    }

    suspend fun readToken(serverId: String): String {
        val server = readStored().servers.singleOrNull { it.id == serverId } ?: error("服务器不存在")
        return decrypt(server.token)
    }

    suspend fun addProxy(profile: ProxyProfile, credential: String?) {
        val encryptedCredential = credential?.let { encrypt(it) }
        requireCredential(profile.configuration, encryptedCredential)
        update { current ->
            require(current.proxies.none { it.id == profile.id }) { "代理 ID 已存在" }
            require(current.proxies.none { it.name.equals(profile.name, ignoreCase = true) }) { "代理名称已存在" }
            current.copy(proxies = current.proxies + profile.toStored(encryptedCredential))
        }
    }

    suspend fun updateProxy(profile: ProxyProfile, credential: String?, clearCredential: Boolean) {
        val encryptedCredential = credential?.let { encrypt(it) }
        update { current ->
            val previous = current.proxies.singleOrNull { it.id == profile.id } ?: error("代理不存在")
            require(current.proxies.none {
                it.id != profile.id && it.name.equals(profile.name, ignoreCase = true)
            }) { "代理名称已存在" }
            val reusable = previous.configuration.credentialKind() == profile.configuration.credentialKind()
            val resolvedCredential = when {
                profile.configuration.credentialKind() == CredentialKind.NONE -> null
                encryptedCredential != null -> encryptedCredential
                clearCredential -> null
                reusable -> previous.credential
                else -> null
            }
            requireCredential(profile.configuration, resolvedCredential)
            current.copy(
                proxies = current.proxies.map { stored ->
                    if (stored.id == profile.id) profile.toStored(resolvedCredential, stored.createdAtMillis)
                    else stored
                },
            )
        }
    }

    suspend fun removeProxy(proxyId: String) {
        update { current ->
            require(current.proxies.any { it.id == proxyId }) { "代理不存在" }
            require(current.servers.none { it.proxyId == proxyId }) { "代理仍被服务器引用" }
            current.copy(proxies = current.proxies.filterNot { it.id == proxyId })
        }
    }

    suspend fun readProxyCredential(proxyId: String): String? {
        val proxy = readStored().proxies.singleOrNull { it.id == proxyId } ?: error("代理不存在")
        return proxy.credential?.let { decrypt(it) }
    }

    suspend fun confirmSshHostKey(proxyId: String, record: SshHostKeyRecord) {
        updateProxyMetadata(proxyId) { proxy ->
            require(proxy.configuration is StoredProxyConfiguration.Ssh) { "当前代理不是 SSH 代理" }
            proxy.copy(
                version = proxy.version + 1,
                sshHostKey = record.toStored(),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun removeSshHostKey(proxyId: String) {
        updateProxyMetadata(proxyId) { proxy ->
            proxy.copy(
                version = proxy.version + 1,
                sshHostKey = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun recordProxyTest(proxyId: String, succeeded: Boolean) {
        updateProxyMetadata(proxyId) { proxy ->
            proxy.copy(
                lastTest = StoredProxyLastTest(succeeded, System.currentTimeMillis()),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun updateProxyMetadata(
        proxyId: String,
        transform: (StoredProxy) -> StoredProxy,
    ) {
        update { current ->
            require(current.proxies.any { it.id == proxyId }) { "代理不存在" }
            current.copy(
                proxies = current.proxies.map { proxy ->
                    if (proxy.id == proxyId) transform(proxy) else proxy
                },
            )
        }
    }

    private suspend fun readStored(): StoredSettings = context.serverDataStore.data
        .map { preferences ->
            preferences[SETTINGS_KEY]?.let { value -> json.decodeStoredSettings(value) } ?: StoredSettings()
        }
        .first()

    private suspend fun encrypt(value: String): EncryptedCredential =
        withContext(Dispatchers.IO) { credentialCipher.encrypt(value) }

    private suspend fun decrypt(value: EncryptedCredential): String =
        withContext(Dispatchers.IO) { credentialCipher.decrypt(value) }

    private suspend fun update(transform: (StoredSettings) -> StoredSettings) {
        context.serverDataStore.edit { preferences ->
            val current = preferences[SETTINGS_KEY]
                ?.let { json.decodeStoredSettings(it) }
                ?: StoredSettings()
            preferences[SETTINGS_KEY] = json.encodeToString(StoredSettings.serializer(), transform(current))
        }
    }

    private fun Json.decodeStoredSettings(value: String): StoredSettings =
        decodeFromString(StoredSettings.serializer(), value)

    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("settings")
    }
}

@Serializable
private data class StoredSettings(
    val servers: List<StoredServer> = emptyList(),
    val proxies: List<StoredProxy> = emptyList(),
    val activeServerId: String? = null,
) {
    fun toDomain(): ServerSettings {
        val referenceCounts = servers.mapNotNull(StoredServer::proxyId).groupingBy { it }.eachCount()
        return ServerSettings(
            servers = servers.map(StoredServer::toDomain),
            proxies = proxies.map { it.toDomain(referenceCounts[it.id] ?: 0) },
            activeServerId = activeServerId,
        )
    }
}

@Serializable
private data class StoredServer(
    val id: String,
    val name: String,
    val webSocketUrl: String,
    val authentication: String,
    val token: EncryptedCredential,
    val proxyId: String? = null,
) {
    fun toDomain(): ServerProfile = ServerProfile(
        id = id,
        name = name,
        webSocketUrl = webSocketUrl,
        authentication = ServerAuthentication.valueOf(authentication),
        proxyId = proxyId,
    )
}

@Serializable
private data class StoredProxy(
    val id: String,
    val name: String,
    val version: Long,
    val configuration: StoredProxyConfiguration,
    val credential: EncryptedCredential? = null,
    val sshHostKey: StoredSshHostKey? = null,
    val lastTest: StoredProxyLastTest? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(referenceCount: Int): ProxyProfile = ProxyProfile(
        id = id,
        name = name,
        version = version,
        configuration = configuration.toDomain(),
        credentialConfigured = credential != null,
        sshHostKey = sshHostKey?.toDomain(),
        lastTest = lastTest?.toDomain(),
        referencedServerCount = referenceCount,
    )
}

@Serializable
private sealed interface StoredProxyConfiguration {
    @Serializable
    @SerialName("httpConnect")
    data class HttpConnect(
        val url: String,
        val authentication: String,
        val username: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val connectTimeoutMillis: Long,
        val tlsPolicy: String,
    ) : StoredProxyConfiguration

    @Serializable
    @SerialName("socks5")
    data class Socks5(
        val host: String,
        val port: Int,
        val authentication: String,
        val username: String? = null,
        val dnsResolution: String,
        val connectTimeoutMillis: Long,
    ) : StoredProxyConfiguration

    @Serializable
    @SerialName("ssh")
    data class Ssh(
        val host: String,
        val port: Int,
        val username: String,
        val authentication: String,
        val privateKeyUri: String? = null,
        val connectTimeoutMillis: Long,
        val keepAliveIntervalMillis: Int,
        val keepAliveMaxFailures: Int,
    ) : StoredProxyConfiguration
}

@Serializable
private data class StoredSshHostKey(
    val host: String,
    val port: Int,
    val algorithm: String,
    val sha256Fingerprint: String,
    val confirmedAtMillis: Long,
) {
    fun toDomain() = SshHostKeyRecord(host, port, algorithm, sha256Fingerprint, confirmedAtMillis)
}

@Serializable
private data class StoredProxyLastTest(
    val succeeded: Boolean,
    val testedAtMillis: Long,
) {
    fun toDomain() = ProxyLastTest(succeeded, testedAtMillis)
}

private enum class CredentialKind {
    NONE,
    HTTP_BASIC,
    HTTP_BEARER,
    SOCKS_PASSWORD,
    SSH_PASSPHRASE,
    SSH_PASSWORD,
}

private fun StoredProxyConfiguration.credentialKind(): CredentialKind = toDomain().credentialKind()

private fun ProxyConfiguration.credentialKind(): CredentialKind = when (this) {
    is ProxyConfiguration.HttpConnect -> when (authentication) {
        HttpProxyAuthentication.NONE -> CredentialKind.NONE
        HttpProxyAuthentication.BASIC -> CredentialKind.HTTP_BASIC
        HttpProxyAuthentication.BEARER -> CredentialKind.HTTP_BEARER
    }
    is ProxyConfiguration.Socks5 -> when (authentication) {
        Socks5Authentication.NONE -> CredentialKind.NONE
        Socks5Authentication.USERNAME_PASSWORD -> CredentialKind.SOCKS_PASSWORD
    }
    is ProxyConfiguration.Ssh -> when (authentication) {
        SshAuthentication.PRIVATE_KEY -> CredentialKind.SSH_PASSPHRASE
        SshAuthentication.PASSWORD -> CredentialKind.SSH_PASSWORD
    }
}

private fun requireCredential(configuration: ProxyConfiguration, credential: EncryptedCredential?) {
    val required = when (configuration.credentialKind()) {
        CredentialKind.HTTP_BASIC,
        CredentialKind.HTTP_BEARER,
        CredentialKind.SOCKS_PASSWORD,
        CredentialKind.SSH_PASSWORD,
        -> true
        CredentialKind.NONE,
        CredentialKind.SSH_PASSPHRASE,
        -> false
    }
    require(!required || credential != null) { "当前认证方式需要凭据" }
}

private fun ServerProfile.toStored(token: EncryptedCredential): StoredServer = StoredServer(
    id = id,
    name = name,
    webSocketUrl = webSocketUrl,
    authentication = authentication.name,
    token = token,
    proxyId = proxyId,
)

private fun ProxyProfile.toStored(
    credential: EncryptedCredential?,
    createdAtMillis: Long = System.currentTimeMillis(),
): StoredProxy {
    val now = System.currentTimeMillis()
    return StoredProxy(
        id = id,
        name = name,
        version = version,
        configuration = configuration.toStored(),
        credential = credential,
        sshHostKey = sshHostKey?.toStored(),
        lastTest = lastTest?.let { StoredProxyLastTest(it.succeeded, it.testedAtMillis) },
        createdAtMillis = createdAtMillis,
        updatedAtMillis = now,
    )
}

private fun ProxyConfiguration.toStored(): StoredProxyConfiguration = when (this) {
    is ProxyConfiguration.HttpConnect -> StoredProxyConfiguration.HttpConnect(
        url = url,
        authentication = authentication.name,
        username = username,
        headers = headers,
        connectTimeoutMillis = connectTimeoutMillis,
        tlsPolicy = tlsPolicy.name,
    )
    is ProxyConfiguration.Socks5 -> StoredProxyConfiguration.Socks5(
        host = host,
        port = port,
        authentication = authentication.name,
        username = username,
        dnsResolution = dnsResolution.name,
        connectTimeoutMillis = connectTimeoutMillis,
    )
    is ProxyConfiguration.Ssh -> StoredProxyConfiguration.Ssh(
        host = host,
        port = port,
        username = username,
        authentication = authentication.name,
        privateKeyUri = privateKeyUri,
        connectTimeoutMillis = connectTimeoutMillis,
        keepAliveIntervalMillis = keepAliveIntervalMillis,
        keepAliveMaxFailures = keepAliveMaxFailures,
    )
}

private fun StoredProxyConfiguration.toDomain(): ProxyConfiguration = when (this) {
    is StoredProxyConfiguration.HttpConnect -> ProxyConfiguration.HttpConnect(
        url = url,
        authentication = HttpProxyAuthentication.valueOf(authentication),
        username = username,
        headers = headers,
        connectTimeoutMillis = connectTimeoutMillis,
        tlsPolicy = ProxyTlsPolicy.valueOf(tlsPolicy),
    )
    is StoredProxyConfiguration.Socks5 -> ProxyConfiguration.Socks5(
        host = host,
        port = port,
        authentication = Socks5Authentication.valueOf(authentication),
        username = username,
        dnsResolution = Socks5DnsResolution.valueOf(dnsResolution),
        connectTimeoutMillis = connectTimeoutMillis,
    )
    is StoredProxyConfiguration.Ssh -> ProxyConfiguration.Ssh(
        host = host,
        port = port,
        username = username,
        authentication = SshAuthentication.valueOf(authentication),
        privateKeyUri = privateKeyUri,
        connectTimeoutMillis = connectTimeoutMillis,
        keepAliveIntervalMillis = keepAliveIntervalMillis,
        keepAliveMaxFailures = keepAliveMaxFailures,
    )
}

private fun SshHostKeyRecord.toStored() = StoredSshHostKey(
    host = host,
    port = port,
    algorithm = algorithm,
    sha256Fingerprint = sha256Fingerprint,
    confirmedAtMillis = confirmedAtMillis,
)
