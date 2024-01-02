package com.getcode.util

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.VoyagerHiltViewModelFactories

@Composable
inline fun <reified T: ViewModel> Screen.getActivityScopedViewModel(): T {
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