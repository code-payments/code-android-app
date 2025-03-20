package com.getcode.ui.components.emojis

import android.os.Parcelable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.libs.emojis.generated.Emojis
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.R
import com.getcode.ui.components.TextInput
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.emojis.EmojiGarden
import com.getcode.ui.emojis.EmojiRender
import com.getcode.ui.emojis.EmojiSearchResults
import com.getcode.ui.emojis.fuzzySearch
import com.getcode.ui.emojis.processEmoji
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
                .statusBarsPadding()
                .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val textState = rememberTextFieldState()
                SearchBar(textState)

                val emojis = remember { Emojis.categorizedNoSkinTones }
                val allEmojis = remember(emojis) {
                    emojis.mapValues { it.value.values.toList().flatten() }.values.toList().flatten()
                }

                val searchResults by remember(allEmojis, textState.text) {
                    derivedStateOf {
                        if (textState.text.isEmpty()) return@derivedStateOf null
                        allEmojis.fuzzySearch(textState.text.toString())
                    }
                }

                Crossfade(searchResults != null) { searching ->
                    if (searching) {
                        EmojiSearchResults(searchResults) { onSelected(it) }
                    } else {
                        EmojiGarden(onClick = onSelected)
                    }
                }
            }
        }
    }
}
@Composable
private fun SearchBar(
    state: TextFieldState,
) {
    TextInput(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CodeTheme.dimens.grid.x2)
            .padding(horizontal = CodeTheme.dimens.inset),
        shape = CodeTheme.shapes.medium,
        state = state,
        placeholder = stringResource(R.string.action_search),
        minHeight = CodeTheme.dimens.grid.x8,
        contentPadding = PaddingValues(
            start = 8.dp + CodeTheme.dimens.staticGrid.x2,
            top = 8.dp,
            end = 8.dp + CodeTheme.dimens.staticGrid.x2,
            bottom = 8.dp
        ),
        maxLines = 1,
        colors = inputColors(
            backgroundColor = Color.Transparent,
            textColor = CodeTheme.colors.textMain,
            cursorColor = Color.White,
            placeholderColor = CodeTheme.colors.textSecondary
        ),
        trailingIcon = state.text.takeIf { it.isNotBlank() }?.let {
            {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "",
                    tint = CodeTheme.colors.textSecondary,
                    modifier = Modifier
                        .padding(end = CodeTheme.dimens.grid.x2)
                        .wrapContentWidth()
                        .size(20.dp)
                        .unboundedClickable { state.clearText() }
                )
            }
        }
    )
}