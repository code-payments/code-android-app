package com.flipcash.app.theme

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import com.getcode.animation.LocalSharedTransitionScope
import com.flipcash.app.theme.internal.Flipcash2DesignSystem
import com.flipcash.app.theme.internal.FlipcashLegacyDesignSystem
import com.getcode.theme.CodeTheme

@Deprecated("Use FlipcashThemeWrapper")
@Composable
fun FlipcashPreview(
    showBackground: Boolean = false,
    useLegacyColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val previewContent = @Composable {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .background(if (showBackground) CodeTheme.colors.background else Color.Transparent)
                ) {
                    content()
                }
            }
        }
    }

    if (useLegacyColors) {
        FlipcashLegacyDesignSystem(previewContent)
    } else {
        Flipcash2DesignSystem(previewContent)
    }
}

class FlipcashThemeWrapper: PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable (() -> Unit)) {
        val previewContent = @Composable {
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize()
                            .background(CodeTheme.colors.background)
                    ) {
                        content()
                    }
                }
            }
        }

        Flipcash2DesignSystem(previewContent)
    }
}

@Preview(name = "Samsung Galaxy Note 20", widthDp = 320, device = "id:Samsung Galaxy Note 20")
@Preview(name = "Pixel 3", widthDp = 360, device = "id:pixel_3")
@Preview(name = "Solana Seeker", widthDp = 415)
@Preview(name = "Pixel 9a", widthDp = 412, device = "id:pixel_9a")
@Preview(name = "Pixel 9 Pro XL", widthDp = 480, device = "id:pixel_9_pro_xl")
@Preview(name = "Pixel 9 Pro Fold (closed)", widthDp = 443)
annotation class MultiDevicePreview