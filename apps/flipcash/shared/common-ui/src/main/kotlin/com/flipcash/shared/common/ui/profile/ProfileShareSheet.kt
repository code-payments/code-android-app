package com.flipcash.shared.common.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.common.ui.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow

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
 * The content of a profile's share sheet: [title] over [subtitle] (who is being shared), then one
 * row per entry in [rows].
 *
 * It only draws and reports the tap. Hosting it in a sheet, dismissing that sheet, and running the
 * chosen action once the sheet has finished leaving are the caller's.
 */
@Composable
fun ProfileShareSheet(
    title: String,
    subtitle: String,
    rows: List<ProfileShareRow>,
    onRow: (ProfileShareRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        AppBarWithTitle(
            title = title,
            titleAlignment = Alignment.CenterHorizontally,
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            text = subtitle,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(vertical = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            rows.forEach { row ->
                ChoiceRow(
                    label = row.label,
                    icon = row.icon,
                    onClick = { onRow(row) },
                )
            }
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileShareSheet() {
    ProfileShareSheet(
        title = "Share User Profile",
        subtitle = "Ada Lovelace · @ada",
        rows = listOf(
            ProfileShareRow("share", R.drawable.ic_share_os, "Share Profile"),
            ProfileShareRow("card", R.drawable.ic_file_download, "Show Profile Card"),
            ProfileShareRow("copy", R.drawable.ic_copy, "Copy Link"),
        ),
        onRow = {},
    )
}
