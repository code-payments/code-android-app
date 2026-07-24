package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.session.TipCardOperations
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.opencode.model.core.ID
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [TipCardOperations] — the single public entry point for presenting another
 * user's tip card, whether it arrives via a deeplink (`/tip/{userId}`), a scanned QR link,
 * or a scanned OpenCode tip payload (see [CodeScanDelegate.onTipCardScanned]).
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
    dispatchers: DispatcherProvider,
) : TipCardOperations {

    sealed interface Event {
        data class Present(val card: Scannable.TipCard) : Event
    }

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    // Users with an in-flight resolve — coalesces duplicate requests (e.g. repeated scan frames).
    private val inFlight = MutableStateFlow<Set<ID>>(emptySet())

    override fun resolveTipCard(user: ID) {
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
