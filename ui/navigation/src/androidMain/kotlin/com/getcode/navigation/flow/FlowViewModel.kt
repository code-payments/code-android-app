package com.getcode.navigation.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner

/**
 * A [ViewModelStoreOwner] scoped to the enclosing [FlowHost]'s outer entry. Provided by
 * [FlowHost] and consumed by [flowSharedViewModel] so that a single [ViewModel] instance can be
 * shared across the inner steps of a flow without being recreated between steps.
 *
 * Because the owner is the outer flow entry's own store, the shared VM is automatically cleared
 * when the flow entry is popped from the outer back stack — no UUID keys or manual reset.
 */
val LocalFlowViewModelStoreOwner =
    staticCompositionLocalOf<ViewModelStoreOwner> { error("No flow store owner") }

/**
 * Resolve a Hilt [ViewModel] against the enclosing flow's [ViewModelStoreOwner], giving all
 * steps in the flow access to the same instance.
 */
@Composable
inline fun <reified T : ViewModel> flowSharedViewModel(key: String? = null): T =
    hiltViewModel(viewModelStoreOwner = LocalFlowViewModelStoreOwner.current, key = key)
