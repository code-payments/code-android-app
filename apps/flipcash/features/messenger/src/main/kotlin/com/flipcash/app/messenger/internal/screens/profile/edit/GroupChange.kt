package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.annotation.StringRes
import com.flipcash.features.messenger.R
import com.flipcash.services.models.EditChatError
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper

/**
 * What an edit changes, for the two things every edit says to the user: the prompt before it is
 * sent and the explanation when it fails. Each is visible to everyone in the group, so none of
 * them leaves the device on the strength of one tap.
 */
internal enum class GroupChangeField(
    @StringRes val promptTitle: Int,
    @StringRes val promptMessage: Int,
    @StringRes val action: Int,
    @StringRes val failedTitle: Int,
) {
    Name(
        R.string.prompt_title_changeGroupName,
        R.string.prompt_description_changeGroupName,
        R.string.action_changeGroupName,
        R.string.error_title_groupNameSaveFailed,
    ),
    Picture(
        R.string.prompt_title_changeGroupPicture,
        R.string.prompt_description_changeGroupPicture,
        R.string.action_changeGroupPicture,
        R.string.error_title_groupPhotoSaveFailed,
    ),
    Cover(
        R.string.prompt_title_changeGroupCover,
        R.string.prompt_description_changeGroupCover,
        R.string.action_changeGroupCover,
        R.string.error_title_groupPhotoSaveFailed,
    ),
    Description(
        R.string.prompt_title_changeGroupDescription,
        R.string.prompt_description_changeGroupDescription,
        R.string.action_changeGroupDescription,
        R.string.error_title_groupDescriptionSaveFailed,
    ),
}

/**
 * The prompt that stands between Save and the call: a destructive [GroupChangeField.action]
 * followed by Cancel. Only the action runs [onConfirm]; dismissing it sends nothing.
 */
internal fun showGroupChangeConfirmation(
    resources: ResourceHelper,
    field: GroupChangeField,
    onConfirm: () -> Unit,
) {
    BottomBarManager.showAlert(
        title = resources.getString(field.promptTitle),
        message = resources.getString(field.promptMessage),
        actions = listOf(BottomBarAction(resources.getString(field.action)) { onConfirm() }),
        showCancel = true,
    )
}

/** What a failed `EditChat` tells the user. [isError] picks the error styling over the alert one. */
internal data class GroupEditAlert(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val isError: Boolean = false,
)

/**
 * `EditChatResponse.Result`, less `OK`, as the user should hear it for an edit of [field].
 *
 * A moderated description is not here: the description screen shows it inline beside the field it
 * refers to, and reaches for this only for what it does not handle itself.
 */
internal fun groupEditAlert(cause: Throwable, field: GroupChangeField): GroupEditAlert = when (cause) {
    is EditChatError.TitleModerated -> GroupEditAlert(
        R.string.error_title_groupNameNotAllowed,
        R.string.error_description_groupNameNotAllowed,
    )

    is EditChatError.PictureBlobNotAccepted,
    is EditChatError.CoverPictureBlobNotAccepted -> GroupEditAlert(
        R.string.error_title_groupPhotoNotAllowed,
        R.string.error_description_groupPhotoNotAllowed,
    )

    is EditChatError.Denied -> GroupEditAlert(
        R.string.error_title_groupEditDenied,
        R.string.error_description_groupEditDenied,
    )

    is EditChatError.NotFound -> GroupEditAlert(
        R.string.error_title_groupEditNotFound,
        R.string.error_description_groupEditNotFound,
    )

    else -> GroupEditAlert(field.failedTitle, R.string.error_description_groupEditFailed, isError = true)
}

internal fun showGroupEditAlert(
    resources: ResourceHelper,
    alert: GroupEditAlert,
) {
    val title = resources.getString(alert.title)
    val message = resources.getString(alert.message)
    if (alert.isError) {
        BottomBarManager.showError(title = title, message = message)
    } else {
        BottomBarManager.showAlert(title = title, message = message)
    }
}
