package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatImageSourceTest {
    @Test
    fun keepsThePersistentDeviceImageDataUrl() {
        val dataUrl = "data:image/png;base64,AQID"
        assertEquals(ChatImageSource.Url(dataUrl), UserInputPart.Image(dataUrl).chatImageSource())
        assertEquals(ChatImageSource.Url(dataUrl), UserMessagePart.Image(url = dataUrl).chatImageSource())
    }

    @Test
    fun resolvesLocalImagesOnTheServer() {
        val path = "/workspace/photo.png"
        assertEquals(ChatImageSource.ServerFile(path), UserInputPart.LocalImage(path).chatImageSource())
        assertEquals(ChatImageSource.ServerFile(path), UserMessagePart.LocalImage(path).chatImageSource())
    }

    @Test
    fun usesImageUrlWhenTheMessageAlsoHasAFileReference() {
        assertEquals(
            ChatImageSource.Url("https://example.com/photo.png"),
            UserMessagePart.Image(url = "https://example.com/photo.png", fileId = "file-1").chatImageSource(),
        )
        assertEquals(ChatImageSource.FileReference("file-1"), UserMessagePart.Image(fileId = "file-1").chatImageSource())
    }

    @Test
    fun excludesNonImageParts() {
        assertNull(UserInputPart.Text("photo.png").chatImageSource())
        assertNull(UserInputPart.Mention("photo", "/workspace/photo.png").chatImageSource())
        assertNull(UserMessagePart.Text("photo.png").chatImageSource())
        assertNull(UserMessagePart.LocalAudio("/workspace/audio.wav").chatImageSource())
    }
}
