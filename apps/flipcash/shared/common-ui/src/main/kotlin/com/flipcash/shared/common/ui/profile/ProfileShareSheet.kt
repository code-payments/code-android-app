package com.flipcash.shared.common.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.common.ui.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * One choice in a [ProfileShareSheet]. The caller decides which rows exist, so a person's profile
 * and a group's can offer different ones; [id] is how the caller recognises the row that comes
 * back through `onRow`.
 */
data class ProfileShareRow(
    val id: String,
    @DrawableRes val icon: Int,
    val label: String,
)

/**
 * The content of the You tab's share sheet: [title] left-aligned, one row per entry in [rows], and
 * a subtle Dismiss.
 *
 * It only draws and reports the tap. Hosting it in a sheet, dismissing that sheet, and running the
 * chosen action once the sheet has finished leaving are the caller's. Other people's profiles and
 * groups share through the share-to-chats sheet instead.
 */
@Composable
fun ProfileShareSheet(
    title: String,
    rows: List<ProfileShareRow>,
    onRow: (ProfileShareRow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The Add Money sheet's colour, as iOS draws this sheet.
            .background(CodeTheme.colors.bannerThemed)
            .navigationBarsPadding(),
    ) {
        AppBarWithTitle(title = title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            rows.forEach { row ->
                ChoiceRow(
                    label = row.label,
                    icon = painterResource(row.icon),
                    onClick = { onRow(row) },
                )
            }
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                buttonState = ButtonState.Subtle,
                text = stringResource(R.string.action_dismiss),
                onClick = onDismiss,
            )
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileShareSheet() {
    ProfileShareSheet(
        title = "Share User Profile",
        rows = listOf(
            ProfileShareRow("share", R.drawable.ic_share_os, "Share Profile"),
            ProfileShareRow("card", R.drawable.ic_file_download, "Show Profile Card"),
        ),
        onRow = {},
        onDismiss = {},
    )
}
