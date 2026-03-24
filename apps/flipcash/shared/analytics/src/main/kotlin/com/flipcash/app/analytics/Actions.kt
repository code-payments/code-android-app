package com.flipcash.app.analytics

import com.getcode.libs.analytics.AppAction

sealed interface Action : AppAction {
    data object CompletedOnboarding : Action {
        override val value: String = "Complete Onboarding"
    }
}

sealed interface Button: AppAction {
    data object CreateAccount : Button {
        override val value: String = "Button: Create Account"
    }

    data object SaveAccessKey : Button {
        override val value: String = "Button: Save Access Key"
    }

    data object WroteAccessKey : Button {
        override val value: String = "Button: Wrote Access Key"
    }

    data object AllowCamera : Button {
        override val value: String = "Button: Allow Camera"
    }

    data object AllowPush : Button {
        override val value: String = "Button: Allow Push"
    }

    data object SkipPush : Button {
        override val value: String = "Button: Skip Push"
    }

    data object TokenBuyWithReserves : Button {
        override val value: String = "Button: Buy With Reserves"
    }

    data object TokenBuyWithPhantom : Button {
        override val value: String = "Button: Buy With Phantom"
    }

    data object TokenBuyWithCoinbase : Button {
        override val value: String = "Button: Buy With Coinbase"
    }

    data object TokenSell : Button {
        override val value: String = "Button: Sell"
    }

    data object TokenShare : Button {
        override val value: String = "Button: Share Token Info"
    }
}