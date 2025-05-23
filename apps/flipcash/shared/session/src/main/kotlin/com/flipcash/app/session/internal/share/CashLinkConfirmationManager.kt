package com.flipcash.app.session.internal.share

import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.shareable.ShareResult
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface ShareConfirmationResult {
    data class Confirmed(val shareResult: ShareResult.ActionTaken, val didConfirm: Boolean = true) : ShareConfirmationResult
    data object TryAgain: ShareConfirmationResult
    data object Cancelled : ShareConfirmationResult
}

@Singleton
class CashLinkConfirmationManager @Inject constructor(
    private val billController: BillController,
    private val resources: ResourceHelper,
) {
    suspend fun confirm(
        shareResult: ShareResult.ActionTaken,
    ) = suspendCancellableCoroutine { cont ->
        billController.cancelAwaitForGrab()
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(R.string.prompt_title_didYouSendLink),
                subtitle = resources.getString(R.string.prompt_description_didYouSendLink),
                actions = buildList {
                    add(
                        BottomBarAction(
                            text = resources.getString(R.string.action_yes),
                            onClick = {
                                cont.resume(ShareConfirmationResult.Confirmed(shareResult))
                            },
                        )
                    )
                    add(
                        BottomBarAction(
                            text = resources.getString(R.string.action_noTryAgain),
                            style = BottomBarManager.BottomBarButtonStyle.Filled50,
                            onClick = {
                                cont.resume(ShareConfirmationResult.TryAgain)
                            }
                        )
                    )
                },
                onClose = { selection ->
                    if (selection.index == -1) {
                        cont.resume(ShareConfirmationResult.Cancelled)
                    }
                },
                onTimeout = {
                    // treat a timeout as confirmation
                    cont.resume(ShareConfirmationResult.Confirmed(shareResult, didConfirm = false))
                },
                type = BottomBarManager.BottomBarMessageType.REMOTE_SEND,
                isDismissible = false,
                showCancel = true,
                timeoutSeconds = 60
            )
        )
    }
}