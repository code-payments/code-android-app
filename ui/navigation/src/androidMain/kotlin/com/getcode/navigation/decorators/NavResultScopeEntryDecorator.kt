package com.getcode.navigation.decorators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.NavContentKey
import com.getcode.navigation.results.LocalNavKey
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.NavResultStore
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

/**
 * Responsible for removing any store associated with a route on cleanup
 */
@Suppress("FunctionName")
fun NavResultScopeEntryDecorator(
    backStack: NavBackStack<NavKey>,
    navResultStore: NavResultStore,
    resultStateRegistry: NavResultStateRegistry,
): NavEntryDecorator<NavKey> {
    val onPop: (Any) -> Unit = { contentKey ->
        require(contentKey is String)
        navResultStore.disposeOwner(NavContentKey(contentKey))
        resultStateRegistry.remove(NavContentKey(contentKey))
    }

    return NavEntryDecorator(onPop = onPop) { entry ->
        // Provide and cleanup a persisted local store for nav results not handled locally
        val persistedVm = hiltViewModel<NavEntrySavedStateHandleViewModel>()
        val key = NavContentKey(entry.contentKey as String)

        LaunchedEffect(key, persistedVm.handle) {
            Timber.d("ResultStateRegistry created for key=$key")
            resultStateRegistry[key] = persistedVm.handle
        }

        // Provide the local navkey which is needed widely for result handling
        CompositionLocalProvider(LocalNavKey provides backStack.last()) {
            entry.Content()
            // This calls any stored result callback handlers
            navResultStore.ResultCallbackDispatcherEffect(NavContentKey(entry.contentKey as String))
        }
    }
}

@Composable
fun rememberNavResultScopeEntryDecorator(
    backStack: NavBackStack<NavKey>,
    navResultStore: NavResultStore,
    resultStateRegistry: NavResultStateRegistry,
) = remember { NavResultScopeEntryDecorator(backStack, navResultStore, resultStateRegistry) }

@HiltViewModel
internal class NavEntrySavedStateHandleViewModel @Inject constructor(
    val handle: SavedStateHandle,
) : ViewModel()
