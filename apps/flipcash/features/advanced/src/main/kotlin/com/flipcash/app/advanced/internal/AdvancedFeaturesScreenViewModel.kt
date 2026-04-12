package com.flipcash.app.advanced.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private val FullMenuList = buildList {
    add(CurrencyCreator)
    add(Deposit)
    add(DeviceLogs)
}

@HiltViewModel
internal class AdvancedFeaturesScreenViewModel @Inject constructor(
    featureFlagController: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<AdvancedFeaturesScreenViewModel.State, AdvancedFeaturesScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val isBetaEnabled: Boolean = false,
        val items: List<MenuItem<Event>> = FullMenuList
    )

    sealed interface Event {
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data class OnCurrencyCreatorEnabled(val enabled: Boolean) : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        featureFlagController.observe(FeatureFlag.BillCustomizer)
            .onEach {
                dispatchEvent(Event.OnCurrencyCreatorEnabled(it))
            }.launchIn(viewModelScope)

        combine(
            featureFlagController.observeOverride(),
            userFlags.resolvedFlags.map { it.isStaff.effectiveValue }
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
                    state.copy(
                        isBetaEnabled = event.unlocked,
                    )
                }

                is Event.OnCurrencyCreatorEnabled -> { state ->
                    state.copy(
                        items = if (event.enabled) {
                            FullMenuList
                        } else {
                            FullMenuList.filterNot { it is CurrencyCreator }
                        }
                    )
                }

                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}