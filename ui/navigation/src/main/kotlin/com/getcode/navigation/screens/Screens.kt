package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.RepeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

abstract class ReturnResultScreen: Screen {
    var result =  MutableStateFlow<Any?>(null)

    abstract val testTag: String

    fun <T> onResult(obj: T) {
        Timber.d("onResult=$obj")
        result.value = obj
    }
}

@Composable
fun ReturnResultScreen.OnScreenResult(block: suspend (Any) -> Unit) {
    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
    ) {
        result
            .filterNotNull()
            .onEach { runCatching { block(it) } }
            .onEach { result.value = null }
            .launchIn(this)
    }
}

interface AppScreen: Screen {
    val testTag: String

    @Composable
    fun ScreenContent()

    @Composable
    override fun Content() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            ScreenContent()
        }
    }
}


interface ModalContent

sealed interface ModalHeightMetric {
    data class Weight(val weight: Float) : ModalHeightMetric
    data object WrapContent : ModalHeightMetric
}

interface ModalScreen : AppScreen, ModalContent {

    val modalHeight: ModalHeightMetric
        @Composable get() = ModalHeightMetric.Weight(CodeTheme.dimens.modalHeightRatio)

    @Composable
    fun ModalContent()

    @Composable
    override fun ScreenContent() = ModalContent()

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .then(
                    modalHeight.let { mh ->
                        when (mh) {
                            is ModalHeightMetric.Weight -> Modifier.fillMaxHeight(mh.weight)
                            ModalHeightMetric.WrapContent -> Modifier.wrapContentHeight()
                        }
                    }
                )
        ) {
            ScreenContent()
        }
    }
}