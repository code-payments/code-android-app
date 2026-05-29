package com.flipcash.app.userflags.internal

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.userflags.Field
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.app.userflags.internal.components.FieldEditorSheet
import com.flipcash.features.userflags.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class UserFlagsViewModel @Inject constructor(
    flagsCoordinator: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
) : BaseViewModel<UserFlagsViewModel.State, UserFlagsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val flags: Loadable<ResolvedUserFlags> = Loadable.Loading(),
    ) {
        val readOnlyEntries: List<ReadOnlyEntry>
            get() = when (flags) {
                is Loadable.Loaded -> listOf(
                    ReadOnlyEntry(
                        R.string.label_flag_isStaff,
                        flags.data.isStaff.effectiveValue
                    ),
                    ReadOnlyEntry(
                        R.string.label_flag_isRegistered,
                        flags.data.isRegistered.effectiveValue
                    ),
                    ReadOnlyEntry(
                        R.string.label_flag_requiresPurchaseForAccount,
                        flags.data.requiresIapForRegistration.effectiveValue
                    ),
                    ReadOnlyEntry(
                        R.string.label_flag_enablePhoneNumberSend,
                        flags.data.enablePhoneNumberSend.effectiveValue
                    ),
                )

                else -> emptyList()
            }

        val editableEntries: List<EditableEntry<*>>
            get() = when (flags) {
                is Loadable.Loaded -> flags.data.editableEntries()
                else -> emptyList()
            }
    }

    internal sealed interface Event {
        data class FlagsUpdated(val flags: Loadable<ResolvedUserFlags>) : Event
        data class UpdateFlag<Stored, Domain>(
            val field: Field<Stored, Domain>,
            val value: Domain,
        ) : Event {
            fun applyTo(coordinator: UserFlagsCoordinator) {
                coordinator.set(field, value)
            }
        }

        data class ResetFlag<T>(val flag: EditableEntry<T>) : Event
        data object Reset : Event
    }

    init {
        flagsCoordinator.resolvedFlags
            .onEach { dispatchEvent(Event.FlagsUpdated(Loadable.Loaded(it))) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UpdateFlag<*, *>>()
            .onEach { event ->
                event.applyTo(flagsCoordinator)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ResetFlag<*>>()
            .onEach { flagsCoordinator.clear(it.flag.field) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Reset>()
            .onEach { flagsCoordinator.clearAll() }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.FlagsUpdated -> { state ->
                    state.copy(flags = event.flags)
                }

                is Event.UpdateFlag<*, *> -> { state -> state }
                is Event.ResetFlag<*> -> { state -> state }
                is Event.Reset -> { state -> state }
            }
        }
    }
}

internal data class ReadOnlyEntry(@get:StringRes val label: Int, val value: Boolean)

internal data class EditableEntry<Domain>(
    val field: Field<*, Domain>,
    val resolved: ResolvedFlag<Domain>,
) {
    fun formattedEffectiveValue(): String = field.format(resolved.effectiveValue)
    fun formattedServerValue(): String = field.format(resolved.serverValue)
    fun formattedEditValue(): String = field.editFormat(resolved.effectiveValue)

    @Composable
    fun SheetContent(
        onDismiss: () -> Unit,
        dispatch: (UserFlagsViewModel.Event) -> Unit,
    ) {
        FieldEditorSheet(this, onDismiss, dispatch)
    }
}

private fun ResolvedUserFlags.editableEntries(): List<EditableEntry<*>> = listOf(
    EditableEntry(Field.PreferredProvider, preferredOnRampProvider),
    EditableEntry(Field.SupportedProviders, supportedOnRampProviders),
    EditableEntry(Field.MinimumVersion, minimumVersion),
    EditableEntry(Field.BillExchangeDataTimeout, billExchangeDataTimeout),
    EditableEntry(Field.NewCurrencyPurchaseAmount, newCurrencyPurchaseAmount),
    EditableEntry(Field.NewCurrencyFeeAmount, newCurrencyFeeAmount),
    EditableEntry(Field.WithdrawalFeeAmount, withdrawalFeeAmount),
    EditableEntry(Field.PreferredUsdcOnRampLiquidityPool, usdcOnRampLiquidityPool),
    EditableEntry(Field.MinimumHolderAmountForLeaderboard, minimumHolderAmountForLeaderboard),
)