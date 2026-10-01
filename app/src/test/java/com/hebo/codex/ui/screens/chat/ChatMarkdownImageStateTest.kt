package com.hebo.codex.ui.screens.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ChatMarkdownImageStateTest {
    @Test
    fun sharesConcurrentRequestsAndRetainsFailureUntilRetry() = runBlocking {
        var requests = 0
        val release = CompletableDeferred<Unit>()
        val failure = IOException("图片请求失败（HTTP 404）")
        val state = ChatMarkdownImageState("image.png", "/workspace", ChatImageLoader {
            requests++
            release.await()
            throw failure
        })

        val measuring = async(start = CoroutineStart.UNDISPATCHED) { state.load() }
        val rendering = async(start = CoroutineStart.UNDISPATCHED) { state.load() }
        release.complete(Unit)
        measuring.await()
        rendering.await()
        state.load()
        assertEquals(1, requests)
        assertEquals(failure.javaClass, state.result?.exceptionOrNull()?.javaClass)
        assertEquals(failure.message, state.result?.exceptionOrNull()?.message)

        state.retry()
        assertNull(state.result)
        state.load()
        assertEquals(2, requests)
        assertEquals(failure.message, state.result?.exceptionOrNull()?.message)
    }

    @Test
    fun cancellingAnImageDoesNotTurnCancellationIntoAVisibleFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val state = ChatMarkdownImageState("image.png", "/workspace", ChatImageLoader {
            entered.complete(Unit)
            awaitCancellation()
        })
        val loading = async { state.load() }
        entered.await()
        loading.cancel()
        loading.join()
        assertNull(state.result)
    }

    @Test
    fun showsInvalidSourceAsFailureWithoutReadingServerFiles() = runBlocking {
        val state = ChatMarkdownImageState("content://images/1", "/workspace", ChatImageLoader {
            error("不应读取服务端文件")
        })
        state.load()
        assertEquals(
            "仅支持 HTTP、HTTPS、图片 data URL 或远端文件路径",
            state.result?.exceptionOrNull()?.message,
        )
    }
}
