package com.flipcash.app.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.verticalScrollStateGradient

@Composable
fun <T> MenuList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    items: List<MenuItem<T>>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onItemClick: (MenuItem<T>) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = state,
                isLongGradient = true,
            ),
        state = state,
        contentPadding = contentPadding,
    ) {
        items(items, key = { it.id }, contentType = { it }) { item ->
            ListItem(modifier = Modifier.animateItem(), item = item) {
                onItemClick(item)
            }
        }
    }
}

@Composable
private fun <T> ListItem(
    modifier: Modifier = Modifier,
    item: MenuItem<T>,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .rememberedClickable { onClick() }
            .padding(CodeTheme.dimens.grid.x5)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(end = CodeTheme.dimens.inset)
                .height(CodeTheme.dimens.staticGrid.x5)
                .width(CodeTheme.dimens.staticGrid.x5),
            painter = item.icon,
            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
            contentDescription = ""
        )

        Text(
            modifier = Modifier.align(CenterVertically),
            text = item.name,
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
        )

        Spacer(Modifier.weight(1f))

        if (item.isStaffOnly) {
            BetaIndicator()
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = CodeTheme.colors.divider,
        thickness = 0.5.dp
    )
}