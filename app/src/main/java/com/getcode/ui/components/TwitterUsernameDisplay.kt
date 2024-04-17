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
        Text(text = username, style = CodeTheme.typography.subtitle1)
        verificationStatus?.let { status ->
            if (status.hasCheckmark()) {
                Image(
                    painter = rememberVectorPainter(image = status.checkmark()),
                    contentDescription = null
                )
            }
        }
    }
}

fun TwitterUser.VerificationStatus.hasCheckmark(): Boolean = this == TwitterUser.VerificationStatus.blue || this == TwitterUser.VerificationStatus.government

@Composable
fun TwitterUser.VerificationStatus.checkmark(): ImageVector {
    return when (this) {
        TwitterUser.VerificationStatus.blue -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge)
        TwitterUser.VerificationStatus.government -> ImageVector.vectorResource(id = R.drawable.ic_twitter_verified_badge_gray)
        else -> throw NotImplementedError()
    }
}