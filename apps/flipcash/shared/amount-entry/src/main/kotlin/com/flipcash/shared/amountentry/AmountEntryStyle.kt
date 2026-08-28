package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.ConfirmationStyle

data class AmountEntryStyle(
    val actionLabel: AmountEntryLabel,
    val actionStyle: ConfirmationStyle = ConfirmationStyle.Button,
    val canChangeCurrency: Boolean = true,
    val infoHint: (maxFormatted: String) -> String = { "" },
    val overMaxHint: (maxFormatted: String) -> String = { "" },
    val belowMinHint: ((minFormatted: String) -> String)? = null,
    val standingHint: StandingHint = StandingHint.Ceiling,
) {
    /**
     * Which bound the resting hint describes when the entry has both a floor and a ceiling.
     * Errors are unaffected — an amount outside either bound still reports the bound it broke.
     */
    enum class StandingHint {
        /** "Enter up to $X" — the usual guidance, since the ceiling is what most flows constrain. */
        Ceiling,

        /**
         * "Minimum tip $X" — for flows where the floor is the rule the user is likely to break and
         * the ceiling is incidental (a tip chat's ceiling is just the sender's own balance).
         */
        Floor,
    }
}
