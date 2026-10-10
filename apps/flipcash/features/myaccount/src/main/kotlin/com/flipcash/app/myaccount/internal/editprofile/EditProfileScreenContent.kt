package com.flipcash.app.myaccount.internal.editprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.shared.common.ui.profile.CoverAndPhoto
import com.flipcash.shared.common.ui.profile.FieldCard
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.theme.CodeTheme

@Composable
internal fun EditProfileScreenContent(
    state: EditProfileViewModel.State,
    dispatch: (EditProfileViewModel.Event) -> Unit,
) {
    val grid = CodeTheme.dimens.staticGrid
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = grid.x5),
    ) {
        val openStep = { step: UpdateProfileStep -> dispatch(EditProfileViewModel.Event.OpenStep(step)) }

        CoverAndPhoto(
            cover = state.cover,
            avatar = state.avatar,
            displayName = state.displayName,
            access = BlobAccessContext.Owned,
            changeCoverLabel = stringResource(R.string.action_changeCover),
            changePhotoLabel = stringResource(R.string.action_changePhoto),
            onChangeCover = { openStep(UpdateProfileStep.Cover) },
            onChangePhoto = { openStep(UpdateProfileStep.Photo) },
        )

        Column(
            modifier = Modifier.padding(top = grid.x4),
            verticalArrangement = Arrangement.spacedBy(grid.x2),
        ) {
            FieldCard(
                title = stringResource(R.string.label_editProfileName),
                value = state.displayName.ifEmpty { null },
                placeholder = stringResource(R.string.placeholder_editProfileName),
                onClick = { openStep(EditProfileViewModel.nameStep) },
            )
            if (state.usernameNeedsClaim) {
                FieldCard(
                    title = stringResource(R.string.title_username),
                    value = stringResource(R.string.action_claimUsername),
                    placeholder = "",
                    valueIsPrompt = true,
                    onClick = { dispatch(EditProfileViewModel.Event.UsernameClicked) },
                )
            } else {
                FieldCard(
                    title = stringResource(R.string.title_username),
                    value = "@${state.username.orEmpty()}",
                    placeholder = "",
                    onClick = { openStep(UpdateProfileStep.Username) },
                )
            }
            FieldCard(
                title = stringResource(R.string.title_bio),
                value = state.bio.ifEmpty { null },
                placeholder = stringResource(R.string.placeholder_bio),
                maxLines = 3,
                onClick = { openStep(UpdateProfileStep.Bio) },
            )
            FieldCard(
                title = stringResource(R.string.title_minimumToChat),
                value = state.minimumToChat,
                placeholder = stringResource(R.string.placeholder_minimumToChat),
                onClick = { openStep(UpdateProfileStep.MinimumTip) },
            )
            FieldCard(
                title = stringResource(R.string.title_featuredGroups),
                value = state.featuredGroupCount.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.value_featuredGroupCount, it, it.toString())
                },
                placeholder = stringResource(R.string.placeholder_featuredGroups),
                onClick = { openStep(UpdateProfileStep.FeaturedGroups) },
            )
        }
    }
}
