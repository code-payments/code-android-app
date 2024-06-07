package dev.bmcreations.tipkit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.bmcreations.tipkit.data.InlineTipData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun InlineTip(
    modifier: Modifier = Modifier,
    tip: Tip,
    enter: EnterTransition = slideInVertically { -it },
    exit: ExitTransition = slideOutVertically { -it },
) {
    val tipScope = LocalTipScope.current

    var show by rememberSaveable(tip) {
        mutableStateOf(false)
    }

    val data by remember(tip) {
        derivedStateOf {
            InlineTipData(
                tip = tip,
                content = tipScope.buildInlineTip(tip) { show = false },
            )
        }
    }

    AnimatedContent(targetState = show, transitionSpec = { enter togetherWith exit }, label = "show/hide inline tip") {
        if (it) {
            Box(modifier, contentAlignment = Alignment.Center) {
                data.content()
            }
        }
    }

    val tipProvider = LocalTipProvider.current
    LaunchedEffect(tip) {
        tip.observe()
            .filterNot { tip.hasBeenSeen() }
            .map { tip.show() }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                tipProvider.show(data)
                show = true
            }
            .launchIn(this)

        tip.flowContinuation
            .map { tip.show() }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                tipProvider.show(data)
                show = true
            }
            .launchIn(this)
    }
}
