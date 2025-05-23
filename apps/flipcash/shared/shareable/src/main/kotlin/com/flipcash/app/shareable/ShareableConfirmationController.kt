package com.flipcash.app.shareable


sealed interface ShareConfirmationResult {
    data class Confirmed(val shareResult: ShareResult.ActionTaken, val didConfirm: Boolean = true) :
        ShareConfirmationResult

    data object TryAgain : ShareConfirmationResult
    data object Cancelled : ShareConfirmationResult
}

interface ShareableConfirmationController {
    suspend fun confirm(
        shareable: Shareable,
        shareResult: ShareResult.ActionTaken
    ): ShareConfirmationResult
}