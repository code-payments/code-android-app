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
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.services.user.AuthState
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.tipping.TippingCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.common.ui.profile.joinedLabel
import com.getcode.opencode.model.core.ID
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
internal class MenuScreenViewModel @Inject constructor(
    userManager: UserManager,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    analytics: FlipcashAnalytics,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodePreviewCache: TipCodePreviewCache,
    private val featuredGroupsStore: FeaturedGroupsStore,
    tipPayments: TipPaymentDelegate,
) :
    BaseViewModel<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent,
        defaultDispatcher = dispatchers.Default,
    ) {
    data class State(
        // What the header draws: nothing yet, the claim prompt, or the viewer's own profile.
        val profileState: ProfileState = ProfileState.Unknown,
        // What another user must send to open a chat with the viewer, formatted. Null until it
        // resolves, which the stats card draws as a dash.
        val minimumToChat: String? = null,
        // The viewer's own featured groups, in profile order. Empty until the first read lands.
        val featuredGroups: List<ChatMetadata> = emptyList(),
        // The username the featured-groups read last succeeded for. Compared with the live username
        // by [showFavoritesEmptyState], so a changed handle is unread until its own read lands.
        val favoritesReadFor: String? = null,
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
         * The account has no display name yet. [placeholder] is a real scannable stand-in drawn
         * blurred behind the claim prompt; it is never shareable. [profile] is whatever the server
         * already holds (a cover, say), or null for an account it has never seen.
         */
        data class Unclaimed(
            val placeholder: Scannable.TipCard?,
            val profile: UserProfile? = null,
        ) : ProfileState

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
        data class OnMinimumToChatChanged(val minimumToChat: String?) : Event
        data class OnFeaturedGroupsChanged(val groups: List<ChatMetadata>) : Event
        data class OnFavoritesRead(val username: String, val groups: List<ChatMetadata>) : Event

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
                    // No name yet: the header still draws (cover, avatar, Edit Profile) with the claim
                    // prompt under it, as on iOS. The placeholder is built locally, so an account whose profile the server has never seen still
                    // gets it.
                    dispatchEvent(
                        Event.OnProfileStateChanged(
                            ProfileState.Unclaimed(tippingCoordinator.unclaimedTipCard(), profile)
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

        // The featured groups are held for the session, so a save in the picker reaches this tab
        // without a read. The read here is for the first look and for a handle that changes.
        featuredGroupsStore.groups
            .onEach { dispatchEvent(Event.OnFeaturedGroupsChanged(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile?.username }
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .onEach { username ->
                if (featuredGroupsStore.load(username)) dispatchEvent(Event.OnFavoritesRead(username, featuredGroupsStore.groups.value))
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
            .mapNotNull { shareProfileRoute(stateFlow.value.profileState, tippingCoordinator.currentUserId) }
            .onEach { dispatchEvent(Event.OpenScreen(it)) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        internal val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProfileStateChanged -> { state ->
                    state.copy(profileState = event.profileState)
                }

                is Event.OnMinimumToChatChanged -> { state ->
                    state.copy(minimumToChat = event.minimumToChat)
                }

                is Event.OnFeaturedGroupsChanged -> { state ->
                    state.copy(featuredGroups = event.groups)
                }

                is Event.OnFavoritesRead -> { state ->
                    state.copy(favoritesReadFor = event.username, featuredGroups = event.groups)
                }

                is Event.PresentDepositOptions,
                Event.ClaimTipCard,
                Event.ShareProfile,
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}

/**
 * Where the header's share button goes: the same share sheet every other profile opens, once the
 * viewer has a profile worth sharing. An account with no name has no card or link to hand out, so
 * the tap leads nowhere, and neither does one with no signed-in user to address.
 */
internal fun shareProfileRoute(
    profileState: MenuScreenViewModel.ProfileState,
    userId: ID?,
): AppRoute? {
    if (profileState !is MenuScreenViewModel.ProfileState.Named || userId == null) return null
    return AppRoute.Messaging.ShareProfileToChats(
        userId = userId,
        username = profileState.profile.username,
        displayName = profileState.profile.displayName,
        // Your own profile has no DM to leave out.
        directChatId = null,
    )
}

/**
 * Whether the own profile's featured-groups section may draw its "None yet" empty state. With no
 * username there is nothing to read, so it may at once; otherwise only once a read for that
 * username has succeeded, so an account that has favorites never flashes the empty state while the
 * read is in flight or after it fails.
 */
internal fun showFavoritesEmptyState(username: String?, favoritesReadFor: String?): Boolean =
    username.isNullOrEmpty() || favoritesReadFor == username

/**
 * Whether the profile is complete: a profile photo, a cover photo and a non-blank bio. Username,
 * minimum-to-chat and favorite chats do not count.
 */
internal fun isProfileComplete(profile: UserProfile?): Boolean =
    profile != null &&
        profile.profilePicture != null &&
        profile.coverPicture != null &&
        profile.bio.isNotBlank()
