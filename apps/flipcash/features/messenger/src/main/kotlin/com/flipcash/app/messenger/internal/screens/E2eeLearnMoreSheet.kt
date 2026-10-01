package com.flipcash.app.messenger.internal.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.BulletRow

/** Which chat kind the learn-more sheet explains. */
internal enum class E2eeSheetKind { Dm, Group }

/**
 * What end-to-end encryption does and doesn't cover, opened from the profile footer. One layout
 * for both kinds, with the DM's and the group's own words (nodes 10416:1533, 10557:1412).
 */
@Composable
internal fun E2eeLearnMoreSheet(
    kind: E2eeSheetKind,
    onDismiss: () -> Unit,
) {
    val isDm = kind == E2eeSheetKind.Dm
    val encrypted = if (isDm) {
        listOf(R.string.item_e2eeSheet_allMessageText, R.string.item_e2eeSheet_allMedia)
    } else {
        listOf(R.string.item_e2eeSheet_messagesAndMediaInDms)
    }
    val notEncrypted = if (isDm) {
        listOf(
            R.string.item_e2eeSheet_payments,
            R.string.item_e2eeSheet_reactions,
            R.string.item_e2eeSheet_publicGroupChats,
            R.string.item_e2eeSheet_beforeEncryption,
        )
    } else {
        listOf(
            R.string.item_e2eeSheet_publicGroupChatsIncludingThis,
            R.string.item_e2eeSheet_payments,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(70.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                imageVector = if (isDm) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
            )
        }

        Text(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = 20.dp),
            text = stringResource(
                if (isDm) R.string.title_e2eeSheet_dm else R.string.title_e2eeSheet_group
            ),
            style = CodeTheme.typography.textLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp),
            text = stringResource(
                if (isDm) R.string.subtitle_e2eeSheet_dm else R.string.subtitle_e2eeSheet_group
            ),
            style = CodeTheme.typography.textSmall.copy(
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetSection(
                header = R.string.header_e2eeSheet_encrypted,
                items = encrypted,
                icon = R.drawable.ic_e2ee_included,
            )
            SheetSection(
                header = R.string.header_e2eeSheet_notEncrypted,
                items = notEncrypted,
                icon = R.drawable.ic_e2ee_excluded,
            )
        }
    }
}

@Composable
private fun SheetSection(
    @StringRes header: Int,
    items: List<Int>,
    @DrawableRes icon: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(header),
            style = CodeTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.44.sp,
            ),
            color = Color.White.copy(alpha = 0.45f),
        )
        for (item in items) {
            BulletRow(
                painter = painterResource(icon),
                text = stringResource(item),
                textStyle = CodeTheme.typography.textSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                textColor = Color.White.copy(alpha = 0.9f),
                // The exported glyphs carry their own colours.
                iconTint = Color.Unspecified,
                spacing = 12.dp,
            )
        }
    }
}

@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_E2eeSheet_Dm() {
    E2eeLearnMoreSheet(kind = E2eeSheetKind.Dm, onDismiss = {})
}

@Preview(heightDp = 817)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_E2eeSheet_Group() {
    E2eeLearnMoreSheet(kind = E2eeSheetKind.Group, onDismiss = {})
}
