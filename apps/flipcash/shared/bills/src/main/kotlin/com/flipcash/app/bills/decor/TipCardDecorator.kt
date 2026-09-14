package com.flipcash.app.bills.decor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.core.tipping.TipEvent
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.ModalAnimationSpeed
import kotlinx.coroutines.delay

/**
 * Decorator owned by a [Scannable.TipCard] (node 10074:18893). Scanning a card is a way of reaching
 * a person, so it ends where reaching them by `@handle` ends: in their chat, started or not. What
 * used to happen here — a modal over the card taking a tip amount, sending it, then handing off to
 * the chat — is now the chat's own job, and the fee it charges is the same one either route pays.
 *
 * The card still gets a beat on screen before the chat takes over. It is the same 450ms the modal
 * used to take sliding up ([ModalAnimationSpeed.Normal] at the tip card's zero confirmation delay),
 * so the scan still reads as "I found this person" rather than as a screen that flashed past.
 */
internal data class TipCardDecorator(private val tipCard: Scannable.TipCard) : ScannableDecorator {
    @Composable
    override fun BoxScope.Content(context: ScannableDecoratorContext) {
        val billState = context.billState
        val navigator = LocalCodeNavigator.current
        val tipCoordinator = LocalTipCoordinator.current

        val tipPresented = context.liveBill is Scannable.TipCard
        // The id is server-provided, so a card that resolved to a locally-constructed profile has
        // no chat to open. Leave it up rather than dismissing into nothing.
        val userId = tipCard.user.userId

        LaunchedEffect(tipPresented, userId) {
            if (!tipPresented || userId == null) return@LaunchedEffect
            delay(ModalAnimationSpeed.Normal(billState.confirmationDelayMillis).delay.toLong())
            navigator.push(
                AppRoute.Messaging.Chat(ChatIdentifier.ByUser(userId, tipCard.user)),
            )
            context.onDismiss()
        }

        // The coordinator's one-shot UI events. Collected here, not in a modal, so it fires
        // regardless of what is on screen.
        LaunchedEffect(tipCoordinator) {
            tipCoordinator.events.collect { event ->
                when (event) {
                    is TipEvent.OpenRoute -> {
                        navigator.openAsSheet(event.route)
                    }
                    is TipEvent.LaunchChat -> {
                        navigator.navigateAll(
                            listOf(
                                AppRoute.Sheets.Tips(),
                                AppRoute.Messaging.Chat(event.identifier, openKeyboard = true),
                            ),
                        )
                        context.onDismiss()
                    }
                }
            }
        }
    }
}
