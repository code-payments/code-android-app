package com.flipcash.app.messenger.internal.invites

import com.flipcash.app.funding.bottomBarCardAction
import com.flipcash.features.messenger.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper

/**
 * The bottom-bar messages of the invite flow. They are top-level so the view models and the
 * previews draw the same ones.
 */

/**
 * Frame 1a, drawn by the bottom bar's card sheet that Group Money and Add Money use, so it keeps
 * their spacing, colours and dismissal.
 */
internal fun showInviteHostSheet(
    resources: ResourceHelper,
    groupName: String,
    onShareInviteLink: () -> Unit,
    onShareJoinLink: () -> Unit,
    onManageInvites: () -> Unit,
) {
    BottomBarManager.showMessage(
        title = resources.getString(R.string.title_inviteToGroup, groupName),
        actions = listOf(
            bottomBarCardAction(
                title = resources.getString(R.string.action_shareInviteLinkLong),
                subtitle = resources.getString(R.string.subtitle_shareInviteLink),
                iconRes = R.drawable.ic_send_outlined,
                testTag = "invite_action_share_invite",
                onClick = onShareInviteLink,
            ),
            bottomBarCardAction(
                title = resources.getString(R.string.action_shareJoinLink),
                subtitle = resources.getString(R.string.subtitle_shareJoinLink),
                iconRes = R.drawable.ic_link_outlined,
                testTag = "invite_action_share_join",
                onClick = onShareJoinLink,
            ),
            bottomBarCardAction(
                title = resources.getString(R.string.action_manageInvites),
                subtitle = resources.getString(R.string.subtitle_manageInvites),
                iconRes = R.drawable.ic_checklist,
                testTag = "invite_action_manage",
                onClick = onManageInvites,
            ),
        ),
    )
}

/**
 * Frame 1c: the group holds the most active links the server allows. With [onManageInvites] the
 * buttons are Manage Invites and Cancel (spec surface 1, step 4); without it, OK only, for use
 * inside Manage Invites itself. [onCancel] runs when the message is dismissed without a button.
 */
internal fun showTooManyInviteLinks(
    resources: ResourceHelper,
    onManageInvites: (() -> Unit)? = null,
    onCancel: () -> Unit = {},
) = BottomBarManager.showAlert(
    title = resources.getString(R.string.title_tooManyInviteLinks),
    message = resources.getString(R.string.description_tooManyInviteLinks),
    actions = if (onManageInvites != null) {
        listOf(BottomBarAction(text = resources.getString(R.string.action_manageInvites), onClick = onManageInvites))
    } else {
        listOf(BottomBarAction.Ok)
    },
    showCancel = onManageInvites != null,
    onDismiss = { fromAction -> if (!fromAction) onCancel() },
)

/** The generic "Couldn't Create Link". [onDismiss] runs once it is closed, however. */
internal fun showInviteLinkNotCreated(resources: ResourceHelper, onDismiss: () -> Unit = {}) =
    BottomBarManager.showError(
        title = resources.getString(R.string.title_inviteLinkNotCreated),
        message = resources.getString(R.string.description_inviteLinkNotCreated),
        onDismiss = { onDismiss() },
    )

/** "Couldn't Load Invite Links". [onDismiss] runs once it is closed, however. */
internal fun showInviteLinksNotLoaded(resources: ResourceHelper, onDismiss: () -> Unit = {}) =
    BottomBarManager.showError(
        title = resources.getString(R.string.title_inviteLinksNotLoaded),
        message = resources.getString(R.string.description_inviteLinkNotCreated),
        onDismiss = { onDismiss() },
    )

/**
 * Frame 1e: invite links are not available to this host yet; [onShareJoinLink], when given, offers
 * the plain link. [onCancel] runs when the message is dismissed without taking the offer.
 */
internal fun showInviteLinksNotAvailable(
    resources: ResourceHelper,
    onShareJoinLink: (() -> Unit)? = null,
    onCancel: () -> Unit = {},
) = BottomBarManager.showMessage(
    title = resources.getString(R.string.title_inviteLinksNotAvailable),
    message = resources.getString(R.string.description_inviteLinksNotAvailable),
    actions = if (onShareJoinLink != null) {
        listOf(
            BottomBarAction(
                text = resources.getString(R.string.action_shareGroupLink),
                onClick = onShareJoinLink,
            ),
        )
    } else {
        listOf(BottomBarAction.Ok)
    },
    showCancel = onShareJoinLink != null,
    onDismiss = { if (it.index == -1) onCancel() },
)

/** Frame 2c: revoking cannot be undone. */
internal fun showRevokeInviteLink(resources: ResourceHelper, onRevoke: () -> Unit) = BottomBarManager.showAlert(
    title = resources.getString(R.string.title_revokeInviteLink),
    message = resources.getString(R.string.description_revokeInviteLink),
    actions = listOf(
        BottomBarAction(text = resources.getString(R.string.action_revokeLink), onClick = onRevoke),
    ),
    showCancel = true,
)
