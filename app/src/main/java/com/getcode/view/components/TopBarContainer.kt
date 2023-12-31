package com.getcode.view.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.CodeAppState
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.manager.TopBarManager.TopBarMessageType.*
import com.getcode.theme.*
import java.util.*
import kotlin.concurrent.timerTask

@Composable
fun TopBarContainer(appState: CodeAppState) {
    val topBarMessage by appState.topBarMessage.observeAsState()
    val topBarVisibleState = remember { MutableTransitionState(false) }
    var topBarMessageDismissId by remember { mutableStateOf(0L) }

    if (!topBarVisibleState.targetState && !topBarVisibleState.currentState) {
        Timer().schedule(timerTask {
            topBarVisibleState.targetState = topBarMessage != null
        }, 50)

        if (topBarMessageDismissId == topBarMessage?.id) {
            TopBarManager.setMessageShown(topBarMessage?.id ?: 0)
            topBarMessageDismissId = 0
        }
        if (topBarMessage == null) {
            topBarVisibleState.targetState = false
        }
    }

    if (topBarMessage == null) return

    if (topBarVisibleState.targetState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black40)
                .clickable(indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {}
        )
    }

    AnimatedVisibility(
        visibleState = topBarVisibleState,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
    ) {
        TopBarView(topBarMessage = topBarMessage) {
            topBarMessageDismissId = topBarMessage?.id ?: 0
            topBarVisibleState.targetState = false
        }
    }
}

@Composable
private fun TopBarView(
    topBarMessage: TopBarManager.TopBarMessage?,
    onClose: () -> Unit
) {
    topBarMessage ?: return
        Column(
            modifier = Modifier
                .background(
                    when (topBarMessage.type) {
                        ERROR_NETWORK, ERROR -> TopError
                        WARNING -> topWarning
                        NOTIFICATION -> topInfo
                        NEUTRAL -> topNeutral
                    }
                )
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .padding(horizontal = 18.dp)
            ) {
                Image(
                    painterResource(
                        when (topBarMessage.type) {
                            ERROR_NETWORK -> R.drawable.ic_wifi_slash
                            ERROR -> R.drawable.ic_x_octagon_fill
                            WARNING -> R.drawable.ic_exclamation_octagon_fill
                            NOTIFICATION -> R.drawable.ic_exclamation_octagon_fill
                            NEUTRAL -> R.drawable.ic_exclamation_octagon_fill
                        }
                    ),
                    contentDescription = "",
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .padding(end = 8.dp)
                        .size(17.dp)
                )
                Text(
                    text = topBarMessage.title,
                    style = MaterialTheme.typography.body1.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier
                        .align(CenterVertically)

                )
            }
            Text(
                modifier = Modifier
                    .padding(horizontal = 18.dp),
                text = topBarMessage.message,
                style = MaterialTheme.typography.body1.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp
                )
            )
            Spacer(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Black10)
            )
            Row {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    onClick = { topBarMessage.primaryAction(); onClose() },
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Transparent)
                ) {
                    Text(text = topBarMessage.primaryText ?: stringResource(R.string.action_ok))
                }
                if (topBarMessage.secondaryText != null) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        onClick = { topBarMessage.secondaryAction(); onClose() },
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Transparent)
                    ) {
                        Text(text = topBarMessage.secondaryText.orEmpty())
                    }
                }
            }
        }
}