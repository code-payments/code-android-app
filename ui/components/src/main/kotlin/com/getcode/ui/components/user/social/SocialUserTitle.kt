package com.getcode.ui.components.user.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import com.getcode.model.social.user.SocialProfile
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun SocialUserTitle(
    profile: SocialProfile,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x1,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (profile) {
            is SocialProfile.Unknown -> Unit
            is SocialProfile.X -> {
                Image(
                    painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
                    contentDescription = null
                )
            }
        }

        when (profile) {
            is SocialProfile.Unknown -> Unit
            is SocialProfile.X -> {
                Text(
                    text = profile.friendlyName,
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain
                )
            }
        }

        when (profile) {
            is SocialProfile.Unknown -> Unit
            is SocialProfile.X -> {
                profile.verificationType.let { type ->
                    type.checkmark()?.let { asset ->
                        Image(
                            painter = rememberVectorPainter(image = asset),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialProfile.X.VerificationType.checkmark(): ImageVector? {
    return when (this) {
        SocialProfile.X.VerificationType.BLUE -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge)
        SocialProfile.X.VerificationType.BUSINESS -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gold)
        SocialProfile.X.VerificationType.GOVERNMENT -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gray)
        else -> null
    }
}