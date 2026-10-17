package com.hebo.codex.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationReviewsTest {
    @Test
    fun newThreadReviewWaitsForConversationRegistrationBeforeStreaming() = runTest {
        val created = CompletableDeferred<Unit>()
        val conversations = mutableMapOf("source" to conversation("source"))
        val target = ReviewTarget.Commit("commit-sha", "检查提交")
        var reviewStarted = false
        val result = async {
            startConversationReview(
                threadId = "source",
                cwd = "/workspace/project",
                target = target,
                destination = ReviewDestination.NEW_THREAD,
                createConversation = { cwd ->
                    assertEquals("/workspace/project", cwd)
                    created.await()
                    conversations["review"] = conversation("review")
                    "review"
                },
                reviewInline = { threadId, reviewTarget ->
                    reviewStarted = true
                    assertEquals("review", threadId)
                    assertEquals(target, reviewTarget)
                    conversations[threadId] = conversations.getValue(threadId)
                        .reduceConversationEvent(ConversationEvent.TurnStarted("review-turn"))
                    threadId
                },
            )
        }

        runCurrent()
        assertFalse(reviewStarted)
        created.complete(Unit)

        assertEquals("review", result.await())
        assertEquals("review-turn", conversations.getValue("review").activeTurnId)
        assertNull(conversations.getValue("source").activeTurnId)
    }

    @Test
    fun currentThreadReviewDoesNotCreateAConversation() = runTest {
        val target = ReviewTarget.UncommittedChanges
        val result = startConversationReview(
            threadId = "source",
            cwd = "/workspace/project",
            target = target,
            destination = ReviewDestination.CURRENT_THREAD,
            createConversation = { error("当前会话评审不应创建新会话") },
            reviewInline = { threadId, reviewTarget ->
                assertEquals("source", threadId)
                assertEquals(target, reviewTarget)
                threadId
            },
        )

        assertEquals("source", result)
    }

    @Test
    fun creationFailureNeverStartsReviewOnTheSourceConversation() = runTest {
        val error = IllegalStateException("无法创建会话")
        var reviewStarted = false
        val result = runCatching {
            startConversationReview(
                threadId = "source",
                cwd = "/workspace/project",
                target = ReviewTarget.UncommittedChanges,
                destination = ReviewDestination.NEW_THREAD,
                createConversation = { throw error },
                reviewInline = { threadId, _ ->
                    reviewStarted = true
                    threadId
                },
            )
        }

        assertSame(error, result.exceptionOrNull())
        assertFalse(reviewStarted)
    }

    private fun conversation(threadId: String) = ConversationState(
        serverId = "server",
        serverName = "服务器",
        threadId = threadId,
        title = "新会话",
        cwd = "/workspace/project",
    )
}
