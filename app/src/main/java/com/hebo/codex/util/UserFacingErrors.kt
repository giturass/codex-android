package com.hebo.codex.util

import com.hebo.codex.data.remote.ConnectionException
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.RpcException
import com.hebo.codex.data.remote.UnsupportedThreadHistoryModeException
import com.hebo.codex.domain.UserFacingError
import kotlinx.serialization.json.JsonNull
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Throwable.toUserFacingError(defaultSummary: String = "操作失败"): UserFacingError = when (this) {
    is IllegalArgumentException -> UserFacingError(message ?: defaultSummary)
    is RpcException -> UserFacingError(
        summary = when (code) {
            -32601 -> "当前服务器不支持此操作"
            -32602 -> "发送给服务器的参数不正确"
            -32001 -> "服务器正忙，请稍后重试"
            else -> "服务器未能完成操作"
        },
        technicalDetails = "RPC $code：$message" +
            (data?.takeUnless { it is JsonNull }?.let { "\n$it" } ?: ""),
    )

    is ConnectionException -> UserFacingError(
        summary = if (rejected) "服务器拒绝连接" else "连接已中断",
        technicalDetails = message,
    )

    is ProtocolException -> UserFacingError(
        summary = "服务器返回了无法识别的数据",
        technicalDetails = message,
    )

    is UnsupportedThreadHistoryModeException -> UserFacingError(
        summary = "当前会话使用 legacy 历史格式，无法加载完整历史",
    )

    is SocketTimeoutException -> UserFacingError("连接服务器超时", message)
    is UnknownHostException -> UserFacingError("找不到服务器", message)
    is ConnectException -> UserFacingError("无法连接服务器", message)
    is SSLException -> UserFacingError("安全连接失败", message)
    is IOException -> UserFacingError("网络连接失败", message)
    else -> UserFacingError(defaultSummary, technicalDescription())
}

fun serverError(summary: String, technicalDetails: String?): UserFacingError =
    UserFacingError(summary, technicalDetails?.takeIf(String::isNotBlank))

private fun Throwable.technicalDescription(): String =
    listOfNotNull(javaClass.name, message?.takeIf(String::isNotBlank)).joinToString("：")
