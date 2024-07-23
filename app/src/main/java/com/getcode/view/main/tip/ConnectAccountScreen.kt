package com.getcode.view.main.tip

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.Brand
import com.getcode.theme.BrandSubtle
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.theme.extraSmall
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Row
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class IdentityConnectionReason {
    TipCard,
    IdentityReveal,
}

@Composable
fun ConnectAccountScreen(
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
                when (state.reason) {
                    IdentityConnectionReason.IdentityReveal -> navigator.pop()
                    else -> navigator.hide()
                }
            }
            .launchIn(this)
    }

    Column(
        Modifier
            .padding(CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        RequestContent(state = state) {
            viewModel.dispatchEvent(TipConnectViewModel.Event.PostToX)
        }
    }
}

@Composable
private fun ColumnScope.RequestContent(state: TipConnectViewModel.State, onClick: () -> Unit) {
    Text(
        text = when(state.reason) {
            IdentityConnectionReason.TipCard -> stringResource(id = R.string.title_requestTip)
            IdentityConnectionReason.IdentityReveal -> stringResource(id = R.string.title_connectAccount)
            null -> ""
        },
        style = CodeTheme.typography.displayMedium.bolded()
    )
    Text(
        text = when(state.reason) {
            IdentityConnectionReason.TipCard -> stringResource(id = R.string.subtitle_tipCardForX)
            IdentityConnectionReason.IdentityReveal -> stringResource(id = R.string.subtitle_connectXAccount)
            null -> ""
        },
        style = CodeTheme.typography.textSmall
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
                text = stringResource(R.string.action_postToConnect),
            )
        }
    )
}

@Composable
private fun TweetPreview(
    modifier: Modifier = Modifier,
    xMessage: String
) {
    Row(
        modifier = Modifier
            .background(BrandSubtle, shape = CodeTheme.shapes.extraSmall)
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
            style = CodeTheme.typography.textSmall
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
        TweetPreview(xMessage = "I’m connecting my X account with @getcode\n" +
                "\n" +
                "CodeAccount:349pQtzGmiBxU9vADVf6AUdMLLXyCCU3Zu4smrQPXved:zGmiBxU9vADVf6AUdMLLXyCCU3Zu4smrQP")
    }
}