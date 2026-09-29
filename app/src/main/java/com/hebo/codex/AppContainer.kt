package com.hebo.codex

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.hebo.codex.data.local.CodexDatabase
import com.hebo.codex.data.local.RoomDraftRepository
import com.hebo.codex.data.local.QuickPhraseStore
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.local.CredentialCipher
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.QuickPhraseRepository
import com.hebo.codex.domain.QueueWithdrawalCoordinator
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
        credentialCipher = CredentialCipher(),
        json = json,
    )
    private val database = Room.databaseBuilder(
        context = context,
        klass = CodexDatabase::class.java,
        name = "codex.db",
    ).setDriver(AndroidSQLiteDriver()).build()
    val notifications = ConversationNotificationController(context)
    val draftRepository: DraftRepository = RoomDraftRepository(
        dao = database.draftDao(),
        filesDir = context.filesDir,
        json = json,
        applicationScope = applicationScope,
    )
    val quickPhraseRepository: QuickPhraseRepository = QuickPhraseStore(
        context = context,
        json = json,
    )

    val repository = CodexRepository(
        serverStore = serverStore,
        clientFactory = AppServerClientFactory(json, context.contentResolver),
        applicationScope = applicationScope,
        notifyConversation = notifications::notify,
    )
    val queueWithdrawalCoordinator = QueueWithdrawalCoordinator(
        applicationScope = applicationScope,
        draftRepository = draftRepository,
        deleteQueuedInput = repository::deleteQueuedInput,
    )
}
