package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.getcode.R
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.formatTimeRelatively
import kotlinx.datetime.Instant

@Composable
internal fun EncryptedContent(modifier: Modifier = Modifier, date: Instant) {
    Column(
        modifier = modifier
            // add top padding to accommodate ascents
            .padding(top = CodeTheme.dimens.grid.x1),
    ) {
        Image(
            modifier = Modifier
                .padding(CodeTheme.dimens.grid.x2)
                .size(CodeTheme.dimens.staticGrid.x6)
                .align(Alignment.CenterHorizontally),
            painter = painterResource(id = R.drawable.lock_app_dashed),
            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
            contentDescription = null
        )
        Text(
            modifier = Modifier.align(Alignment.End),
            text = date.formatTimeRelatively(),
            style = CodeTheme.typography.caption,
            color = BrandLight,
        )
    }
}