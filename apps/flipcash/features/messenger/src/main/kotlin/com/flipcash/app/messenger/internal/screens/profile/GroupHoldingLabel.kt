package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.app.messenger.internal.RuleCurrency
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint

/**
 * A rule's amount as the profile writes it: "$10", "$2.50", and "$10 of NYC" for a token other
 * than USDF whose name is known. A rule naming no mint, or the reserve, states the amount alone.
 */
@Composable
internal fun holdingLabel(
    rule: ChatRuleRequirement.MinimumBalance,
    tokens: Map<List<Byte>, RuleCurrency>,
): String = holdingLabel(rule.amount, rule.mints.firstOrNull()?.let { Mint(it.bytes) }, tokens)

@Composable
internal fun holdingLabel(
    amount: Fiat,
    mint: Mint?,
    tokens: Map<List<Byte>, RuleCurrency>,
): String {
    val formatted = amount.formatted(Fiat.FormattingRule.Truncated)
    val name = mint?.takeUnless { it == Mint.usdf }?.let { tokens[it.bytes] }?.name
        ?: return formatted
    return stringResource(R.string.label_chat_preview_cash_suffix, formatted, name)
}

