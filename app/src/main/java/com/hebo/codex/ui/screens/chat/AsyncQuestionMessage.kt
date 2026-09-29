package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.AsyncUserInputQuestion
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.isAsyncQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AsyncQuestionMessage(
    item: ConversationItem.AgentMessage,
    enabled: Boolean,
    onReply: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (!item.isAsyncQuestion) return
    val scope = rememberCoroutineScope()
    var sendingIndex by remember(item.turnId, item.id) { mutableStateOf<Int?>(null) }
    var errorIndex by rememberSaveable(item.turnId, item.id) { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item.questions.forEachIndexed { index, question ->
            AsyncQuestion(
                question = question,
                stateKey = "${item.turnId}:${item.id}:$index",
                enabled = enabled && sendingIndex == null,
                sending = sendingIndex == index,
                hasError = errorIndex == index,
                onReply = { text, onAccepted ->
                    if (enabled && sendingIndex == null && text.isNotBlank()) {
                        sendingIndex = index
                        errorIndex = null
                        scope.launch {
                            try {
                                if (onReply(text)) onAccepted() else errorIndex = index
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                errorIndex = index
                            } finally {
                                sendingIndex = null
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AsyncQuestion(
    question: AsyncUserInputQuestion,
    stateKey: String,
    enabled: Boolean,
    sending: Boolean,
    hasError: Boolean,
    onReply: (String, () -> Unit) -> Unit,
) {
    var showCustomReply by rememberSaveable(stateKey) { mutableStateOf(false) }
    var draft by rememberSaveable(stateKey) { mutableStateOf("") }
    val hasOptions = question.options.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.title, style = MaterialTheme.typography.titleSmall)
            question.options.forEach { option ->
                OutlinedButton(
                    onClick = { onReply(option) {} },
                    enabled = enabled && option.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(option, modifier = Modifier.fillMaxWidth())
                }
            }
            if (showCustomReply || !hasOptions) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("自定义回复") },
                    enabled = !sending,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (hasOptions) {
                        TextButton(onClick = { showCustomReply = false }, enabled = !sending) {
                            Text("收起")
                        }
                    }
                    TextButton(
                        onClick = { onReply(draft) { draft = "" } },
                        enabled = enabled && draft.isNotBlank(),
                    ) {
                        Text("发送回复")
                    }
                }
            } else {
                TextButton(onClick = { showCustomReply = true }, enabled = !sending) {
                    Text("自定义回复")
                }
                Text(
                    "点击选项直接发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sending || hasError) {
                Text(
                    text = if (sending) "发送中…" else "发送结果未确认，请检查聊天记录后再重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
