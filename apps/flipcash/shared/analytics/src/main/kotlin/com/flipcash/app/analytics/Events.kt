package com.flipcash.app.analytics

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode

sealed interface AnalyticsEvent {

    val name: String

    data class PaidForAccount(
        val price: Double,
        val currency: CurrencyCode,
        val owner: KeyPair
    ) : AnalyticsEvent {
        override val name: String = "Create Account Payment"
    }

    sealed interface Transfer : AnalyticsEvent
    data object GrabBill : Transfer {
        override val name: String = "Grab Bill"
    }

    data object GiveBill : Transfer {
        override val name: String = "Give Bill"
    }

    data object Withdrawal : Transfer {
        override val name: String = "Withdrawal"
    }

    data class SentCashLink(
        val clipboard: Boolean? = null,
        val app: String? = null
    ) : Transfer {
        override val name: String = "Send Cash Link"
    }

    data object ClaimedCashLink : Transfer {
        override val name: String = "Receive Cash Link"
    }

    sealed interface PoolEvent : AnalyticsEvent {
        val id: ID
    }

    data class PoolOpened(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Opened From Deeplink"
    }

    data class PoolCreated(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Created"
    }

    data class PlacedBid(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Place Bet"
    }

    data class DeclaredOutcome(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Declared Outcome"
    }

    sealed interface OnRampOpenEvent : AnalyticsEvent {
        data object Settings : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Settings"
        }

        data object Balance : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Balance"
        }

        data object Give : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Give"
        }
    }

    sealed interface OnRampVerificationEvent : AnalyticsEvent {
        data object ShowInfo : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Verification Info"
        }

        data object EnterPhone : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Enter Phone"
        }

        data object ConfirmPhone : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Confirm Phone"
        }

        data object EnterEmail : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Enter Email"
        }

        data object ConfirmEmail : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Confirm Email"
        }
    }

    sealed interface OnRampPurchaseEvent : AnalyticsEvent {
        data object PresetSelected : OnRampPurchaseEvent {
            override val name: String = "Onramp: Amount Selected"
        }

        data object EnterCustomAmount : OnRampPurchaseEvent {
            override val name: String = "Onramp: Enter Custom Amount"
        }

        data object InvokePayment : OnRampPurchaseEvent {
            override val name: String = "Onramp: Invoke Payment"
        }

        data object InvokePaymentCustom : OnRampPurchaseEvent {
            override val name: String = "Onramp: Invoke Payment Custom"
        }

        data object Completed : OnRampPurchaseEvent {
            override val name: String = "Onramp: Completed"
        }
    }

    sealed interface WalletEvent : AnalyticsEvent {
        val provider: OnRampProvider.UsesDeeplinks
    }

    data class WalletConnect(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name: String = "Wallet: Connect"
    }

    data class WalletRequestAmount(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Request Amount"
    }

    data class WalletSubmitTransaction(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Transactions Submitted"
    }

    data class WalletTransactionFailed(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Transactions Failed"
    }

    data class WalletTransactionCancelled(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Cancel"
    }
}