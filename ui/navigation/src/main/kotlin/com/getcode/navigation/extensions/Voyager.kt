package com.getcode.navigation.extensions

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.VoyagerHiltViewModelFactories
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.getActivity

@Composable
inline fun <reified T: ViewModel> getActivityScopedViewModel(): T {
    val activity = LocalContext.current.getActivity() as ComponentActivity
    val defaultFactory = (LocalLifecycleOwner.current as HasDefaultViewModelProviderFactory)
    val viewModelStore = LocalContext.current.getActivity()!!.viewModelStore
    return remember(key1 = T::class) {
        val factory = VoyagerHiltViewModelFactories.getVoyagerFactory(
            activity = activity,
            delegateFactory = defaultFactory.defaultViewModelProviderFactory
        )

        val provider = ViewModelProvider(
            store = viewModelStore,
            factory = factory,
            defaultCreationExtras = defaultFactory.defaultViewModelCreationExtras
        )
        provider[T::class.java]
    }
}

@Composable
inline fun <reified T: ViewModel> Screen.getStackScopedViewModel(key: String? = null): T {
    val _key = key ?: LocalCodeNavigator.current.sheetStackRoot?.key
    val activity = LocalContext.current.getActivity() as ComponentActivity
    val viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner

    return remember(key1 = _key) {
        val factory = viewModelStoreOwner.createVoyagerFactory(activity, null)
        viewModelStoreOwner.get(T::class.java, _key, factory)
    }
}

@PublishedApi
internal fun ViewModelStoreOwner.createVoyagerFactory(
    context: Context,
    viewModelProviderFactory: ViewModelProvider.Factory? = null
): ViewModelProvider.Factory? {
    val factory = viewModelProviderFactory
        ?: (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
    return if (factory != null) {
        VoyagerHiltViewModelFactories.getVoyagerFactory(
            activity = context.getActivity() as ComponentActivity,
            delegateFactory = factory
        )
    } else {
        null
    }
}

@PublishedApi
internal fun <T : ViewModel> ViewModelStoreOwner.get(
    javaClass: Class<T>,
    key: String?,
    viewModelProviderFactory: ViewModelProvider.Factory? = null,
    creationExtras: CreationExtras = if (this is HasDefaultViewModelProviderFactory) {
        this.defaultViewModelCreationExtras
    } else {
        CreationExtras.Empty
    }
): T {
    val factory = viewModelProviderFactory
        ?: (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
    val provider = if (factory != null) {
        ViewModelProvider(viewModelStore, factory, creationExtras)
    } else {
        ViewModelProvider(this)
    }
    return if (key != null) {
        provider[key, javaClass]
    } else {
        provider[javaClass]
    }
}
