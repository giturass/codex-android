package com.hebo.codex.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hebo.codex.domain.QuickPhrase
import com.hebo.codex.domain.QuickPhraseRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.quickPhraseDataStore by preferencesDataStore(name = "quick_phrases")

class QuickPhraseStore(
    private val context: Context,
    private val json: Json,
) : QuickPhraseRepository {
    override val phrases: Flow<List<QuickPhrase>> = context.quickPhraseDataStore.data.map { preferences ->
        preferences[PHRASES_KEY]
            ?.let { json.decodeFromString<StoredQuickPhrases>(it) }
            ?.phrases
            .orEmpty()
            .sortedBy(StoredQuickPhrase::sortOrder)
            .map(StoredQuickPhrase::toDomain)
    }

    override suspend fun add(content: String) {
        require(content.isNotBlank()) { "快捷短语内容不能为空" }
        update { phrases ->
            phrases + StoredQuickPhrase(
                id = UUID.randomUUID().toString(),
                content = content,
                sortOrder = phrases.size,
            )
        }
    }

    override suspend fun update(id: String, content: String) {
        require(content.isNotBlank()) { "快捷短语内容不能为空" }
        update { phrases ->
            require(phrases.any { it.id == id }) { "快捷短语不存在" }
            phrases.map { phrase ->
                if (phrase.id == id) phrase.copy(content = content) else phrase
            }
        }
    }

    override suspend fun delete(id: String) {
        update { phrases ->
            require(phrases.any { it.id == id }) { "快捷短语不存在" }
            phrases.filterNot { it.id == id }
        }
    }

    override suspend fun move(id: String, offset: Int) {
        require(offset == -1 || offset == 1) { "快捷短语只能移动一个位置" }
        update { phrases ->
            val currentIndex = phrases.indexOfFirst { it.id == id }
            require(currentIndex >= 0) { "快捷短语不存在" }
            val targetIndex = currentIndex + offset
            require(targetIndex in phrases.indices) { "快捷短语无法继续移动" }
            phrases.toMutableList().apply {
                add(targetIndex, removeAt(currentIndex))
            }
        }
    }

    private suspend fun update(transform: (List<StoredQuickPhrase>) -> List<StoredQuickPhrase>) {
        context.quickPhraseDataStore.edit { preferences ->
            val current = preferences[PHRASES_KEY]
                ?.let { json.decodeFromString<StoredQuickPhrases>(it) }
                ?.phrases
                .orEmpty()
                .sortedBy(StoredQuickPhrase::sortOrder)
            val updated = transform(current).mapIndexed { index, phrase -> phrase.copy(sortOrder = index) }
            preferences[PHRASES_KEY] = json.encodeToString(StoredQuickPhrases(updated))
        }
    }

    private companion object {
        val PHRASES_KEY = stringPreferencesKey("phrases")
    }
}

@Serializable
private data class StoredQuickPhrases(
    val phrases: List<StoredQuickPhrase> = emptyList(),
)

@Serializable
private data class StoredQuickPhrase(
    val id: String,
    val content: String,
    val sortOrder: Int,
) {
    fun toDomain(): QuickPhrase = QuickPhrase(
        id = id,
        content = content,
        sortOrder = sortOrder,
    )
}
