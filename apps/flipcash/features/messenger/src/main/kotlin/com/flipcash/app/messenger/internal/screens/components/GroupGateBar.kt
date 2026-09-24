package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.app.messenger.internal.RuleCurrency
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.theme.CodeButton
import com.getcode.view.LoadingSuccessState

/**
 * What stands where the composer does for someone who is not in the group.
 *
 * Node 10125:19153. The caption states the group's standing requirement, not what is holding this
 * viewer up, so it is shown whether or not they meet it: nodes 10127:117120 and 10127:117171 carry
 * the same line above an eligible viewer's Join and a blocked one's Buy More. That leaves
 * [GroupAccess] deciding only the button — which is why this bar can stand, captions and all, on
 * frames where the access has not landed yet: whether the viewer is outside the group is the
 * subject's answer, and only what they can do about it waits on their balance.
 *
 * A staff-only group states its line the same way. It is not a bar the viewer can go and clear, but
 * it is the reason Join is dead, and the alternative is a disabled button with nothing above it.
 */
@Composable
internal fun GroupGateBar(
    /** What the viewer may do here, or null while the balance the rules are read against loads. */
    access: GroupAccess?,
    /** The group's balance rule, if it has one — the chat's, not this viewer's shortfall. */
    requirement: ChatRuleRequirement.MinimumBalance?,
    /** The token that rule names, once the cache resolves it, and how to write it. */
    currency: RuleCurrency?,
    /** Whether the group is staff-only — the chat's rule, read the same way [requirement] is. */
    staffOnly: Boolean,
    /**
     * Where Join and Buy More go. Passed rather than read off `LocalChatActionHandler`: that local
     * is provided around the transcript, and the gate stands where the composer does — outside it.
     * Reading it here got the static default, which is a handler that does nothing.
     */
    onAction: ChatActionHandler,
    /** The Join button's loading/success state, straight off the screen's own. */
    joinProgress: LoadingSuccessState = LoadingSuccessState(),
    modifier: Modifier = Modifier,
) {
    val unmet = (access as? GroupAccess.Blocked)?.unmet
    val unmetBalance = unmet as? ChatRuleRequirement.MinimumBalance

    // The caption and the button sit on one panel (node 10125:19197), not loose over the
    // transcript: the transcript behind them is the group's own, blurred, and without a surface to
    // stand on the gate reads as part of it rather than as the thing standing in for the composer.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x2)
            .background(White05, CodeTheme.shapes.medium)
            .padding(horizontal = CodeTheme.dimens.grid.x1)
            .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        // Both rules can be set on one chat, so both are stated. Each line is the group's own
        // standing requirement, read from the rules rather than from the gate: staff see the
        // staff-only line over a Join that works, the same way an eligible viewer already sees the
        // balance line. A group with no rules states nothing above a button that is already open.
        val captions = buildList {
            // The reserve falls to the amount-only line rather than to a named one: "$100 of
            // Dollars" says dollars twice. Same reason the token is still named on the button
            // below — that is a thing to buy, not a restatement of the figure.
            val requirementCurrency = currency?.nameInRequirement
            if (requirement != null) {
                add(
                    if (requirementCurrency != null) {
                        stringResource(
                            R.string.subtitle_chatGate_minimumBalance,
                            requirement.amount.formatted(),
                            requirementCurrency,
                        )
                    } else {
                        stringResource(
                            R.string.subtitle_chatGate_minimumBalance_anyToken,
                            requirement.amount.formatted(),
                        )
                    }
                )
            }
            if (staffOnly) add(stringResource(R.string.subtitle_chatGate_staffOnly))
        }

        captions.forEach { caption ->
            Text(
                text = caption,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (unmetBalance != null) {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_buyMoreToken, currency?.name.orEmpty()),
                // Held until the currency's name resolves, so the label never reads "Buy More ". A
                // rule that names no mint never resolves one and has no token screen to open.
                enabled = currency != null,
                onClick = {
                    unmetBalance.mints.firstOrNull()
                        ?.let { onAction(ChatAction.ViewToken(Mint(it.bytes), returnAfterBuy = true)) }
                },
            )
        } else {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_joinChat),
                // Membership comes back through the roster, not through the join's reply, so the
                // spinner is the only thing that moves while the call is out — and then the
                // checkmark, which the reply does answer.
                isLoading = joinProgress.loading,
                isSuccess = joinProgress.success,
                // A staff rule is not something the user can go and satisfy, so the button says
                // what it would do and refuses to do it, rather than sending a join the server
                // will reject. An undecided gate is not that: it is a fact that has not arrived,
                // and dimming the button until it does makes the button change under the reader a
                // quarter-second after they are looking at it. The server checks the rules on
                // every join anyway, so an early tap gets the same refusal by a slower route.
                enabled = unmet == null && joinProgress.isIdle,
                onClick = { onAction(ChatAction.JoinChat) },
            )
        }
    }
}
