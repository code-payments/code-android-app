package com.getcode.navigation.extensions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.getcode.ui.utils.getActivity

@Composable
inline fun <reified T : ViewModel> getActivityScopedViewModel(): T {
    val activity = LocalContext.current.getActivity() as ComponentActivity
    val viewModelStore = activity.viewModelStore
    val defaultFactory = activity as HasDefaultViewModelProviderFactory
    return remember(key1 = T::class) {
        val provider = ViewModelProvider(
            store = viewModelStore,
            factory = defaultFactory.defaultViewModelProviderFactory,
            defaultCreationExtras = defaultFactory.defaultViewModelCreationExtras
        )
        provider[T::class.java]
    }
}

/**
 * Get a ViewModel scoped to a key (for multi-step flows sharing state).
 * Falls back to activity-scoped hiltViewModel when no key is provided.
 */
@Deprecated(
    "Use flowScopedViewModel(key) for flow-shared VMs or hiltViewModel() for entry-scoped VMs",
    ReplaceWith("flowScopedViewModel(key)", "com.getcode.navigation.extensions.flowScopedViewModel")
)
@Composable
inline fun <reified T : ViewModel> getStackScopedViewModel(key: String? = null): T {
    return if (key != null) {
        val activity = LocalContext.current.getActivity() as ComponentActivity
        remember(key1 = key) {
            val factory = activity.defaultViewModelProviderFactory
            val extras = activity.defaultViewModelCreationExtras
            val provider = ViewModelProvider(activity.viewModelStore, factory, extras)
            provider[key, T::class.java]
        }
    } else {
        hiltViewModel<T>()
    }
}

/**
 * Get a Hilt ViewModel shared across multiple screens in a multi-step flow.
 * The ViewModel is scoped to the activity's ViewModelStore with the given [key],
 * so all screens using the same key get the same instance.
 *
 * @param key A unique key identifying the flow instance (typically a UUID generated at flow start).
 */
@Composable
inline fun <reified T : ViewModel> flowScopedViewModel(key: String): T {
    val activity = LocalContext.current.getActivity() as ComponentActivity
    return hiltViewModel<T>(viewModelStoreOwner = activity, key = key)
}
