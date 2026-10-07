package com.flipcash.app.myaccount.internal.editprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.ProfileCover
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.ListItemDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.offset
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// Carried from the iOS screen.
private val CoverHeight = 126.dp
private val AvatarSize = 68.dp
private val BadgeSize = 28.dp
private val BadgeIconSize = 16.dp
private val BadgeOffsetX = 6.dp
private val BadgeOffsetY = 2.dp

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
            state = state,
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

/** The cover card with the avatar hanging off its bottom edge, half over it. */
@Composable
private fun CoverAndPhoto(
    state: EditProfileViewModel.State,
    onChangeCover: () -> Unit,
    onChangePhoto: () -> Unit,
) {
    val grid = CodeTheme.dimens.staticGrid
    // The cover is what the Change cover chip frosts, as iOS's CoverChip is glass over it.
    val hazeState = rememberHazeState()
    Box(modifier = Modifier.fillMaxWidth().height(CoverHeight + AvatarSize / 2)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CoverHeight)
                .clip(CodeTheme.shapes.medium)
                .clickable(onClick = onChangeCover),
        ) {
            ProfileCover(
                image = state.cover,
                access = BlobAccessContext.Owned,
                height = Dp.Unspecified,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            )
            Text(
                text = stringResource(R.string.action_changeCover),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(grid.x2)
                    .floatingGlass(hazeState)
                    .padding(horizontal = grid.x2, vertical = grid.x1),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = grid.x3)
                .clickable(onClick = onChangePhoto),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(grid.x2),
        ) {
            Box {
                ContactAvatar(
                    image = state.avatar,
                    displayName = state.displayName,
                    access = BlobAccessContext.Owned,
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .border(grid.x1, CodeTheme.colors.background, CircleShape),
                )
                CameraBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = BadgeOffsetX, y = BadgeOffsetY),
                )
            }
            Text(
                text = stringResource(R.string.action_changePhoto),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = grid.x1),
            )
        }
    }
}

@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BadgeSize)
            .clip(CircleShape)
            // Opaque, so the avatar doesn't show through the tinted fill.
            .background(CodeTheme.colors.background)
            .background(White05),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(BadgeIconSize),
        )
    }
}

/** A tappable card: the field's title over its current value, with the disclosure chevron. */
@Composable
private fun FieldCard(
    title: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueIsPrompt: Boolean = false,
    maxLines: Int = 1,
) {
    val grid = CodeTheme.dimens.staticGrid
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.medium)
            .background(White05)
            .clickable(onClick = onClick)
            .padding(horizontal = grid.x3, vertical = grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(grid.x2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = value ?: placeholder,
                style = CodeTheme.typography.textMedium,
                color = if (value == null || valueIsPrompt) {
                    CodeTheme.colors.textSecondary
                } else {
                    CodeTheme.colors.textMain
                },
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ListItemDefaults.Chevron()
    }
}
