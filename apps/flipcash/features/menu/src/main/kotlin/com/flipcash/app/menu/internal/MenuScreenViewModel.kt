package com.flipcash.app.menu.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.bills.share.TipCodePreviewCache
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.menu.internal.components.UsernameProgress
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.menu.R
import com.flipcash.services.user.AuthState
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.tipping.TippingCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.util.Locale
import javax.inject.Inject

/**
 * The minimum-balance gate as the copy quotes it — `$100 USD` rather than `$100.00`. Shared by the
 * card and the sheet so a round threshold never renders two different ways.
 */
private fun Fiat.formattedGate(): String =
    formatted(rule = Fiat.FormattingRule.Truncated, suffix = currencyCode.name)

@HiltViewModel
internal class MenuScreenViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    totalBalance: TotalBalanceProvider,
    analytics: FlipcashAnalytics,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodePreviewCache: TipCodePreviewCache,
    tipPayments: TipPaymentDelegate,
    private val resources: ResourceHelper,
) :
    BaseViewModel<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent,
        defaultDispatcher = dispatchers.Default,
    ) {
    data class State(
        // What the header draws: nothing yet, the claim prompt, or the viewer's own profile.
        val profileState: ProfileState = ProfileState.Unknown,
        // The nudge toward claiming a `@handle`, or null when there is nothing to nudge about — a
        // handle already exists, or the account state hasn't resolved yet.
        val usernameProgress: UsernameProgress? = null,
        // The gate, formatted (e.g. `$100 USD`). Carried next to [usernameProgress] because both the
        // card's locked subtitle and the sheet behind its tap quote it.
        val usernameMinimumBalance: String = "",
        // What another user must send to open a chat with the viewer, formatted. Null until it
        // resolves, which the stats card draws as a dash.
        val minimumToChat: String? = null,
    )

    /**
     * What the "You" tab has to draw at the top of the page.
     *
     * The cases are deliberately distinct: a missing profile used to mean both "we haven't resolved
     * it yet" and "this account has no display name", and the header drew nothing for either —
     * leaving a nameless account with no prompt and no way to claim a name from this tab.
     */
    sealed interface ProfileState {
        /** Still resolving (or signed out). Draw nothing rather than guessing. */
        data object Unknown : ProfileState

        /**
         * The account has no display name, so it has no profile worth showing yet. [placeholder] is
         * a real scannable stand-in drawn blurred behind the claim prompt; it is never shareable.
         */
        data class Unclaimed(val placeholder: Scannable.TipCard?) : ProfileState

        /** The viewer's own profile, with [joined] already formatted for the stats card. */
        data class Named(val profile: UserProfile, val joined: String?) : ProfileState
    }

    sealed interface Event {
        /**
         * Add money, tagged with what prompted it. The default covers the menu's own row; the
         * username gate passes its own source so a shortfall-driven deposit isn't reported as a
         * deliberate visit to Add Money.
         */
        data class PresentDepositOptions(
            val source: AddMoneySource = AddMoneySource.MENU,
        ) : Event
        data class OpenScreen(val screen: AppRoute) : Event

        data class OnProfileStateChanged(val profileState: ProfileState) : Event
        data class OnUsernameProgressChanged(
            val progress: UsernameProgress?,
            val minimumBalance: String,
        ) : Event
        data class OnMinimumToChatChanged(val minimumToChat: String?) : Event

        /** The progress card's tap — claim a handle, or explain why it can't be claimed yet. */
        data object ClaimUsername : Event
        /** The claim prompt's CTA — collect a display name so the account gets a real card. */
        data object ClaimTipCard : Event

        /** The header's share button — opens the share sheet, but only for a profile that has a name. */
        data object ShareProfile : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull { event ->
                analytics.track(AddMoneyEvents.opened(event.source))
                purchaseMethodController.presentDepositOptions(popToRoot = true)
            }.onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // Rebuild what the header draws whenever the viewer's profile becomes available/changes.
        // Warm the Sharesheet preview eagerly so it's ready by the time the user picks "Share
        // Profile" — but only for a profile that can be shared.
        //
        // Gated on Ready: a named account restores its cached profile before auth completes, so
        // waiting here means it never flashes the claim prompt on the way in.
        userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile }
            .distinctUntilChanged()
            .onEach { profile ->
                if (profile == null || profile.displayName.isEmpty()) {
                    // No name means nothing to show yet — the tab prompts to claim one instead.
                    // Built locally, so an account whose profile the server has never seen still
                    // gets it.
                    dispatchEvent(
                        Event.OnProfileStateChanged(
                            ProfileState.Unclaimed(tippingCoordinator.unclaimedTipCard())
                        )
                    )
                } else {
                    dispatchEvent(
                        Event.OnProfileStateChanged(
                            ProfileState.Named(profile, joinedLabel(profile.joinedAt, Locale.getDefault()))
                        )
                    )
                    tippingCoordinator.resolveTipCard().onSuccess { card ->
                        tippingCoordinator.currentUserId?.let { tipCodePreviewCache.prepare(it, card) }
                    }
                }
            }
            .launchIn(viewModelScope)

        // The fee is a Flow because it moves with the exchange rate and the region minimum, and it
        // follows the profile because the viewer's own fee setting is part of the answer.
        userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile }
            .distinctUntilChanged()
            .flatMapLatest { tipPayments.startChattingFee(it) }
            .map { it?.formatted() }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnMinimumToChatChanged(it)) }
            .launchIn(viewModelScope)

        // The username nudge. Gated on Ready for the same reason as the tip card: a named account
        // restores its cached profile before auth completes, so the card would otherwise flash for
        // someone who already holds a handle.
        combine(
            userManager.state
                .filter { it.authState is AuthState.Ready }
                .map { it.userProfile?.username to (it.userProfile?.isUsernameAutoAssigned == true) },
            userFlags.resolvedFlags.map { it.usernameMinBalance.effectiveValue },
            totalBalance.observeTotalBalance(),
        ) { (username, isAutoAssigned), minimum, balance ->
            val progress = when (val gate = usernameGate(username, isAutoAssigned, minimum, balance)) {
                UsernameGate.Claimed -> null
                UsernameGate.Unlocked -> UsernameProgress.Unlocked
                is UsernameGate.Locked -> UsernameProgress.Locked(
                    fraction = gate.fraction,
                    remaining = gate.shortfall.formattedGate(),
                )
            }
            Event.OnUsernameProgressChanged(progress, minimum.formattedGate())
        }
            .distinctUntilChanged()
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ClaimUsername>()
            .onEach {
                when (stateFlow.value.usernameProgress) {
                    UsernameProgress.Unlocked -> dispatchEvent(
                        Event.OpenScreen(
                            AppRoute.UpdateUserProfile(
                                origin = AppRoute.Tabs.Menu,
                                steps = listOf(UpdateProfileStep.Username),
                            )
                        )
                    )

                    // Below the minimum the tap states the rule instead of walking into a rejection
                    // on submit. Same strings as the server's refusal, and the same informational
                    // style the entry screen gives it, so the two can't disagree.
                    is UsernameProgress.Locked -> BottomBarManager.showInfo(
                        title = resources.getString(
                            R.string.error_title_usernameMinimumBalance,
                            stateFlow.value.usernameMinimumBalance,
                        ),
                        message = resources.getString(
                            R.string.error_description_usernameMinimumBalance,
                            stateFlow.value.usernameMinimumBalance,
                        ),
                        actions = listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_addMoney),
                                onClick = {
                                    dispatchEvent(
                                        Event.PresentDepositOptions(
                                            AddMoneySource.USERNAME_SHORTFALL
                                        )
                                    )
                                },
                            ),
                            BottomBarAction(
                                text = resources.getString(R.string.action_dismiss),
                                style = BottomBarManager.BottomBarButtonStyle.Text,
                            ),
                        ),
                    )

                    null -> Unit
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ClaimTipCard>()
            .onEach {
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.UpdateUserProfile(
                            origin = AppRoute.Tabs.Menu,
                            // A name is all a tip card needs.
                            steps = listOf(UpdateProfileStep.Name(DisplayNameSource.TipCardSetup)),
                        )
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareProfile>()
            .mapNotNull { shareProfileRoute(stateFlow.value.profileState) }
            .onEach { dispatchEvent(Event.OpenScreen(it)) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        internal val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProfileStateChanged -> { state ->
                    state.copy(profileState = event.profileState)
                }

                is Event.OnUsernameProgressChanged -> { state ->
                    state.copy(
                        usernameProgress = event.progress,
                        usernameMinimumBalance = event.minimumBalance,
                    )
                }

                is Event.OnMinimumToChatChanged -> { state ->
                    state.copy(minimumToChat = event.minimumToChat)
                }

                is Event.PresentDepositOptions,
                Event.ClaimTipCard,
                Event.ClaimUsername,
                Event.ShareProfile,
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}

/**
 * Where the header's share button goes: the share sheet, once the viewer has a profile worth
 * sharing. An account with no name has no card or link to hand out, so the tap leads nowhere.
 */
internal fun shareProfileRoute(profileState: MenuScreenViewModel.ProfileState): AppRoute? =
    if (profileState is MenuScreenViewModel.ProfileState.Named) AppRoute.Menu.ShareProfile else null
