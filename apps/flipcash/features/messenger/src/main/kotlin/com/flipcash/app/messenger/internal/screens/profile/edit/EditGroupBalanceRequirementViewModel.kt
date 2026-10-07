package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.SetGroupMinimumBalanceError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.minimumBalanceFor
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import com.getcode.view.SuccessHoldDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One of a group's minimum balances, behind a row of Edit Group's Balance Requirements card.
 *
 * Entry is in the preferred currency and stored in USD, through the same [minimumBalanceFor] group
 * creation uses, so the same keypad entry writes the same rule from either screen. The rule's
 * mints carry over from the requirement being replaced; a group with none gets an empty list.
 *
 * The write goes through [ChatCoordinator.setMinimumBalance], which fails with
 * [SetGroupMinimumBalanceError.Unavailable] until the contract can change a group's rules. That
 * failure is expected on every save, so it is announced but not reported.
 */
@HiltViewModel
class EditGroupBalanceRequirementViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    private val exchange: Exchange,
    private val chatCoordinator: ChatCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<EditGroupBalanceRequirementViewModel.State, EditGroupBalanceRequirementViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val chatId: ChatId? = null,
        val role: GroupBalanceRole = GroupBalanceRole.Join,
        /** The rule being replaced, or null when the group has none for [role]. */
        val current: ChatRuleRequirement.MinimumBalance? = null,
        /** The preferred rate the keypad enters in; null until it resolves. */
        val rate: Rate? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        /** The screen handing over the group, the rule being edited, and its current value. */
        data class Initialize(
            val chatId: ChatId,
            val role: GroupBalanceRole,
            val current: ChatRuleRequirement.MinimumBalance?,
        ) : Event

        data class RateChanged(val rate: Rate) : Event

        /** Save, pressed. Only proposes the change; [Submit] is dispatched by the prompt. */
        data object SaveClicked : Event

        /** The confirmed change. */
        data class Submit(val requirement: ChatRuleRequirement.MinimumBalance) : Event

        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The change was stored; the screen can leave. */
        data object OnRequirementAccepted : Event
    }

    /** Hilt cannot inject a defaulted lambda, so tests swap this to observe what was reported. */
    @VisibleForTesting
    internal var reportError: (Throwable) -> Unit = ErrorUtils::handleError

    /** Whether Save is live; the keypad's confirm gate. Exposed for tests. */
    @VisibleForTesting
    internal val canSave = MutableStateFlow(false)

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = MutableStateFlow(
            AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_save)),
                actionStyle = ConfirmationStyle.Button,
                canChangeCurrency = false,
            )
        ),
        loadingState = stateFlow.map { it.processingState }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingSuccessState()),
        confirmEnabled = canSave,
    )

    // Seeds once. `prefill` types on top of the entry, so a second pass would append to whatever
    // the user has typed since.
    private var seeded = false

    init {
        exchange.observePreferredRate()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)?.let(amountDelegate::onCurrencyChanged)
                dispatchEvent(Event.RateChanged(rate))
                seedIfReady()
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Initialize>()
            .onEach { seedIfReady() }
            .launchIn(viewModelScope)

        combine(
            amountDelegate.state.map { it.enteredAmount }.distinctUntilChanged(),
            stateFlow,
        ) { entered, state ->
            state.processingState.isIdle && state.chatId != null &&
                requirementFor(entered, state.rate, state.current, usdOf(state.current)) != null
        }.onEach { canSave.value = it }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SaveClicked>()
            .onEach {
                val state = stateFlow.value
                if (!state.processingState.isIdle) return@onEach
                val requirement = requirementFor(
                    entered = amountDelegate.state.value.enteredAmount,
                    rate = state.rate,
                    current = state.current,
                    currentUsd = usdOf(state.current),
                ) ?: return@onEach

                val copy = promptCopy(state.role)
                BottomBarManager.showAlert(
                    title = resources.getString(copy.title),
                    message = resources.getString(copy.message),
                    actions = listOf(
                        BottomBarAction(resources.getString(copy.action)) {
                            viewModelScope.launch {
                                // The bar dismisses on an animation; the spinner belongs to the
                                // screen behind it and would otherwise start underneath.
                                delay(150.milliseconds)
                                dispatchEvent(Event.Submit(requirement))
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Submit>()
            .onEach { event ->
                val state = stateFlow.value
                val chatId = state.chatId ?: return@onEach
                if (!state.processingState.isIdle) return@onEach

                dispatchEvent(Event.UpdateProcessingState(loading = true))
                chatCoordinator.setMinimumBalance(chatId, state.role, event.requirement)
                    .onSuccess {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        viewModelScope.launch {
                            delay(SuccessHoldDuration)
                            dispatchEvent(Event.OnRequirementAccepted)
                            dispatchEvent(Event.UpdateProcessingState())
                        }
                    }
                    .onFailure { cause ->
                        dispatchEvent(Event.UpdateProcessingState())
                        announceFailure(cause)
                    }
            }.launchIn(viewModelScope)
    }

    /**
     * Types the current rule into the keypad, restated in the entry currency, once both the rule
     * and a usable rate are known. A group with no rule leaves the keypad empty.
     */
    private fun seedIfReady() {
        if (seeded) return
        val state = stateFlow.value
        if (state.chatId == null) return
        val rate = state.rate?.takeIf { it.isUsable() } ?: return
        seeded = true

        val currentUsd = usdOf(state.current) ?: return
        // An untouched keypad reads "0" rather than empty.
        if (amountDelegate.state.value.enteredAmount != 0.0) return
        val inEntryCurrency = currentUsd.convertingTo(rate).rounded(rate.currency.fractionDigits)
        if (inEntryCurrency.decimalValue > 0.0) amountDelegate.prefill(inEntryCurrency.decimalValue)
    }

    /**
     * [requirement]'s amount in USD. Rules are written in USD — group creation and this screen
     * both store them that way — but the proto carries a currency, so one that arrives in another
     * is restated rather than read as dollars.
     */
    private fun usdOf(requirement: ChatRuleRequirement.MinimumBalance?): Fiat? {
        val amount = requirement?.amount ?: return null
        if (amount.currencyCode == CurrencyCode.USD) return amount
        val rate = exchange.rateFor(amount.currencyCode)?.takeIf { it.isUsable() } ?: return null
        return amount.convertingToUsdIfNeeded(rate)
    }

    private fun announceFailure(cause: Throwable) {
        when (cause) {
            is SetGroupMinimumBalanceError.Unavailable -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_groupRequirementUnavailable),
                message = resources.getString(R.string.error_description_groupRequirementUnavailable),
            )

            else -> {
                reportError(cause)
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_groupRequirementFailed),
                    message = resources.getString(R.string.error_description_groupRequirementFailed),
                )
            }
        }
    }

    private data class PromptCopy(val title: Int, val message: Int, val action: Int)

    private fun promptCopy(role: GroupBalanceRole) = when (role) {
        GroupBalanceRole.Join -> PromptCopy(
            title = R.string.prompt_title_changeGroupJoinRequirement,
            message = R.string.prompt_description_changeGroupJoinRequirement,
            action = R.string.action_changeGroupJoinRequirement,
        )

        GroupBalanceRole.Chat -> PromptCopy(
            title = R.string.prompt_title_changeGroupChatRequirement,
            message = R.string.prompt_description_changeGroupChatRequirement,
            action = R.string.action_changeGroupChatRequirement,
        )
    }

    internal companion object {
        /**
         * The rule [entered] would store, or null when there is nothing to save: no usable rate,
         * an entry that rounds to nothing in USD, or one equal to [currentUsd] in cents. The mints
         * carry over from [current].
         */
        fun requirementFor(
            entered: Double,
            rate: Rate?,
            current: ChatRuleRequirement.MinimumBalance?,
            currentUsd: Fiat?,
        ): ChatRuleRequirement.MinimumBalance? {
            val usd = minimumBalanceFor(entered, rate ?: return null) ?: return null
            if (currentUsd?.rounded(CurrencyCode.USD.fractionDigits)?.decimalValue == usd.decimalValue) {
                return null
            }
            return ChatRuleRequirement.MinimumBalance(amount = usd, mints = current?.mints.orEmpty())
        }

        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Initialize -> { state ->
                    state.copy(chatId = event.chatId, role = event.role, current = event.current)
                }

                is Event.RateChanged -> { state -> state.copy(rate = event.rate) }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = LoadingSuccessState(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                Event.SaveClicked -> { state -> state }
                is Event.Submit -> { state -> state }
                Event.OnRequirementAccepted -> { state -> state }
            }
        }
    }
}
