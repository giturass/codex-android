package com.hebo.codex.data.local

import androidx.room3.ColumnInfo
import androidx.room3.AutoMigration
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "drafts",
    primaryKeys = ["server_id", "target_type", "target_id"],
    indices = [Index(value = ["server_id", "updated_at_millis"])],
)
internal data class DraftEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "context_title") val contextTitle: String,
    @ColumnInfo(name = "cwd") val cwd: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    @ColumnInfo(name = "attachments_json") val attachmentsJson: String,
    @ColumnInfo(name = "attachment_count") val attachmentCount: Int,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
    @ColumnInfo(name = "revision") val revision: String,
    @ColumnInfo(name = "queued_input_json") val queuedInputJson: String? = null,
)

@Dao
internal interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updated_at_millis DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query(
        """
        SELECT * FROM drafts
        WHERE server_id = :serverId AND target_type = :targetType AND target_id = :targetId
        """,
    )
    suspend fun get(serverId: String, targetType: String, targetId: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE server_id = :serverId")
    suspend fun getByServer(serverId: String): List<DraftEntity>

    @Upsert
    suspend fun upsert(entity: DraftEntity)

    @Query(
        """
        DELETE FROM drafts
        WHERE server_id = :serverId AND target_type = :targetType AND target_id = :targetId
        """,
    )
    suspend fun delete(serverId: String, targetType: String, targetId: String)

    @Query("DELETE FROM drafts WHERE server_id = :serverId")
    suspend fun deleteServer(serverId: String)

    @Query(
        """
        UPDATE drafts SET context_title = :title
        WHERE server_id = :serverId AND target_type = 'thread' AND target_id = :threadId
        """,
    )
    suspend fun renameThread(serverId: String, threadId: String, title: String)
}

@Database(
    entities = [DraftEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class CodexDatabase : RoomDatabase() {
    internal abstract fun draftDao(): DraftDao
}
