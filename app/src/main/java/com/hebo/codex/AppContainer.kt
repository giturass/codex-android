package com.hebo.codex

import android.content.Context
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.local.TokenCipher
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.domain.CodexRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val serverStore = ServerStore(
        context = context,
        tokenCipher = TokenCipher(),
        json = json,
    )

    val repository = CodexRepository(
        serverStore = serverStore,
        clientFactory = AppServerClientFactory(json),
        applicationScope = applicationScope,
    )
}
