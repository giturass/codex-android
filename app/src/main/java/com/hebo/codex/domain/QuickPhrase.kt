package com.hebo.codex.domain

import kotlinx.coroutines.flow.Flow

data class QuickPhrase(
    val id: String,
    val content: String,
    val sortOrder: Int,
)

interface QuickPhraseRepository {
    val phrases: Flow<List<QuickPhrase>>

    suspend fun add(content: String)

    suspend fun update(id: String, content: String)

    suspend fun delete(id: String)

    suspend fun move(id: String, offset: Int)
}
