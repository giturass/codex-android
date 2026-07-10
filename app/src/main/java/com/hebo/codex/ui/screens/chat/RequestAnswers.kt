package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.UserQuestion

internal fun allQuestionsAnswered(
    questions: List<UserQuestion>,
    answers: Map<String, List<String>>,
): Boolean = questions.all { question -> answers[question.id].orEmpty().any(String::isNotBlank) }
