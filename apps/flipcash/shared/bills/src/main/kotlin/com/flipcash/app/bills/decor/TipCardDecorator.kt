package com.flipcash.app.bills.decor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.core.tipping.TipEvent
import com.flipcash.app.session.Grabbed
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Decorator owned by a [Scannable.TipCard] (node 10074:18893). Scanning a card is a way of reaching
 * a person, and what it shows is who they are: their profile, every time, even when a DM with them
 * already exists. The profile is where the DM is opened from (Open Chat) or started (Send to Start
 * Chatting), the same as on iOS.
 *
 * The card still gets a beat on screen before the profile takes over, [CardHoldMillis], matching
 * iOS, so the scan reads as "I found this person" rather than as a screen that flashed past. Then
 * the card's pop overlaps the push, so the hand-off reads as one movement.
 */
internal data class TipCardDecorator(private val tipCard: Scannable.TipCard) : ScannableDecorator {
    @Composable
    override fun BoxScope.Content(context: ScannableDecoratorContext) {
        val navigator = LocalCodeNavigator.current
        val tipCoordinator = LocalTipCoordinator.current

        val tipPresented = context.liveBill is Scannable.TipCard
        // The id is server-provided, so a card that resolved to a locally-constructed profile has
        // no profile to open. Leave it up rather than dismissing into nothing.
        val userId = tipCard.user.userId

        LaunchedEffect(tipPresented, userId) {
            if (!tipPresented || userId == null) return@LaunchedEffect
            delay(CardHoldMillis)
            // Dismiss with [Grabbed] rather than [PutInWallet]: the card leaves as a 100ms
            // fade-and-scale instead of a 600ms slide back to the wallet, which the entry sliding
            // out from under it would swallow whole. [PopLeadMillis] then gives that pop a head
            // start, so it is already running when the push takes the entry away — dismissed and
            // pushed in the same frame, the card is removed rather than seen to leave.
            //
            // Uncancellable because dismissing sets the bill to null, which flips `tipPresented`
            // and restarts this effect; a plain delay here would take the push down with it.
            withContext(NonCancellable) {
                context.onDismiss(Grabbed)
                delay(PopLeadMillis)
                // Chats under the profile, so back and a block land on the list the scan came
                // from rather than on the scanner.
                navigator.navigateAll(
                    listOf(
                        AppRoute.Tabs.Chats,
                        AppRoute.Messaging.Profile(ProfileAddress.ById(userId), ProfileOrigin.Scan),
                    )
                )
            }
        }

        // The coordinator's one-shot UI events. Collected here, not in a modal, so it fires
        // regardless of what is on screen.
        LaunchedEffect(tipCoordinator) {
            tipCoordinator.events.collect { event ->
                when (event) {
                    is TipEvent.OpenRoute -> navigator.openAsSheet(event.route)
                }
            }
        }
    }
}

/** How long the scanned card stays up before the profile replaces it. iOS holds it 750ms. */
private const val CardHoldMillis = 750L

/**
 * How long the card's exit gets to itself before the profile is pushed. Long enough for the
 * fade-and-scale to be under way when the navigation transition starts, short enough that the two
 * still read as one movement.
 */
private const val PopLeadMillis = 50L
