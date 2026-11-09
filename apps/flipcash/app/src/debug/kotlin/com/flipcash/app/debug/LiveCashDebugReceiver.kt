package com.flipcash.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.LiveCashArrival
import com.flipcash.shared.chat.LiveCashArrivals
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only: feeds a synthetic live cash message, to the viewer, into the open chat through
 * the same sink the event stream uses, so the bill, the keyboard and the toast can be driven from
 * adb without moving money. It credits nothing.
 *
 *     adb shell am broadcast -n com.flipcash.app.android/com.flipcash.app.debug.LiveCashDebugReceiver \
 *         -a com.flipcash.debug.LIVE_CASH --ef amount 1.25
 *     adb shell am broadcast -n com.flipcash.app.android/com.flipcash.app.debug.LiveCashDebugReceiver \
 *         -a com.flipcash.debug.LIVE_CASH --ef amount 1.25 --ez twice true
 *
 * The component (`-n`) is required: an implicit broadcast is not delivered to a manifest receiver
 * and fails silently.
 *
 * The message is addressed to the viewer as a legacy DM payment (no receiver field), from a sender
 * id that is not theirs. `twice` fires a second one at once, which must be dropped.
 */
class LiveCashDebugReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun arrivals(): LiveCashArrivals
        fun chats(): ChatCoordinator
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val chat = deps.chats().state.value.activeChat ?: return
        val amount = intent.getFloatExtra("amount", 1.0f).toDouble()
        val count = if (intent.getBooleanExtra("twice", false)) 2 else 1
        repeat(count) { i ->
            deps.arrivals().offer(
                LiveCashArrival(
                    chatId = chat,
                    messageId = System.currentTimeMillis() + i,
                    senderId = List(32) { 7 },
                    content = MessageContent.Cash(
                        intentId = List(32) { (i + 1).toByte() },
                        amount = Fiat(amount),
                        mint = Mint.usdf,
                    ),
                )
            )
        }
    }
}
