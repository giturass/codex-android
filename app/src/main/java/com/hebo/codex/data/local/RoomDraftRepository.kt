package com.hebo.codex.data.local

import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.DraftFailure
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.hasContent
import com.hebo.codex.domain.newDraftRevision
import com.hebo.codex.domain.toSummary
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class RoomDraftRepository(
    private val dao: DraftDao,
    filesDir: File,
    private val json: Json,
    private val applicationScope: CoroutineScope,
) : DraftRepository {
    private val attachmentRoot = File(filesDir, "draft_attachments")
    private val pending = MutableStateFlow<Map<DraftKey, PendingMutation>>(emptyMap())
    private val mutableFailures = MutableSharedFlow<DraftFailure>(extraBufferCapacity = 16)
    private val jobs = mutableMapOf<DraftKey, Job>()
    private val jobsLock = Any()
    private val persistenceLocks = ConcurrentHashMap<DraftKey, Mutex>()
    private val deletedServers = ConcurrentHashMap.newKeySet<String>()
    private val databaseDrafts = dao.observeAll().shareIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    override val failures: Flow<DraftFailure> = mutableFailures
    override val summaries: Flow<List<DraftSummary>> = combine(databaseDrafts, pending) { entities, changes ->
        val merged = entities.associate { it.key to it.toSummary() }.toMutableMap()
        changes.values.forEach { change ->
            when (change) {
                is PendingMutation.Save -> merged[change.key] = change.draft.toSummary()
                is PendingMutation.Delete -> merged.remove(change.key)
            }
        }
        merged.values.sortedByDescending(DraftSummary::updatedAtMillis)
    }.distinctUntilChanged()

    init {
        applicationScope.launch {
            databaseDrafts.collect { entities ->
                val persisted = entities.associateBy { it.key }
                pending.update { changes ->
                    changes.filterValues { change ->
                        when (change) {
                            is PendingMutation.Save -> persisted[change.key]?.revision != change.draft.revision
                            is PendingMutation.Delete -> persisted.containsKey(change.key)
                        }
                    }
                }
            }
        }
    }

    override suspend fun load(key: DraftKey): ConversationDraft? {
        when (val change = pending.value[key]) {
            is PendingMutation.Save -> return change.draft
            is PendingMutation.Delete -> return null
            null -> Unit
        }
        val target = key.target.toStoredTarget()
        return dao.get(key.serverId, target.type, target.id)?.toDraft()
    }

    override fun schedule(draft: ConversationDraft) {
        if (draft.serverId in deletedServers) return
        val change = if (draft.hasContent()) PendingMutation.Save(draft) else PendingMutation.Delete(draft.key)
        pending.update { it + (change.key to change) }
        schedulePersistence(change.key, SAVE_DEBOUNCE_MILLIS)
    }

    override fun flush(key: DraftKey) {
        if (pending.value.containsKey(key)) schedulePersistence(key, 0)
    }

    override suspend fun save(draft: ConversationDraft) {
        require(draft.serverId !in deletedServers) { "服务器已删除" }
        if (!draft.hasContent()) {
            delete(draft.key)
            return
        }
        val change = PendingMutation.Save(draft)
        pending.update { it + (change.key to change) }
        cancelScheduled(change.key)
        persist(change)
    }

    override suspend fun delete(key: DraftKey) {
        val change = PendingMutation.Delete(key)
        pending.update { it + (key to change) }
        cancelScheduled(key)
        persist(change)
    }

    override suspend fun deleteServer(serverId: String) {
        deletedServers += serverId
        val persistedKeys = dao.getByServer(serverId).map { it.key }
        val pendingKeys = pending.value.keys.filter { it.serverId == serverId }
        val keys = (persistedKeys + pendingKeys).distinct()
        keys.forEach(::cancelScheduled)
        pending.update { current ->
            current + keys.associateWith(PendingMutation::Delete)
        }
        keys.forEach { key -> persistenceLocks.getOrPut(key) { Mutex() }.withLock {} }
        dao.deleteServer(serverId)
        keys.forEach(::deleteAttachmentDirectory)
    }

    override suspend fun renameThread(serverId: String, threadId: String, title: String) {
        val key = DraftKey(serverId, DraftTarget.Thread(threadId))
        val pendingDraft = (pending.value[key] as? PendingMutation.Save)?.draft as? ConversationDraft.Thread
        if (pendingDraft != null) {
            save(pendingDraft.copy(threadTitle = title, revision = newDraftRevision()))
        } else {
            dao.renameThread(serverId, threadId, title)
        }
    }

    private fun schedulePersistence(key: DraftKey, delayMillis: Long) {
        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            if (delayMillis > 0) delay(delayMillis)
            pending.value[key]?.let { change ->
                try {
                    persist(change)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableFailures.emit(DraftFailure(key, error))
                }
            }
        }
        synchronized(jobsLock) {
            jobs.remove(key)?.cancel()
            jobs[key] = job
        }
        job.invokeOnCompletion {
            synchronized(jobsLock) {
                if (jobs[key] === job) jobs.remove(key)
            }
        }
        job.start()
    }

    private fun cancelScheduled(key: DraftKey) {
        synchronized(jobsLock) { jobs.remove(key) }?.cancel()
    }

    private suspend fun persist(change: PendingMutation) {
        persistenceLocks.getOrPut(change.key) { Mutex() }.withLock {
            if (change is PendingMutation.Save && pending.value[change.key] != change) return@withLock
            when (change) {
                is PendingMutation.Save -> persistDraft(change.draft)
                is PendingMutation.Delete -> {
                    val target = change.key.target.toStoredTarget()
                    dao.delete(change.key.serverId, target.type, target.id)
                    deleteAttachmentDirectory(change.key)
                }
            }
        }
    }

    private suspend fun persistDraft(draft: ConversationDraft) {
        val entity = draft.toEntity()
        dao.upsert(entity)
        val retainedFiles = json.decodeFromString<List<StoredAttachment>>(entity.attachmentsJson)
            .filter { it.type == ATTACHMENT_EMBEDDED_IMAGE }
            .mapTo(mutableSetOf(), StoredAttachment::value)
        cleanAttachmentDirectory(draft.key, retainedFiles)
    }

    private suspend fun ConversationDraft.toEntity(): DraftEntity = withContext(Dispatchers.IO) {
        when (this@toEntity) {
            is ConversationDraft.NewTask -> DraftEntity(
                serverId = serverId,
                targetType = TARGET_NEW_TASK,
                targetId = draftId,
                contextTitle = "新建任务",
                cwd = cwd,
                message = message,
                optionsJson = json.encodeToString(StoredThreadOptions.from(options)),
                attachmentsJson = json.encodeToString(emptyList<StoredAttachment>()),
                attachmentCount = 0,
                updatedAtMillis = updatedAtMillis,
                revision = revision,
            )

            is ConversationDraft.Thread -> {
                val storedAttachments = attachments.map { it.toStored(key) }
                DraftEntity(
                    serverId = serverId,
                    targetType = TARGET_THREAD,
                    targetId = threadId,
                    contextTitle = threadTitle,
                    cwd = cwd,
                    message = message,
                    optionsJson = json.encodeToString(StoredTurnOptions.from(options)),
                    attachmentsJson = json.encodeToString(storedAttachments),
                    attachmentCount = storedAttachments.size,
                    updatedAtMillis = updatedAtMillis,
                    revision = revision,
                )
            }
        }
    }

    private suspend fun DraftEntity.toDraft(): ConversationDraft = withContext(Dispatchers.IO) {
        when (targetType) {
            TARGET_NEW_TASK -> ConversationDraft.NewTask(
                serverId = serverId,
                draftId = targetId,
                message = message,
                cwd = cwd,
                options = json.decodeFromString<StoredThreadOptions>(optionsJson).toDomain(),
                updatedAtMillis = updatedAtMillis,
                revision = revision,
            )

            TARGET_THREAD -> ConversationDraft.Thread(
                serverId = serverId,
                threadId = targetId,
                threadTitle = contextTitle,
                message = message,
                cwd = cwd,
                options = json.decodeFromString<StoredTurnOptions>(optionsJson).toDomain(),
                attachments = json.decodeFromString<List<StoredAttachment>>(attachmentsJson).map { it.toDomain(key) },
                updatedAtMillis = updatedAtMillis,
                revision = revision,
            )

            else -> error("未知草稿类型：$targetType")
        }
    }

    private fun DraftAttachment.toStored(key: DraftKey): StoredAttachment = when (val value = input) {
        is UserInputPart.Text -> StoredAttachment(id, ATTACHMENT_TEXT, value = value.text)
        is UserInputPart.LocalImage -> StoredAttachment(id, ATTACHMENT_LOCAL_IMAGE, value = value.path)
        is UserInputPart.Skill -> StoredAttachment(id, ATTACHMENT_SKILL, name = value.name, value = value.path)
        is UserInputPart.Mention -> StoredAttachment(id, ATTACHMENT_MENTION, name = value.name, value = value.path)
        is UserInputPart.Image -> {
            if (!value.url.startsWith("data:")) {
                StoredAttachment(id, ATTACHMENT_IMAGE_URL, value = value.url)
            } else {
                val match = DATA_URL_PATTERN.matchEntire(value.url)
                    ?: throw IllegalArgumentException("图片 data URL 无效")
                val metadata = match.groupValues[1]
                val mimeType = metadata.substringBefore(';')
                require(mimeType.startsWith("image/")) { "data URL 不是图片" }
                val file = attachmentFile(key, id)
                if (!file.exists()) {
                    val encoded = match.groupValues[2]
                    val bytes = if (metadata.split(';').any { it.equals("base64", ignoreCase = true) }) {
                        runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                            .getOrElse { throw IllegalArgumentException("图片 data URL 无效", it) }
                    } else {
                        Uri.decode(encoded).toByteArray(Charsets.UTF_8)
                    }
                    writeAtomic(file, bytes)
                }
                StoredAttachment(
                    id = id,
                    type = ATTACHMENT_EMBEDDED_IMAGE,
                    value = file.name,
                    mimeType = mimeType,
                )
            }
        }
    }

    private fun StoredAttachment.toDomain(key: DraftKey): DraftAttachment = when (type) {
        ATTACHMENT_TEXT -> DraftAttachment(id, UserInputPart.Text(value))
        ATTACHMENT_IMAGE_URL -> DraftAttachment(id, UserInputPart.Image(value))
        ATTACHMENT_LOCAL_IMAGE -> DraftAttachment(id, UserInputPart.LocalImage(value))
        ATTACHMENT_SKILL -> DraftAttachment(id, UserInputPart.Skill(requireNotNull(name), value))
        ATTACHMENT_MENTION -> DraftAttachment(id, UserInputPart.Mention(requireNotNull(name), value))
        ATTACHMENT_EMBEDDED_IMAGE -> {
            val file = storedAttachmentFile(key, value)
            val bytes = file.readBytes()
            val mime = requireNotNull(mimeType)
            DraftAttachment(
                id = id,
                input = UserInputPart.Image("data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"),
                previewUri = Uri.fromFile(file).toString(),
            )
        }

        else -> error("未知草稿附件类型：$type")
    }

    private fun attachmentFile(key: DraftKey, attachmentId: String): File {
        val directory = attachmentDirectory(key).apply { mkdirs() }
        return File(directory, sha256(attachmentId))
    }

    private fun storedAttachmentFile(key: DraftKey, fileName: String): File {
        require(FILE_NAME_PATTERN.matches(fileName)) { "草稿附件路径无效" }
        return File(attachmentDirectory(key), fileName)
    }

    private fun attachmentDirectory(key: DraftKey): File = File(
        attachmentRoot,
        sha256("${key.serverId}\u0000${key.target.toStoredTarget().type}\u0000${key.target.id}"),
    )

    private fun cleanAttachmentDirectory(key: DraftKey, retainedFiles: Set<String>) {
        val directory = attachmentDirectory(key)
        directory.listFiles()?.forEach { file ->
            if (file.name !in retainedFiles) file.delete()
        }
        if (directory.list().isNullOrEmpty()) directory.delete()
    }

    private fun deleteAttachmentDirectory(key: DraftKey) {
        attachmentDirectory(key).deleteRecursively()
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private sealed interface PendingMutation {
        val key: DraftKey

        data class Save(val draft: ConversationDraft) : PendingMutation {
            override val key: DraftKey = draft.key
        }

        data class Delete(override val key: DraftKey) : PendingMutation
    }

    private data class StoredTarget(val type: String, val id: String)

    private fun DraftTarget.toStoredTarget(): StoredTarget = when (this) {
        is DraftTarget.NewTask -> StoredTarget(TARGET_NEW_TASK, id)
        is DraftTarget.Thread -> StoredTarget(TARGET_THREAD, id)
    }

    private val DraftEntity.key: DraftKey
        get() = DraftKey(
            serverId,
            when (targetType) {
                TARGET_NEW_TASK -> DraftTarget.NewTask(targetId)
                TARGET_THREAD -> DraftTarget.Thread(targetId)
                else -> error("未知草稿类型：$targetType")
            },
        )

    private fun DraftEntity.toSummary(): DraftSummary = DraftSummary(
        key = key,
        preview = com.hebo.codex.domain.draftPreview(message, attachmentCount),
        contextTitle = contextTitle,
        cwd = cwd,
        attachmentCount = attachmentCount,
        updatedAtMillis = updatedAtMillis,
        revision = revision,
    )

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 350L
        const val TARGET_NEW_TASK = "new_task"
        const val TARGET_THREAD = "thread"
        const val ATTACHMENT_TEXT = "text"
        const val ATTACHMENT_IMAGE_URL = "image_url"
        const val ATTACHMENT_EMBEDDED_IMAGE = "embedded_image"
        const val ATTACHMENT_LOCAL_IMAGE = "local_image"
        const val ATTACHMENT_SKILL = "skill"
        const val ATTACHMENT_MENTION = "mention"
        val DATA_URL_PATTERN = Regex("data:([^,]*),(.*)", setOf(RegexOption.DOT_MATCHES_ALL))
        val FILE_NAME_PATTERN = Regex("[0-9a-f]{64}")
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

@Serializable
private data class StoredAttachment(
    val id: String,
    val type: String,
    val name: String? = null,
    val value: String,
    val mimeType: String? = null,
)

@Serializable
private data class StoredThreadOptions(
    val model: String?,
    val modelProvider: String?,
    val serviceTier: String?,
    val approvalPolicy: String?,
    val approvalsReviewer: String?,
    val sandboxMode: String?,
    val configJson: String?,
    val baseInstructions: String?,
    val developerInstructions: String?,
    val personality: String?,
    val ephemeral: Boolean,
) {
    fun toDomain(): ThreadOptions = ThreadOptions(
        model = model,
        modelProvider = modelProvider,
        serviceTier = serviceTier,
        approvalPolicy = approvalPolicy?.let(ApprovalPolicy::valueOf),
        approvalsReviewer = approvalsReviewer?.let(ApprovalReviewer::valueOf),
        sandboxMode = sandboxMode?.let(SandboxMode::valueOf),
        configJson = configJson,
        baseInstructions = baseInstructions,
        developerInstructions = developerInstructions,
        personality = personality?.let(Personality::valueOf),
        ephemeral = ephemeral,
    )

    companion object {
        fun from(value: ThreadOptions): StoredThreadOptions = StoredThreadOptions(
            model = value.model,
            modelProvider = value.modelProvider,
            serviceTier = value.serviceTier,
            approvalPolicy = value.approvalPolicy?.name,
            approvalsReviewer = value.approvalsReviewer?.name,
            sandboxMode = value.sandboxMode?.name,
            configJson = value.configJson,
            baseInstructions = value.baseInstructions,
            developerInstructions = value.developerInstructions,
            personality = value.personality?.name,
            ephemeral = value.ephemeral,
        )
    }
}

@Serializable
private data class StoredTurnOptions(
    val cwd: String?,
    val model: String?,
    val serviceTier: String?,
    val reasoningEffort: String?,
    val reasoningSummary: String?,
    val personality: String?,
    val approvalPolicy: String?,
    val approvalsReviewer: String?,
    val sandboxMode: String?,
    val sandboxNetworkAccess: Boolean,
    val sandboxWritableRoots: List<String>,
    val sandboxExcludeTmpdirEnvVar: Boolean,
    val sandboxExcludeSlashTmp: Boolean,
    val outputSchema: String?,
) {
    fun toDomain(): TurnOptions = TurnOptions(
        cwd = cwd,
        model = model,
        serviceTier = serviceTier,
        reasoningEffort = reasoningEffort,
        reasoningSummary = reasoningSummary?.let(ReasoningSummary::valueOf),
        personality = personality?.let(Personality::valueOf),
        approvalPolicy = approvalPolicy?.let(ApprovalPolicy::valueOf),
        approvalsReviewer = approvalsReviewer?.let(ApprovalReviewer::valueOf),
        sandboxMode = sandboxMode?.let(SandboxMode::valueOf),
        sandboxNetworkAccess = sandboxNetworkAccess,
        sandboxWritableRoots = sandboxWritableRoots,
        sandboxExcludeTmpdirEnvVar = sandboxExcludeTmpdirEnvVar,
        sandboxExcludeSlashTmp = sandboxExcludeSlashTmp,
        outputSchema = outputSchema,
    )

    companion object {
        fun from(value: TurnOptions): StoredTurnOptions = StoredTurnOptions(
            cwd = value.cwd,
            model = value.model,
            serviceTier = value.serviceTier,
            reasoningEffort = value.reasoningEffort,
            reasoningSummary = value.reasoningSummary?.name,
            personality = value.personality?.name,
            approvalPolicy = value.approvalPolicy?.name,
            approvalsReviewer = value.approvalsReviewer?.name,
            sandboxMode = value.sandboxMode?.name,
            sandboxNetworkAccess = value.sandboxNetworkAccess,
            sandboxWritableRoots = value.sandboxWritableRoots,
            sandboxExcludeTmpdirEnvVar = value.sandboxExcludeTmpdirEnvVar,
            sandboxExcludeSlashTmp = value.sandboxExcludeSlashTmp,
            outputSchema = value.outputSchema,
        )
    }
}
