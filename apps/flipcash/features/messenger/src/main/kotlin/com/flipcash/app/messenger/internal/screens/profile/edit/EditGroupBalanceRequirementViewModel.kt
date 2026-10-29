package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.listenerMinimum
import com.flipcash.shared.chat.minimumBalanceIn
import com.flipcash.shared.chat.restatedIn
import com.flipcash.shared.chat.speakerMinimum
import com.flipcash.shared.chat.unmetRequirements
import com.flipcash.shared.chat.withMinimum
import com.flipcash.shared.chat.withoutSpeakerMinimum
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
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
 * Entry is in the preferred currency and stored in the listener rule's currency, which the
 * speaker rule must share; a speaker rule also takes the listener's mints. Saving sends the whole
 * rule set back through `EditChatRequest.rules` with only this minimum changed, so staff, creator
 * and any other rule the group has go back as they came.
 *
 * What the server would refuse is stopped here first where it can be known: an amount below the
 * currency's minimum transfer value or equal to the current one leaves Save disabled, and a Chat
 * minimum not above Join, a Join minimum not below Chat, or new minimums the caller's own balance
 * does not meet each raise an alert instead of a request.
 */
@HiltViewModel
class EditGroupBalanceRequirementViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    private val exchange: Exchange,
    private val chatCoordinator: ChatCoordinator,
    tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<EditGroupBalanceRequirementViewModel.State, EditGroupBalanceRequirementViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val chatId: ChatId? = null,
        val role: GroupBalanceRole = GroupBalanceRole.Join,
        /** The group's rules as the editor opened them; the baseline every save is built from. */
        val rules: ChatRules? = null,
        /** The preferred rate the keypad enters in; null until it resolves. */
        val rate: Rate? = null,
        val rates: Map<CurrencyCode, Rate> = emptyMap(),
        /** The caller's balances, or null until they load; the self-check is skipped without them. */
        val balances: List<TokenWithBalance>? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        /** The rule being replaced, or null when the group has none for [role]. */
        val current: ChatRuleRequirement.MinimumBalance?
            get() = when (role) {
                GroupBalanceRole.Join -> rules?.listenerMinimum
                GroupBalanceRole.Chat -> rules?.speakerMinimum
            }

        /** Whether the Chat minimum can go back to "None": only one the group has can be removed. */
        val canRemove: Boolean
            get() = role == GroupBalanceRole.Chat && rules?.speakerMinimum != null
    }

    sealed interface Event {
        /** The screen handing over the group, the rule being edited, and the group's rules. */
        data class Initialize(
            val chatId: ChatId,
            val role: GroupBalanceRole,
            val rules: ChatRules?,
        ) : Event

        data class RateChanged(val rate: Rate) : Event
        data class RatesChanged(val rates: Map<CurrencyCode, Rate>) : Event
        data class BalancesChanged(val balances: List<TokenWithBalance>) : Event

        /** Save, pressed. Checks the entry and proposes the change; [Submit] is dispatched by the prompt. */
        data object SaveClicked : Event

        /** Remove, pressed on the Chat minimum. [Submit] is dispatched by the prompt. */
        data object RemoveClicked : Event

        /** The confirmed rule set. */
        data class Submit(val rules: ChatRules) : Event

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
            )
        ),
        loadingState = stateFlow.map { it.processingState }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingSuccessState()),
        confirmEnabled = canSave,
    )

    // What the seed typed, or null before it runs. `prefill` types on top of the entry, so it
    // runs once; and the restated rule can land a unit off the stored one, so the seeded entry
    // itself is what reads as unchanged.
    private var seededEntry: Double? = null

    init {
        exchange.observePreferredRate()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)?.let(amountDelegate::onCurrencyChanged)
                dispatchEvent(Event.RateChanged(rate))
                seedIfReady()
            }.launchIn(viewModelScope)

        exchange.observeRates()
            .onEach { rates ->
                dispatchEvent(Event.RatesChanged(rates))
                seedIfReady()
            }.launchIn(viewModelScope)

        tokenCoordinator.tokenBalances
            .onEach { dispatchEvent(Event.BalancesChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Initialize>()
            .onEach { seedIfReady() }
            .launchIn(viewModelScope)

        combine(
            amountDelegate.state.map { it.enteredAmount }.distinctUntilChanged(),
            stateFlow,
        ) { entered, state ->
            state.processingState.isIdle && state.chatId != null && proposedAmount(entered, state) != null
        }.onEach { canSave.value = it }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SaveClicked>()
            .onEach {
                val state = stateFlow.value
                if (!state.processingState.isIdle) return@onEach
                val rules = state.rules ?: return@onEach
                val amount = proposedAmount(amountDelegate.state.value.enteredAmount, state) ?: return@onEach
                when (val check = check(state, rules, amount)) {
                    is Check.Blocked -> BottomBarManager.showError(
                        title = resources.getString(check.title),
                        message = check.message,
                    )

                    is Check.Ready -> confirm(state.role, check.rules)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RemoveClicked>()
            .onEach {
                val state = stateFlow.value
                if (!state.processingState.isIdle || !state.canRemove) return@onEach
                val rules = state.rules ?: return@onEach
                confirm(GroupBalanceRole.Chat, rules.withoutSpeakerMinimum())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Submit>()
            .onEach { event ->
                val state = stateFlow.value
                val chatId = state.chatId ?: return@onEach
                if (!state.processingState.isIdle) return@onEach

                dispatchEvent(Event.UpdateProcessingState(loading = true))
                // The coordinator stores the metadata the response carries, so every screen
                // reading the chat moves to the new rules without waiting for RulesChanged.
                chatCoordinator.editChat(chatId, EditChatParameters(rules = event.rules))
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
     * The amount [entered] would store, in the listener rule's currency, or null when there is
     * nothing to save: no listener rule or usable rate, an entry below the currency's minimum
     * transfer value, or one that leaves the current rule as it is.
     */
    private fun proposedAmount(entered: Double, state: State): Fiat? {
        val listener = state.rules?.listenerMinimum ?: return null
        // `Rate.ignore` is in USD, so an unresolved rate would read the entry as dollars.
        val rate = state.rate?.takeIf { it.isUsable() } ?: return null
        if (seededEntry != null && entered == seededEntry) return null
        val target = listener.amount.currencyCode
        val amount = minimumBalanceIn(entered, rate, target, state.rates[target]) ?: return null
        val current = state.current?.amount?.rounded(target.fractionDigits)
        return amount.takeUnless { it.quarks == current?.quarks }
    }

    private sealed interface Check {
        data class Blocked(val title: Int, val message: String) : Check
        data class Ready(val rules: ChatRules) : Check
    }

    /**
     * Whether the server would take [amount] for the state's role, checked where the answer is
     * already known, and the rule set to send when it would.
     */
    private fun check(state: State, rules: ChatRules, amount: Fiat): Check {
        val listener = rules.listenerMinimum ?: return Check.Blocked(
            R.string.error_title_groupRequirementInvalid,
            resources.getString(R.string.error_description_groupRequirementInvalid),
        )
        val step = amount.smallestUnit.quarks
        val joinAmount = listener.amount.rounded(listener.amount.currencyCode.fractionDigits)
        when (state.role) {
            // A speaker minimum has to clear the listener's by at least the minimum transfer value.
            GroupBalanceRole.Chat -> if (amount.quarks < joinAmount.quarks + step) {
                return Check.Blocked(
                    R.string.error_title_groupChatMinimumTooLow,
                    resources.getString(
                        R.string.error_description_groupChatMinimumTooLow,
                        displayed(joinAmount, state).formatted(),
                    ),
                )
            }

            GroupBalanceRole.Join -> {
                val speaker = rules.speakerMinimum?.amount?.rounded(listener.amount.currencyCode.fractionDigits)
                if (speaker != null && amount.quarks + step > speaker.quarks) {
                    return Check.Blocked(
                        R.string.error_title_groupRequirementInvalid,
                        resources.getString(R.string.error_description_groupRequirementInvalid),
                    )
                }
            }
        }

        val updated = rules.withMinimum(state.role, amount)
        if (!callerMeets(updated, state)) {
            return Check.Blocked(
                R.string.error_title_groupRequirementNotSatisfied,
                resources.getString(R.string.error_description_groupRequirementNotSatisfied),
            )
        }
        return Check.Ready(updated)
    }

    /**
     * Whether the caller's own balance meets every minimum in [rules], listener and speaker alike,
     * which the server requires of whoever sets them. Only the minimums are weighed here: the
     * rest of the set is unchanged and stays the server's to judge. With no balances yet, or no
     * rate for the rule's currency, it reads as met and the server decides.
     */
    private fun callerMeets(rules: ChatRules, state: State): Boolean {
        val balances = state.balances ?: return true
        val minimums = (rules.listener + rules.speaker).filterIsInstance<ChatRuleRequirement.MinimumBalance>()
        return unmetRequirements(
            rules = ChatRules(listener = minimums, speaker = emptyList()),
            balances = balances,
            isStaff = false,
            rates = state.rates,
        ).isEmpty()
    }

    /** [amount] restated in the entry currency, or as it is when no rate can restate it. */
    private fun displayed(amount: Fiat, state: State): Fiat {
        val rate = state.rate ?: return amount
        return amount.restatedIn(rate.currency, state.rates[amount.currencyCode], rate)
            ?.rounded(rate.currency.fractionDigits)
            ?: amount
    }

    private fun confirm(role: GroupBalanceRole, rules: ChatRules) {
        val copy = promptCopy(role)
        BottomBarManager.showAlert(
            title = resources.getString(copy.title),
            message = resources.getString(copy.message),
            actions = listOf(
                BottomBarAction(resources.getString(copy.action)) {
                    viewModelScope.launch {
                        // The bar dismisses on an animation; the spinner belongs to the
                        // screen behind it and would otherwise start underneath.
                        delay(150.milliseconds)
                        dispatchEvent(Event.Submit(rules))
                    }
                }
            ),
            showCancel = true,
        )
    }

    /**
     * Types the current rule into the keypad, restated in the entry currency, once the rule and
     * the rates to restate it are known. A group with no rule for the role leaves the keypad empty.
     */
    private fun seedIfReady() {
        if (seededEntry != null) return
        val state = stateFlow.value
        if (state.chatId == null) return
        val rate = state.rate?.takeIf { it.isUsable() } ?: return
        val current = state.current
        if (current == null) {
            seededEntry = 0.0
            return
        }
        val inEntryCurrency = current.amount
            .restatedIn(rate.currency, state.rates[current.amount.currencyCode], rate)
            ?.rounded(rate.currency.fractionDigits)
            ?: return
        // An untouched keypad reads "0" rather than empty; anything else was typed by the user.
        if (amountDelegate.state.value.enteredAmount != 0.0) {
            seededEntry = Double.NaN
            return
        }
        if (inEntryCurrency.decimalValue > 0.0) amountDelegate.prefill(inEntryCurrency.decimalValue)
        seededEntry = amountDelegate.state.value.enteredAmount
    }

    private fun announceFailure(cause: Throwable) {
        when (cause) {
            is EditChatError.RulesNotSatisfied -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_groupRequirementNotSatisfied),
                message = resources.getString(R.string.error_description_groupRequirementNotSatisfied),
            )

            is EditChatError.InvalidRules -> {
                reportError(cause)
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_groupRequirementInvalid),
                    message = resources.getString(R.string.error_description_groupRequirementInvalid),
                )
            }

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
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Initialize -> { state ->
                    state.copy(chatId = event.chatId, role = event.role, rules = event.rules)
                }

                is Event.RateChanged -> { state -> state.copy(rate = event.rate) }
                is Event.RatesChanged -> { state -> state.copy(rates = event.rates) }
                is Event.BalancesChanged -> { state -> state.copy(balances = event.balances) }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = LoadingSuccessState(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                Event.SaveClicked -> { state -> state }
                Event.RemoveClicked -> { state -> state }
                is Event.Submit -> { state -> state }
                Event.OnRequirementAccepted -> { state -> state }
            }
        }
    }
}
