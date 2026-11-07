package `fun`.kirari.hanako.feature.overlay.state

import `fun`.kirari.hanako.core.model.latestAnswerText

internal fun OverlayUiState.currentAnswerText(): String =
    if (working && !sendingFollowUp) liveAnswerText
    else result?.latestAnswerText().orEmpty().ifBlank { result?.automationAction?.text.orEmpty() }

/** An empty running task has only the animated controls, never an empty answer card. */
internal fun OverlayUiState.hasAnswerBubbleContent(): Boolean =
    currentAnswerText().isNotBlank() || !error.isNullOrBlank() || result?.followUpTurns?.isNotEmpty() == true
