package com.hebo.codex.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hebo.codex.domain.ServerAuthentication
import com.hebo.codex.domain.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.serverDataStore by preferencesDataStore(name = "servers")

data class ServerSettings(
    val servers: List<ServerProfile>,
    val activeServerId: String?,
)

class ServerStore(
    private val context: Context,
    private val tokenCipher: TokenCipher,
    private val json: Json,
) {
    val settings: Flow<ServerSettings> = context.serverDataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]
            ?.let { json.decodeStoredSettings(it) }
            ?.toDomain()
            ?: ServerSettings(servers = emptyList(), activeServerId = null)
    }

    suspend fun addServer(profile: ServerProfile, token: String) {
        val encryptedToken = withContext(Dispatchers.IO) { tokenCipher.encrypt(token) }
        update { current ->
            require(current.servers.none { it.id == profile.id }) { "服务器 ID 已存在" }
            current.copy(
                servers = current.servers + profile.toStored(encryptedToken),
                activeServerId = profile.id,
            )
        }
    }

    suspend fun updateServer(profile: ServerProfile, token: String) {
        val encryptedToken = withContext(Dispatchers.IO) { tokenCipher.encrypt(token) }
        update { current ->
            require(current.servers.any { it.id == profile.id }) { "服务器不存在" }
            current.copy(
                servers = current.servers.map { server ->
                    if (server.id == profile.id) {
                        profile.toStored(encryptedToken)
                    } else {
                        server
                    }
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
        val settings = context.serverDataStore.data
            .map { preferences ->
                preferences[SETTINGS_KEY]
                    ?.let { json.decodeStoredSettings(it) }
                    ?: StoredSettings()
            }
            .first()
        val server = settings.servers.singleOrNull { it.id == serverId }
            ?: error("服务器不存在")
        return withContext(Dispatchers.IO) { tokenCipher.decrypt(server.token) }
    }

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
    val activeServerId: String? = null,
) {
    fun toDomain(): ServerSettings = ServerSettings(
        servers = servers.map(StoredServer::toDomain),
        activeServerId = activeServerId,
    )
}

@Serializable
private data class StoredServer(
    val id: String,
    val name: String,
    val webSocketUrl: String,
    val authentication: String,
    val token: EncryptedToken,
) {
    fun toDomain(): ServerProfile = ServerProfile(
        id = id,
        name = name,
        webSocketUrl = webSocketUrl,
        authentication = ServerAuthentication.valueOf(authentication),
    )
}

private fun ServerProfile.toStored(token: EncryptedToken): StoredServer = StoredServer(
    id = id,
    name = name,
    webSocketUrl = webSocketUrl,
    authentication = authentication.name,
    token = token,
)
