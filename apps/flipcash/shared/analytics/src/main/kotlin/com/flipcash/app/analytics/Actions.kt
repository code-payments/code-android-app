package com.flipcash.app.analytics

import com.getcode.libs.analytics.AppAction

sealed class Action : AppAction {
    data object CreateAccount : Action() {
        override val value: String = "Button: Create Account"
    }

    data object SaveAccessKey : Action() {
        override val value: String = "Button: Save Access Key"
    }

    data object WroteAccessKey : Action() {
        override val value: String = "Button: Wrote Access Key"
    }

    data object AllowCamera : Action() {
        override val value: String = "Button: Allow Camera"
    }

    data object AllowPush : Action() {
        override val value: String = "Button: Allow Push"
    }

    data object SkipPush : Action() {
        override val value: String = "Button: Skip Push"
    }

    data object CompletedOnboarding : Action() {
        override val value: String = "Complete Onboarding"
    }
}