package com.hebo.codex.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface UserInputPart {
    @Serializable @SerialName("text")
    data class Text(val text: String, val textElements: List<InputTextElement> = emptyList()) : UserInputPart

    @Serializable @SerialName("image")
    data class Image(val url: String, val detail: String? = null) : UserInputPart

    @Serializable @SerialName("uploadedImage")
    data class UploadedImage(val fileId: String, val detail: String? = null) : UserInputPart

    @Serializable @SerialName("localImage")
    data class LocalImage(val path: String, val detail: String? = null) : UserInputPart

    @Serializable @SerialName("audio")
    data class Audio(val url: String) : UserInputPart

    @Serializable @SerialName("localAudio")
    data class LocalAudio(val path: String) : UserInputPart

    @Serializable @SerialName("skill")
    data class Skill(val name: String, val path: String) : UserInputPart

    @Serializable @SerialName("mention")
    data class Mention(val name: String, val path: String) : UserInputPart
}

@Serializable
data class InputTextElement(val byteRange: InputByteRange, val placeholder: String? = null)

@Serializable
data class InputByteRange(val start: Int, val end: Int)
