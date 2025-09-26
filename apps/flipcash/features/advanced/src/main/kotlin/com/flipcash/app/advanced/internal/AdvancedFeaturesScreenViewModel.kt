package com.flipcash.app.advanced.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val FullMenuList = buildList {
    add(Pools)
}

@HiltViewModel
internal class AdvancedFeaturesScreenViewModel @Inject constructor(
    userManager: UserManager,
    featureFlagController: FeatureFlagController,
): BaseViewModel2<AdvancedFeaturesScreenViewModel.State, AdvancedFeaturesScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
){
    data class State(
        val isBetaEnabled: Boolean = false,
        val items: List<MenuItem<Event>> = FullMenuList
    )

    sealed interface Event {
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        combine(
            featureFlagController.observeOverride(),
            userManager.state.map { it.flags?.isStaff == true }
        ) { override, isStaff ->
            override || isStaff
        }.map {
            dispatchEvent(Event.OnBetaFeaturesUnlocked(it))
        }.launchIn(viewModelScope)
    }

    internal companion object {
        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(isBetaEnabled = event.unlocked)
                }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}