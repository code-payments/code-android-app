package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.ui.utils.RepeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

interface NamedScreen {

    val name: String?
        @Composable get() = null

    val hasName: Boolean
        @Composable get() = !name.isNullOrEmpty()
}

abstract class AppScreen: Screen {
    var result =  MutableStateFlow<Any?>(null)

    fun <T> onResult(obj: T) {
        Timber.d("onResult=$obj")
        result.value = obj
    }
}

@Composable
fun AppScreen.OnScreenResult(block: suspend (Any) -> Unit) {
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


interface ModalContent
interface ModalRoot : ModalContent