package com.hebo.codex.data.local

import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomQueuedDraftRepositoryTest {
    @Test
    fun persistsQueuedInputOrderAndMetadataAcrossRepositoryInstances() = runTest {
        val directory = Files.createTempDirectory(Path.of("/tmp"), "codex-queued-draft-test-").toFile()
        val dao = MemoryDraftDao()
        val input = listOf(
            UserInputPart.Text("检查 [图片]", listOf(InputTextElement(InputByteRange(7, 15), "附件"))),
            UserInputPart.Image("https://example.test/image.png", "original"),
            UserInputPart.UploadedImage("file-1", "high"),
            UserInputPart.LocalImage("/workspace/图片.png", "low"),
            UserInputPart.Audio("https://example.test/audio.wav"),
            UserInputPart.LocalAudio("/workspace/audio.wav"),
            UserInputPart.Skill("review", "/skills/review/SKILL.md"),
            UserInputPart.Mention("README.md", "/workspace/README.md"),
            UserInputPart.Text("保留结尾说明"),
        )
        val draft = queuedDraft(input)

        try {
            RoomDraftRepository(dao, directory, Json, backgroundScope).save(draft)
            val restored = RoomDraftRepository(dao, directory, Json, backgroundScope).load(draft.key)

            assertNotNull(dao.entities.value.single().queuedInputJson)
            assertEquals(draft, restored)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacingQueuedDraftClearsOriginalInputAndPreservesOrdinaryDrafts() = runTest {
        val directory = Files.createTempDirectory(Path.of("/tmp"), "codex-queued-draft-test-").toFile()
        val dao = MemoryDraftDao()
        val draft = queuedDraft(listOf(UserInputPart.Text("原始输入")))
        val replacement = draft.copy(message = "新草稿", queuedInput = null, revision = "revision-2")

        try {
            val repository = RoomDraftRepository(dao, directory, Json, backgroundScope)
            repository.save(draft)
            repository.save(replacement)
            val restored = RoomDraftRepository(dao, directory, Json, backgroundScope).load(draft.key)

            assertNull(dao.entities.value.single().queuedInputJson)
            assertEquals(replacement, restored)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedDataUrlsAndMediaTypesThatDoNotMatchTheInput() = runTest {
        val directory = Files.createTempDirectory(Path.of("/tmp"), "codex-queued-draft-test-").toFile()
        val dao = MemoryDraftDao()
        val invalidInputs = listOf(
            UserInputPart.Image("data:audio/wav;base64,UklGRg=="),
            UserInputPart.Audio("data:image/png;base64,aW1hZ2U="),
            UserInputPart.Audio("data:audio/wav;base64"),
            UserInputPart.Audio("data:audio/;base64,UklGRg=="),
        )

        try {
            val repository = RoomDraftRepository(dao, directory, Json, backgroundScope)
            invalidInputs.forEach { input ->
                val failure = runCatching { repository.save(queuedDraft(listOf(input))) }.exceptionOrNull()

                assertTrue(failure is IllegalArgumentException)
                assertTrue(dao.entities.value.isEmpty())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun queuedDraft(input: List<UserInputPart>) = ConversationDraft.Thread(
        serverId = "server-1",
        threadId = "thread-1",
        threadTitle = "队列撤回",
        message = input.filterIsInstance<UserInputPart.Text>().joinToString("\n") { it.text },
        cwd = "/workspace",
        options = TurnOptions(),
        attachments = input.mapIndexedNotNull { index, part ->
            if (part is UserInputPart.Text) null else DraftAttachment("queued:$index", part)
        },
        updatedAtMillis = 1_000,
        revision = "revision-1",
        queuedInput = input,
    )

    private class MemoryDraftDao : DraftDao {
        val entities = MutableStateFlow<List<DraftEntity>>(emptyList())

        override fun observeAll() = entities

        override suspend fun get(serverId: String, targetType: String, targetId: String): DraftEntity? =
            entities.value.firstOrNull { it.serverId == serverId && it.targetType == targetType && it.targetId == targetId }

        override suspend fun getByServer(serverId: String): List<DraftEntity> =
            entities.value.filter { it.serverId == serverId }

        override suspend fun upsert(entity: DraftEntity) {
            entities.update { current ->
                current.filterNot {
                    it.serverId == entity.serverId && it.targetType == entity.targetType && it.targetId == entity.targetId
                } + entity
            }
        }

        override suspend fun delete(serverId: String, targetType: String, targetId: String) {
            entities.update { current ->
                current.filterNot { it.serverId == serverId && it.targetType == targetType && it.targetId == targetId }
            }
        }

        override suspend fun deleteServer(serverId: String) {
            entities.update { current -> current.filterNot { it.serverId == serverId } }
        }

        override suspend fun renameThread(serverId: String, threadId: String, title: String) {
            entities.update { current ->
                current.map {
                    if (it.serverId == serverId && it.targetType == "thread" && it.targetId == threadId) {
                        it.copy(contextTitle = title)
                    } else {
                        it
                    }
                }
            }
        }
    }
}
