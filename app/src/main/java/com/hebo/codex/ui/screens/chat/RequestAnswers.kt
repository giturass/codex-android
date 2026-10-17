package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.UserQuestion

internal val PendingServerRequest.Questions.title: String
    get() = if (isBlocking) "Codex 正在等待你的回答" else "Codex 有一个问题"

internal fun allQuestionsAnswered(
    questions: List<UserQuestion>,
    answers: Map<String, List<String>>,
): Boolean = questions.all { question -> answers[question.id].orEmpty().any(String::isNotBlank) }
