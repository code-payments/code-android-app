package com.flipcash.app.shareable.internal

import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.shareable.ShareConfirmationResult
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareableConfirmationController
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


internal class InternalShareConfirmationController(
    private val billController: BillController,
    private val resources: ResourceHelper,
) : ShareableConfirmationController {

    override suspend fun confirm(
        shareable: Shareable,
        shareResult: ShareResult.ActionTaken
    ): ShareConfirmationResult {
        return when (shareable) {
            is Shareable.CashLink -> confirmCashLink(shareResult)
            is Shareable.DownloadLink -> ShareConfirmationResult.Confirmed(shareResult)
        }
    }

    private suspend fun confirmCashLink(shareResult: ShareResult.ActionTaken): ShareConfirmationResult =
        suspendCancellableCoroutine { cont ->
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
                                    if (cont.isActive) {
                                        cont.resume(ShareConfirmationResult.Confirmed(shareResult))
                                    }
                                },
                            )
                        )
                        add(
                            BottomBarAction(
                                text = resources.getString(R.string.action_noTryAgain),
                                style = BottomBarManager.BottomBarButtonStyle.Filled50,
                                onClick = {
                                    if (cont.isActive) {
                                        cont.resume(ShareConfirmationResult.TryAgain)
                                    }
                                }
                            )
                        )
                    },
                    onClose = { selection ->
                        if (selection.index == -1) {
                            if (cont.isActive) {
                                cont.resume(ShareConfirmationResult.Cancelled)
                            }
                        }
                    },
                    onTimeout = {
                        if (cont.isActive) {
                            // treat a timeout as confirmation
                            cont.resume(
                                ShareConfirmationResult.Confirmed(
                                    shareResult,
                                    didConfirm = false
                                )
                            )
                        }
                    },
                    type = BottomBarManager.BottomBarMessageType.REMOTE_SEND,
                    isDismissible = false,
                    showCancel = true,
                    timeoutSeconds = 60
                )
            )
        }
}