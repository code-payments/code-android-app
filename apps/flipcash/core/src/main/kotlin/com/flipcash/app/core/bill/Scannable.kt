package com.flipcash.app.core.bill

import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlin.time.Duration

/**
 * Anything that renders a scannable code. The single common member is [data] —
 * the encoded KikCode bytes (an `OpenCodePayload.codeData`).
 */
sealed interface Scannable {
    val data: List<Byte>

    /**
     * A payment-bearing scannable (carries a token + amount). [CashBill] and [GoldBar]
     * differ only in which renderer draws them; the type IS the render choice.
     */
    sealed interface Payable : Scannable {
        val token: Token
        val amount: LocalFiat
        val didReceive: Boolean
        val disableGestures: Boolean
        val confirmationDelay: Duration
        val kind: Kind
        val verifiedState: VerifiedState?
        val nonce: List<Byte>

        enum class Kind { cash, airdrop }

        val canSwipeToDismiss: Boolean get() = !disableGestures
        val canFlip: Boolean get() = false
        val metadata: Metadata get() = Metadata(token = token, amount = amount, data = data)

        /** Returns a copy of this bill with its scannable [code] (+ [nonce]) stamped in. */
        fun stamped(code: List<Byte>, nonce: List<Byte>): Payable

        companion object {
            /** Applies the USDF->[GoldBar] rule; every other token -> [CashBill]. */
            fun forToken(
                token: Token,
                amount: LocalFiat,
                didReceive: Boolean = false,
                disableGestures: Boolean = false,
                confirmationDelay: Duration = Duration.ZERO,
                data: List<Byte> = emptyList(),
                kind: Kind = Kind.cash,
                verifiedState: VerifiedState? = null,
                nonce: List<Byte> = emptyList(),
            ): Payable = if (token.address == Mint.usdf) {
                GoldBar(token, amount, didReceive, disableGestures, confirmationDelay, data, kind, verifiedState, nonce)
            } else {
                CashBill(token, amount, didReceive, disableGestures, confirmationDelay, data, kind, verifiedState, nonce)
            }
        }
    }

    data class CashBill(
        override val token: Token,
        override val amount: LocalFiat,
        override val didReceive: Boolean = false,
        override val disableGestures: Boolean = false,
        override val confirmationDelay: Duration = Duration.ZERO,
        override val data: List<Byte> = emptyList(),
        override val kind: Payable.Kind = Payable.Kind.cash,
        override val verifiedState: VerifiedState? = null,
        override val nonce: List<Byte> = emptyList(),
    ) : Payable {
        override fun stamped(code: List<Byte>, nonce: List<Byte>) = copy(data = code, nonce = nonce)
    }

    data class GoldBar(
        override val token: Token,
        override val amount: LocalFiat,
        override val didReceive: Boolean = false,
        override val disableGestures: Boolean = false,
        override val confirmationDelay: Duration = Duration.ZERO,
        override val data: List<Byte> = emptyList(),
        override val kind: Payable.Kind = Payable.Kind.cash,
        override val verifiedState: VerifiedState? = null,
        override val nonce: List<Byte> = emptyList(),
    ) : Payable {
        override fun stamped(code: List<Byte>, nonce: List<Byte>) = copy(data = code, nonce = nonce)
    }

    data class TipCard(
        override val data: List<Byte>,
        val username: String,
    ) : Scannable
}
