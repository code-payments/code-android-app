package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.features.messenger.R
import com.getcode.opencode.model.financial.Fiat

/**
 * A rule's amount as the group's screens write it: "$10", "$2.50", and "$10 of NYC" when [tokenName]
 * is given. Pass null for the reserve or an unnamed token, which states the amount alone. A rule is
 * priced in USD and shown in the viewer's currency.
 */
@Composable
internal fun ruleAmountLabel(amount: Fiat, tokenName: String?): String {
    val formatted = amount.inDisplayCurrency().formatted(Fiat.FormattingRule.Truncated)
    return tokenName?.let { stringResource(R.string.label_chat_preview_cash_suffix, formatted, it) } ?: formatted
}
