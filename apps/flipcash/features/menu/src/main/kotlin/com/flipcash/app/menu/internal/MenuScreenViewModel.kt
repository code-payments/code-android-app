package com.flipcash.app.menu.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.bills.share.TipCodePreviewCache
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.app.core.share.TipCodeExporter
import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.menu.internal.components.UsernameProgress
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.menu.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tipping.TippingCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
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
    private val toastController: SystemToastController,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    totalBalance: TotalBalanceProvider,
    analytics: FlipcashAnalytics,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodePreviewCache: TipCodePreviewCache,
    private val shareable: ShareSheetController,
    private val clipboardManager: ClipboardManager,
    private val tipCodeExporter: TipCodeExporter,
    private val resources: ResourceHelper,
) :
    BaseViewModel<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent,
        defaultDispatcher = dispatchers.Default,
    ) {
    data class State(
        // The viewer's own tip card, shown at the top of the v2 "You" tab.
        val tipCardState: TipCardState = TipCardState.Unknown,
        // The nudge toward claiming a `@handle`, or null when there is nothing to nudge about — a
        // handle already exists, or the account state hasn't resolved yet.
        val usernameProgress: UsernameProgress? = null,
        // The gate, formatted (e.g. `$100 USD`). Carried next to [usernameProgress] because both the
        // card's locked subtitle and the sheet behind its tap quote it.
        val usernameMinimumBalance: String = "",
        // The "Finish Your Profile" checklist, or null while the profile is unresolved. Only ever
        // drawn under a claimed card — see [ClaimedTipCard].
        val profileTutorial: List<TutorialItem.Profile>? = null,
    ) {
        /** The card to share, export or expand — only a claimed one qualifies. */
        val tipCard: Scannable.TipCard?
            get() = (tipCardState as? TipCardState.Claimed)?.card

        /** The shareable URL for [tipCard]. Displayed abbreviated; copied in full. */
        val tipLink: String?
            get() = (tipCardState as? TipCardState.Claimed)?.link
    }

    /**
     * What the "You" tab has to draw at the top of the page.
     *
     * The three cases are deliberately distinct: `tipCard == null` used to mean both "we haven't
     * resolved it yet" and "this account has no display name, so there is nothing to resolve", and
     * the header drew nothing for either — leaving a nameless account with no card, no prompt, and
     * no way to claim one from this tab.
     */
    sealed interface TipCardState {
        /** Still resolving (or signed out). Draw nothing rather than guessing. */
        data object Unknown : TipCardState

        /**
         * The account has no display name, so it has no card to show yet. [placeholder] is a real
         * scannable stand-in drawn blurred behind the claim prompt; it is never shareable.
         */
        data class Unclaimed(val placeholder: Scannable.TipCard?) : TipCardState

        data class Claimed(val card: Scannable.TipCard, val link: String?) : TipCardState
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

        data class OnTipCardStateChanged(val tipCardState: TipCardState) : Event
        data class OnUsernameProgressChanged(
            val progress: UsernameProgress?,
            val minimumBalance: String,
        ) : Event
        data class OnProfileTutorialChanged(val items: List<TutorialItem.Profile>?) : Event

        /** The checklist's photo row — opens the photo step of the profile flow on its own. */
        data object SetProfilePicture : Event

        /** The checklist's minimum-tip row — opens the amount entry for the DM-init fee. */
        data object SetMinimumTip : Event

        /** The progress card's tap — claim a handle, or explain why it can't be claimed yet. */
        data object ClaimUsername : Event
        /** The claim prompt's CTA — collect a display name so the account gets a real card. */
        data object ClaimTipCard : Event
        data object ShareTipCard : Event
        data object CopyTipLink : Event
        data object DownloadTipCard : Event
        data class ExportTipCard(val format: TipCodeExportFormat) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull { event ->
                analytics.track(AddMoneyEvents.opened(event.source))
                purchaseMethodController.presentDepositOptions(popToRoot = true)
            }.onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // Rebuild the viewer's own tip card whenever their profile becomes available/changes, so
        // the v2 "You" tab can show it at the top. Warm the Sharesheet preview eagerly so it's ready
        // by the time the user taps "Share as a Link" — but only for a card that can be shared.
        //
        // Gated on Ready: a named account restores its cached profile before auth completes, so
        // waiting here means it never flashes the claim prompt on the way in.
        userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile }
            .distinctUntilChanged()
            .onEach { profile ->
                if (profile?.displayName.isNullOrEmpty()) {
                    // No name means no card yet — the tab prompts to claim one instead. Built
                    // locally, so an account whose profile the server has never seen still gets it.
                    dispatchEvent(
                        Event.OnTipCardStateChanged(
                            TipCardState.Unclaimed(tippingCoordinator.unclaimedTipCard())
                        )
                    )
                } else {
                    tippingCoordinator.resolveTipCard().onSuccess { card ->
                        val userId = tippingCoordinator.currentUserId
                        dispatchEvent(
                            Event.OnTipCardStateChanged(
                                TipCardState.Claimed(card, tipCardLink(card.user, userId))
                            )
                        )
                        userId?.let { tipCodePreviewCache.prepare(it, card) }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Gated on Ready for the same reason as the tip card: a named account restores its cached
        // profile before auth completes, so the checklist would otherwise flash an outstanding
        // photo step at someone who already has one.
        userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile }
            .distinctUntilChanged()
            .map { profileTutorialItems(it) }
            .onEach { dispatchEvent(Event.OnProfileTutorialChanged(it)) }
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
            .filterIsInstance<Event.SetProfilePicture>()
            .onEach {
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.UpdateUserProfile(
                            origin = AppRoute.Tabs.Menu,
                            // The account already has a name and a card by the time this
                            // checklist is drawn, so the flow reduces to the one step.
                            steps = listOf(UpdateProfileStep.Photo),
                        )
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SetMinimumTip>()
            .onEach {
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.UpdateUserProfile(
                            origin = AppRoute.Tabs.Menu,
                            steps = listOf(UpdateProfileStep.MinimumTip),
                        )
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyTipLink>()
            .mapNotNull { stateFlow.value.tipLink }
            .onEach { link ->
                // The row shows an abbreviated link; the clipboard gets the whole thing.
                clipboardManager.setText(
                    text = link,
                    label = resources.getString(R.string.title_clipboardLabelTipCardLink),
                )
                toastController.showToast(R.string.action_copied, replacePrevious = true)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DownloadTipCard>()
            .onEach {
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.title_downloadTipCardAs),
                    actions = downloadOptions(resources) { format ->
                        dispatchEvent(Event.ExportTipCard(format))
                    },
                    showCancel = false,
                    showScrim = true,
                )
            }
            .launchIn(viewModelScope)

        // Render the chosen format, then hand the file to the Sharesheet — Android has no
        // permissionless "save to Photos", and the chooser already offers Files/Drive/Photos.
        eventFlow
            .filterIsInstance<Event.ExportTipCard>()
            .mapNotNull { event -> stateFlow.value.tipCard?.let { it to event.format } }
            .onEach { (card, format) ->
                val export = tipCodeExporter.export(card, format)
                if (export == null) {
                    BottomBarManager.showMessage(
                        title = resources.getString(R.string.error_title_tipCardExportFailed),
                        message = resources.getString(R.string.error_description_tipCardExportFailed),
                    )
                    return@onEach
                }
                shareable.present(
                    Shareable.TipCodeImage(
                        export = export,
                        title = resources.getString(R.string.title_shareTipCode),
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareTipCard>()
            .mapNotNull { tippingCoordinator.currentUserId }
            .map { userId ->
                val user = stateFlow.value.tipCard?.user
                // Attach the eagerly-rendered preview if it's ready; null shares the URL alone.
                shareable.present(
                    Shareable.Profile(
                        userId = userId,
                        displayName = user?.displayName,
                        username = user?.username,
                        preview = tipCodePreviewCache.get(userId),
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    /** The link the card shares itself with. Null only when there is no signed-in user to address. */
    private fun tipCardLink(profile: UserProfile, userId: ID?): String? =
        userId?.let { Linkify.tipcard(TipCardOwner.preferringUsername(profile.username, it)) }

    internal companion object {
        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnTipCardStateChanged -> { state ->
                    state.copy(tipCardState = event.tipCardState)
                }

                is Event.OnUsernameProgressChanged -> { state ->
                    state.copy(
                        usernameProgress = event.progress,
                        usernameMinimumBalance = event.minimumBalance,
                    )
                }

                is Event.OnProfileTutorialChanged -> { state ->
                    state.copy(profileTutorial = event.items)
                }

                is Event.PresentDepositOptions,
                Event.ClaimTipCard,
                Event.ClaimUsername,
                Event.SetProfilePicture,
                Event.SetMinimumTip,
                Event.ShareTipCard,
                Event.CopyTipLink,
                Event.DownloadTipCard,
                is Event.ExportTipCard,
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}
