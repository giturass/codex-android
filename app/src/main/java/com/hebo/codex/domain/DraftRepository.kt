package com.hebo.codex.domain

import kotlinx.coroutines.flow.Flow

data class DraftFailure(
    val key: DraftKey,
    val error: Throwable,
)

interface DraftRepository {
    val summaries: Flow<List<DraftSummary>>
    val failures: Flow<DraftFailure>

    suspend fun load(key: DraftKey): ConversationDraft?

    fun schedule(draft: ConversationDraft)

    fun flush(key: DraftKey)

    suspend fun save(draft: ConversationDraft)

    suspend fun delete(key: DraftKey)

    suspend fun deleteServer(serverId: String)

    suspend fun renameThread(serverId: String, threadId: String, title: String)
}
