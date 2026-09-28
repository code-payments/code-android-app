package com.flipcash.app.messenger.internal.screens.profile

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.flipcash.core.R as CoreR

/**
 * Another person's profile opened on its own, from a `flipcash.com/{username}` or
 * `flipcash.com/{uuid}` link, rather than from inside a chat.
 *
 * [ChatProfileViewModel] is handed a participant the chat already knows. A link hands over only
 * an address, so this one's job is the lookup: resolve the address to a profile, or say why it
 * couldn't and let the screen leave.
 *
 * The failure is announced the way a tip card link's was before this screen replaced it (see
 * `TipCardDelegate.announceUnresolvable`): an unclaimed handle is information, not an error, and
 * only a failed fetch is ours to apologise for.
 */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val profiles: ProfileController,
    private val userManager: UserManager,
    private val exchange: Exchange,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ProfileViewModel.State, ProfileViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val participant: ChatParticipant.TipUser? = null,
        /** The Send Cash shortcut's glyph, as the chat derives it. */
        val cashSymbol: String = "$",
    )

    sealed interface Event {
        data class Load(val address: ProfileAddress) : Event
        data class Loaded(val participant: ChatParticipant.TipUser) : Event
        data class OnCurrencySymbolUpdated(val symbol: String) : Event

        /** Nobody to show; the failure has been announced and the screen should leave. */
        data object Unavailable : Event

        /**
         * The address turned out to be the viewer's own, which only the lookup can tell for a
         * handle opened before this account's profile arrived. See `AppRouter.profile`.
         */
        data object OwnProfile : Event
    }

    init {
        exchange.observePreferredRate()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)?.let { currency ->
                    dispatchEvent(Event.OnCurrencySymbolUpdated(currency.symbol.ifEmpty { "$" }))
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Load>()
            .map { it.address }
            .distinctUntilChanged()
            .onEach { address ->
                lookup(address)
                    .onSuccess { profile ->
                        val userId = profile.userId
                        when {
                            // Without an id there's nothing to open a DM with.
                            userId == null -> {
                                trace(
                                    tag = "Profile",
                                    message = "Profile for $address carries no user id",
                                )
                                announceUnavailable(address, cause = null)
                                dispatchEvent(Event.Unavailable)
                            }

                            userId == userManager.accountId -> dispatchEvent(Event.OwnProfile)

                            else -> dispatchEvent(
                                Event.Loaded(ChatParticipant.TipUser(userId, profile))
                            )
                        }
                    }
                    .onFailure { cause ->
                        trace(
                            tag = "Profile",
                            message = "Failed to resolve profile for $address",
                            error = cause,
                        )
                        announceUnavailable(address, cause)
                        dispatchEvent(Event.Unavailable)
                    }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun lookup(address: ProfileAddress) = when (address) {
        is ProfileAddress.ById -> profiles.getProfileForUser(address.userId)
        is ProfileAddress.ByUsername -> profiles.getProfileForUsername(address.username)
    }

    private fun announceUnavailable(address: ProfileAddress, cause: Throwable?) {
        if (address is ProfileAddress.ByUsername && cause is GetUserProfileError.NotFound) {
            BottomBarManager.showInfo(
                title = resources.getString(CoreR.string.error_title_usernameNotFound),
                message = resources.getString(
                    CoreR.string.error_description_usernameNotFound,
                    address.username,
                ),
            )
        } else {
            BottomBarManager.showError(
                title = resources.getString(CoreR.string.error_title_profileUnavailable),
                message = resources.getString(CoreR.string.error_description_profileUnavailable),
            )
        }
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.Loaded -> { state -> state.copy(participant = event.participant) }
                is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
                is Event.Load,
                Event.Unavailable,
                Event.OwnProfile -> { state -> state }
            }
        }
    }
}
