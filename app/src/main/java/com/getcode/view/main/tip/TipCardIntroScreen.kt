package com.getcode.view.main.tip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.HomeResult
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton

@Composable
fun TipCardIntroScreen() {
    val navigator = LocalCodeNavigator.current

    Column(
        Modifier
            .padding(CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        Text(
            text = stringResource(id = R.string.title_requestTip),
            style = CodeTheme.typography.h1
        )
        Text(
            text = stringResource(id = R.string.subtitle_requestTip),
            style = CodeTheme.typography.body2
        )
        Spacer(modifier = Modifier.weight(1f))
        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // TODO: Connect with X
                navigator.hideWithResult(HomeResult.ShowTipCard)
            },
            buttonState = ButtonState.Filled,
            content = {
                Image(
                    painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
                    colorFilter = ColorFilter.tint(Brand),
                    contentDescription = null
                )
                Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
                Text(
                    text = stringResource(R.string.action_connectXAccount),
                )
            }
        )
    }
}