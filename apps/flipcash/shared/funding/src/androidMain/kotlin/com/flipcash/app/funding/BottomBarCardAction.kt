package com.flipcash.app.funding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.getcode.manager.BottomBarAction
import com.getcode.theme.CodeTheme

/**
 * A card row for a [com.getcode.manager.BottomBarManager] sheet: title, subtitle and a trailing
 * icon. The Add Money method picker is built from these, and so is any sheet that offers a choice
 * of money actions the same way.
 */
fun bottomBarCardAction(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    tintIcon: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit,
): BottomBarAction = BottomBarAction(
    text = AnnotatedString(title),
    inlineContentMap = emptyMap(),
    testTag = testTag,
    onClick = onClick,
    content = {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = CodeTheme.typography.textMedium,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        if (tintIcon) {
            // Monochrome glyph (phantom, QR, reserves) — tint white and size square.
            Icon(
                modifier = Modifier.size(32.dp),
                painter = painterResource(iconRes),
                tint = Color.White,
                contentDescription = null,
            )
        } else {
            // Brand mark (Google Pay) — keep its own colours; constrain height, let width wrap.
            Image(
                modifier = Modifier.height(28.dp),
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        }
    },
)
