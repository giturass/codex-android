package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.RuntimeTerminalInput

@Composable
internal fun TerminalInputDetails(input: RuntimeTerminalInput, command: ConversationItem.CommandExecution?) {
    Text(
        text = command?.let { "对应命令 · ${shellCommandForDisplay(it.command)}" } ?: "命令记录未加载",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "进程 ${input.processId}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SelectionContainer {
        Text(
            text = formatTerminalInput(input.stdin),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun terminalInputCommand(
    input: RuntimeTerminalInput,
    items: List<ConversationItem>,
): ConversationItem.CommandExecution? = items.asSequence()
    .filterIsInstance<ConversationItem.CommandExecution>()
    // 后续回合的终端输入仍指向启动命令的 itemId
    .firstOrNull { it.id == input.itemId }

/** 只格式化展示，保留运行记录中的原始输入，不推断进程执行结果 */
internal fun formatTerminalInput(input: String): String = TERMINAL_CONTROL_CHARACTERS.replace(input) { match ->
    val control = match.value
    val label = when (control) {
        "\u0003" -> "Ctrl+C：请求中断"
        "\u0004" -> "Ctrl+D：结束输入"
        "\b", "\u007f" -> "Backspace：退格"
        "\t" -> "Tab：制表"
        "\n" -> "Enter：换行"
        "\r" -> "Enter：回车"
        "\r\n" -> "Enter：回车换行"
        "\u001a" -> "Ctrl+Z：请求挂起"
        "\u001b" -> "Esc"
        else -> "U+${control.first().code.toString(16).uppercase().padStart(4, '0')}"
    }
    "⟦$label⟧${if (control.endsWith('\n')) "\n" else ""}"
}

private val TERMINAL_CONTROL_CHARACTERS = Regex("\r\n|[\u0000-\u001f\u007f-\u009f]")
