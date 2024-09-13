package com.getcode.ui.components

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
import com.getcode.R
import com.getcode.model.TwitterUser
import com.getcode.theme.CodeTheme

@Composable
fun TwitterUsernameDisplay(
    modifier: Modifier = Modifier,
    username: String,
    verificationStatus: TwitterUser.VerificationStatus? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x2,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
            contentDescription = null
        )
        Text(text = username, style = CodeTheme.typography.textLarge, color = CodeTheme.colors.textMain)
        verificationStatus?.let { status ->
            status.checkmark()?.let { asset ->
                Image(
                    painter = rememberVectorPainter(image = asset),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun TwitterUser.VerificationStatus.checkmark(): ImageVector? {
    return when (this) {
        TwitterUser.VerificationStatus.blue -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge)
        TwitterUser.VerificationStatus.business -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gold)
        TwitterUser.VerificationStatus.government -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gray)
        else -> null
    }
}