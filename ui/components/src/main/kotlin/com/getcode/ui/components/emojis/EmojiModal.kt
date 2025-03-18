package com.getcode.ui.components.emojis

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.theme.CodeTheme
import com.getcode.ui.emojis.EmojiGarden
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class EmojiModal(private val onSelected: (String) -> Unit) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                EmojiGarden(onClick = onSelected)
            }
        }
    }
}