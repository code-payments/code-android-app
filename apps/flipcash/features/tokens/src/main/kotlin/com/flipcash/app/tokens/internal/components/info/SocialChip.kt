package com.flipcash.app.tokens.internal.components.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.theme.extraSmall
import androidx.compose.foundation.clickable

@Composable
internal fun SocialChip(
    socialLink: SocialLink,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = modifier
            .background(CodeTheme.colors.surfaceVariant, CodeTheme.shapes.extraSmall)
            .clip(CodeTheme.shapes.extraSmall)
            .clickable {
                uriHandler.openUri(socialLink.uri)
            }.padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(
                when (socialLink) {
                    is SocialLink.Website -> R.drawable.ic_social_web
                    is SocialLink.X -> R.drawable.ic_social_x
                    is SocialLink.Discord -> R.drawable.ic_social_discord
                    is SocialLink.Telegram -> R.drawable.ic_social_tg
                }
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary)
        )

        Text(
            text = when (socialLink) {
                is SocialLink.Website -> stringResource(R.string.label_social_website)
                is SocialLink.X -> stringResource(R.string.label_social_x, socialLink.username)
                is SocialLink.Discord -> stringResource(R.string.label_social_discord)
                is SocialLink.Telegram -> stringResource(R.string.label_social_tg)
            },
            color = CodeTheme.colors.textMain,
            style = CodeTheme.typography.textMedium
        )
    }
}