package com.hebo.codex.domain

import kotlinx.coroutines.flow.Flow

data class QuickPhrase(
    val id: String,
    val content: String,
    val sortOrder: Int,
)

fun quickPhraseInput(content: String): List<UserInputPart> {
    require(content.isNotBlank()) { "快捷短语内容不能为空" }
    return listOf(UserInputPart.Text(content))
}

interface QuickPhraseRepository {
    val phrases: Flow<List<QuickPhrase>>

    suspend fun add(content: String)

    suspend fun update(id: String, content: String)

    suspend fun delete(id: String)

    suspend fun move(id: String, offset: Int)
}
