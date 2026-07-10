package com.hebo.codex.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class AppServerClientFactory(
    private val json: Json,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun create(endpoint: String, token: String, parentContext: CoroutineContext): AppServerConnection =
        AppServerConnection(
            okHttpClient = okHttpClient,
            json = json,
            endpoint = endpoint,
            token = token,
            parentContext = parentContext,
        )
}

