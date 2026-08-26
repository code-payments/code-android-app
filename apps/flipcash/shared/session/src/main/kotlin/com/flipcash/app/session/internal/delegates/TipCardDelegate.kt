package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tipping.OwnTipCard
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.session.TipCardEvent
import com.flipcash.app.session.TipCardOperations
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [TipCardOperations] — the single public entry point for presenting another
 * user's tip card, whether it arrives via a deeplink (`/tip/{userId}` or the vanity
 * `flipcash.com/{username}`), a scanned QR link, or a scanned OpenCode tip payload
 * (see [CodeScanDelegate.onTipCardScanned]).
 *
 * 1. Resolves [ID] to a [Scannable.TipCard] via [TippingCoordinator.resolveTipCard]
 *    (a server-backed profile fetch).
 * 2. Coalesces concurrent resolves for the same user via [inFlight] so repeated camera
 *    frames don't fan out into redundant fetches.
 * 3. On success emits [Event.Present]; the [com.flipcash.app.session.internal.RealSessionController]
 *    shell collects it and hands the resolved card to [BillPresentationDelegate.presentTipCard]
 *    (which owns writes to the bill container). Presentation itself is intentionally not done
 *    here — this delegate resolves, the bill delegate shows.
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class TipCardDelegate @Inject constructor(
    private val tippingCoordinator: TippingCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : TipCardOperations {

    sealed interface Event {
        data class Present(val card: Scannable.TipCard) : Event
    }

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    // Separate from [events]: that channel is the shell's (single-consumer, consumeAsFlow), while
    // this one is the UI's. Replay-less, so an event with no scanner on screen is simply dropped.
    private val _tipCardEvents = MutableSharedFlow<TipCardEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val tipCardEvents: Flow<TipCardEvent> = _tipCardEvents.asSharedFlow()

    // Users with an in-flight resolve — coalesces duplicate requests (e.g. repeated scan frames).
    private val inFlight = MutableStateFlow<Set<ID>>(emptySet())

    override fun resolveTipCard(owner: TipCardOwner) {
        // You can't tip yourself, so there's no card to present for your own account, by either
        // name. Both scan paths (a QR tip link and an OpenCode tip payload) land here, so this is
        // the one place that has to answer for them: signal the UI to show the You tab, which owns
        // your card, instead of silently doing nothing. Checked before dispatch so your own card
        // never costs a profile fetch. A self deeplink is diverted earlier still, by AppRouter.
        // Mirrors iOS TipFlow.begin's `guard userID != session.userID`.
        if (owner.isSelf(tippingCoordinator.currentUserId, tippingCoordinator.currentUsername)) {
            _tipCardEvents.tryEmit(TipCardEvent.OwnCardScanned)
            return
        }

        when (owner) {
            is TipCardOwner.ById -> resolveById(owner.userId)
            is TipCardOwner.ByUsername -> resolveByUsername(owner.username)
        }
    }

    private fun resolveById(user: ID) {
        if (!inFlight.add(user)) return

        scope.launch {
            tippingCoordinator.resolveTipCard(user)
                .onSuccess { card ->
                    // Always present the card. Whether the tip modal slides up (or an
                    // add-money prompt shows instead) is decided in the UI from the
                    // coordinator's affordability state — see TipCardDecorator.
                    analytics.tipCardPresented()
                    _events.trySend(Event.Present(card))
                }
                .onFailure {
                    trace(
                        tag = "Session",
                        message = "Failed to resolve tip card for user",
                        error = it,
                    )
                }

            inFlight.remove(user)
        }
    }

    private fun resolveByUsername(username: String) {
        // No coalescing here, unlike the id path: that one exists because the camera hands the same
        // user over on every frame. A vanity link arrives once, from a tap.
        scope.launch {
            tippingCoordinator.resolveTipCard(username)
                .onSuccess { card ->
                    analytics.tipCardPresented()
                    _events.trySend(Event.Present(card))
                }
                .onFailure { cause ->
                    // Your own handle, discovered only after the fetch — see OwnTipCard. Same
                    // answer as the pre-dispatch guard above, not a failure to report.
                    if (cause is OwnTipCard) {
                        _tipCardEvents.tryEmit(TipCardEvent.OwnCardScanned)
                        return@launch
                    }
                    trace(
                        tag = "Session",
                        message = "Failed to resolve tip card for @$username",
                        error = cause,
                    )
                    announceUnresolvable(username, cause)
                }
        }
    }

    /**
     * Says out loud that a vanity link went nowhere.
     *
     * The id path stays silent on failure because an id is machine-supplied — it comes off a code
     * the camera just read, so a miss there is a transient fetch, not a wrong address. A handle is
     * the opposite: it is typed, printed on merch, or pasted out of a bio, and it goes stale the
     * moment its owner changes it. Without this the app opens on the home screen and looks like it
     * ignored the tap.
     *
     * Informational, not an error — an unclaimed handle is a fact about the link, not a fault in
     * the app. Only the network case is ours to apologise for.
     */
    private fun announceUnresolvable(username: String, cause: Throwable) {
        if (cause is GetUserProfileError.NotFound) {
            BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameNotFound),
                message = resources.getString(R.string.error_description_usernameNotFound, username),
            )
        } else {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_tipCardUnavailable),
                message = resources.getString(R.string.error_description_tipCardUnavailable),
            )
        }
    }

    /** Atomically adds [user]; returns true only if it wasn't already in flight. */
    private fun MutableStateFlow<Set<ID>>.add(user: ID): Boolean {
        var added = false
        update { current ->
            added = user !in current
            // plusElement (not `+`): ID is List<Byte>, so `+` would pick the Iterable overload.
            if (added) current.plusElement(user) else current
        }
        return added
    }

    private fun MutableStateFlow<Set<ID>>.remove(user: ID) = update { it.minusElement(user) }
}
