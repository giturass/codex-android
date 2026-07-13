package com.hebo.codex.ui.screens.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal suspend fun cacheRemoteFileForOpening(
    context: Context,
    fileName: String,
    bytes: ByteArray,
): Uri = withContext(Dispatchers.IO) {
    val cacheRoot = File(context.cacheDir, REMOTE_FILE_CACHE_DIRECTORY).apply {
        check(mkdirs() || isDirectory) { "无法创建临时文件目录" }
    }
    val expiration = System.currentTimeMillis() - REMOTE_FILE_CACHE_LIFETIME_MILLIS
    cacheRoot.listFiles()?.filter { it.lastModified() < expiration }?.forEach(File::deleteRecursively)

    val fileDirectory = File(cacheRoot, UUID.randomUUID().toString()).apply {
        check(mkdirs()) { "无法创建临时文件目录" }
    }
    val cachedFile = File(fileDirectory, fileName)
    cachedFile.outputStream().use { output -> output.write(bytes) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cachedFile)
}

internal fun openCachedRemoteFile(
    context: Context,
    uri: Uri,
    fileName: String,
    mimeType: String,
) {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(viewIntent, "使用其他应用打开 $fileName").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

internal fun remoteFileMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return TECHNICAL_MIME_TYPES[extension]
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

private val TECHNICAL_MIME_TYPES = mapOf(
    "csv" to "text/csv",
    "json" to "application/json",
    "jsonl" to "application/x-ndjson",
    "log" to "text/plain",
    "md" to "text/markdown",
    "markdown" to "text/markdown",
    "svg" to "image/svg+xml",
    "toml" to "application/toml",
    "yaml" to "application/yaml",
    "yml" to "application/yaml",
)

private const val REMOTE_FILE_CACHE_DIRECTORY = "remote-files"
private const val REMOTE_FILE_CACHE_LIFETIME_MILLIS = 24 * 60 * 60 * 1_000L
