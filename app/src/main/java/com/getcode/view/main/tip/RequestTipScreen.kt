package com.getcode.view.main.tip

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.HomeResult
import com.getcode.theme.Alert
import com.getcode.theme.Brand
import com.getcode.theme.BrandSubtle
import com.getcode.theme.CodeTheme
import com.getcode.theme.green
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Row
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun RequestTipScreen(
    viewModel: TipConnectViewModel = hiltViewModel()
) {
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TipConnectViewModel.Event.OpenX>()
            .onEach {
                composeTweet(context, it.intent)
                delay(1_000)
                navigator.hide()
            }
            .launchIn(this)
    }

    Column(
        Modifier
            .padding(CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        if (state.connected) {
            SuccessContent(state = state) {
                navigator.hideWithResult(HomeResult.ShowTipCard)
            }
        } else {
            RequestContent(state = state) {
                viewModel.dispatchEvent(TipConnectViewModel.Event.PostToX)
            }
        }
    }
}

@Composable
private fun ColumnScope.RequestContent(state: TipConnectViewModel.State, onClick: () -> Unit) {
    Text(
        text = stringResource(id = R.string.title_requestTip),
        style = CodeTheme.typography.h1
    )
    Text(
        text = stringResource(id = R.string.subtitle_requestTip),
        style = CodeTheme.typography.body2
    )
    Spacer(modifier = Modifier.weight(0.3f))
    TweetPreview(modifier = Modifier.fillMaxWidth(), xMessage = state.xMessage)
    Spacer(modifier = Modifier.weight(0.7f))
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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

@Composable
private fun ColumnScope.SuccessContent(state: TipConnectViewModel.State, onClick: () -> Unit) {
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = stringResource(id = R.string.title_success),
        style = CodeTheme.typography.h1,
    )
    Spacer(modifier = Modifier.weight(1f))
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(CodeTheme.dimens.grid.x16)
            .border(width = CodeTheme.dimens.thickBorder, color = green, shape = CircleShape)
            .padding(CodeTheme.dimens.grid.x3),
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.size(CodeTheme.dimens.grid.x6),
            painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(id = R.string.subtitle_xAccountConnected, state.username),
        style = CodeTheme.typography.body2,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(CodeTheme.dimens.grid.x2))
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        buttonState = ButtonState.Filled,
        text = stringResource(R.string.action_shareTipCard),
    )
}

@Composable
private fun TweetPreview(
    modifier: Modifier = Modifier,
    xMessage: String
) {
    Row(
        modifier = Modifier
            .background(BrandSubtle)
            .padding(CodeTheme.dimens.inset)
            .then(modifier),
    ) {
        Image(
            modifier = Modifier
                .size(CodeTheme.dimens.grid.x5)
                .clip(CircleShape),
            painter = painterResource(id = R.drawable.ic_placeholder_user),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(CodeTheme.dimens.grid.x3))
        Text(
            modifier = Modifier.weight(1f),
            text = xMessage,
            color = Color.White,
            style = CodeTheme.typography.body2
        )
    }
}

private fun composeTweet(context: Context, intent: Intent) {
    context.startActivity(intent)
}

@Preview
@Composable
private fun Preview_TweetPreview() {
    CodeTheme {
        TweetPreview(xMessage = "I’m connecting my X account with @getcode so I can receive tips from people all over the world. My accountForX=fh5g376")
    }
}