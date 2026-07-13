package com.hebo.codex.ui.screens.chat

/** 将 app-server 的完整执行 argv 转为面向用户的命令正文 */
internal fun shellCommandForDisplay(command: String): String {
    val arguments = splitPosixShellWords(command) ?: return command
    if (arguments.size != 3) return command

    val executable = arguments[0]
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val shell = executable.substringBeforeLast('.', missingDelimiterValue = executable)
    val flag = arguments[1]
    return if (shell in SUPPORTED_SHELLS && flag in SUPPORTED_COMMAND_FLAGS) {
        arguments[2]
    } else {
        command
    }
}

private fun splitPosixShellWords(command: String): List<String>? {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    var state = ShellQuoteState.UNQUOTED
    var wordStarted = false
    var index = 0

    fun finishWord() {
        if (!wordStarted) return
        words += word.toString()
        word.clear()
        wordStarted = false
    }

    while (index < command.length) {
        val character = command[index]
        when (state) {
            ShellQuoteState.UNQUOTED -> when {
                character.isWhitespace() -> finishWord()
                character == '\'' -> {
                    state = ShellQuoteState.SINGLE_QUOTED
                    wordStarted = true
                }

                character == '"' -> {
                    state = ShellQuoteState.DOUBLE_QUOTED
                    wordStarted = true
                }

                character == '\\' -> {
                    val escaped = command.getOrNull(++index) ?: return null
                    if (escaped != '\n') {
                        word.append(escaped)
                        wordStarted = true
                    }
                }

                else -> {
                    word.append(character)
                    wordStarted = true
                }
            }

            ShellQuoteState.SINGLE_QUOTED -> if (character == '\'') {
                state = ShellQuoteState.UNQUOTED
            } else {
                word.append(character)
            }

            ShellQuoteState.DOUBLE_QUOTED -> when {
                character == '"' -> state = ShellQuoteState.UNQUOTED
                character == '\\' -> {
                    val escaped = command.getOrNull(++index) ?: return null
                    when (escaped) {
                        '$', '`', '"', '\\' -> word.append(escaped)
                        '\n' -> Unit
                        else -> word.append('\\').append(escaped)
                    }
                }

                else -> word.append(character)
            }
        }
        index++
    }

    if (state != ShellQuoteState.UNQUOTED) return null
    finishWord()
    return words
}

private enum class ShellQuoteState {
    UNQUOTED,
    SINGLE_QUOTED,
    DOUBLE_QUOTED,
}

private val SUPPORTED_SHELLS = setOf("zsh", "bash", "sh")
private val SUPPORTED_COMMAND_FLAGS = setOf("-lc", "-c")
