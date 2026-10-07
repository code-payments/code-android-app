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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.app.messenger.internal.RuleCurrency
import com.flipcash.app.messenger.internal.screens.profile.GateFunding
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.theme.CodeButton
import com.getcode.view.LoadingSuccessState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

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
    /** What the viewer is short of the unmet balance rule, for the buy button; null when it can't be stated. */
    shortfall: Fiat? = null,
    /** Where Buy goes: the buy screen, or Add Money when nothing held can pay for it. */
    funding: GateFunding = GateFunding.Buy,
    /** The transcript behind the panel, which the panel frosts. Null draws it flat. */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val unmet = (access as? GroupAccess.Blocked)?.unmet
    val unmetBalance = unmet as? ChatRuleRequirement.MinimumBalance

    GatePanel(modifier, hazeState) {
        // Both rules can be set on one chat, so both are stated. Each line is the group's own
        // standing requirement, read from the rules rather than from the gate: staff see the
        // staff-only line over a Join that works, the same way an eligible viewer already sees the
        // balance line. A group with no rules states nothing above a button that is already open.
        val captions = buildList {
            if (requirement != null) {
                add(
                    stringResource(
                        R.string.label_groupRequiredToJoin,
                        ruleAmountLabel(requirement.amount, currency?.nameInRequirement),
                    )
                )
            }
            if (staffOnly) add(stringResource(R.string.subtitle_chatGate_staffOnly))
        }
        captions.forEach { GateCaption(it) }

        if (unmetBalance != null) {
            BuyButton(
                rule = unmetBalance,
                shortfall = shortfall,
                currency = currency,
                toJoin = true,
                funding = funding,
                onAction = onAction,
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

/**
 * What stands where the composer does for a member below the group's minimum to chat: the
 * requirement, and a button to buy what they are short of it (Figma "Buy $90.00 of NYC to Chat").
 */
@Composable
internal fun ChatMinimumGateBar(
    requirement: ChatRuleRequirement.MinimumBalance,
    /** The token the rule names, when it is the one [requirement] is about. */
    currency: RuleCurrency?,
    /** What the viewer is short of [requirement]; null when it can't be stated. */
    shortfall: Fiat?,
    onAction: ChatActionHandler,
    funding: GateFunding = GateFunding.Buy,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    GatePanel(modifier, hazeState) {
        GateCaption(
            stringResource(
                R.string.label_groupRequiredToChat,
                ruleAmountLabel(requirement.amount, currency?.nameInRequirement),
            )
        )
        BuyButton(
            rule = requirement,
            shortfall = shortfall,
            currency = currency,
            toJoin = false,
            funding = funding,
            onAction = onAction,
        )
    }
}

// The caption and the button sit on one panel (node 10125:19197), not loose over the transcript:
// the transcript behind them is the group's own, blurred, and without a surface to stand on the
// gate reads as part of it rather than as the thing standing in for the composer.
@Composable
private fun GatePanel(modifier: Modifier, hazeState: HazeState?, content: @Composable () -> Unit) {
    val shape = CodeTheme.shapes.medium
    // Frosted, as iOS's gate card is: the transcript blurred behind it, the screen's background
    // over that at 30%, then the row tint, so it stays a little see-through.
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x2)
            .clip(shape)
            .then(if (hazeState != null) Modifier.hazeBlur(HazeInput.Sources(hazeState), material) else Modifier)
            .background(CodeTheme.colors.background.copy(alpha = 0.3f))
            .background(White05)
            .padding(horizontal = CodeTheme.dimens.grid.x1)
            .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        content()
    }
}

@Composable
private fun GateCaption(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

/**
 * "Buy $3.67 of NYC to Join": what the viewer is short of [rule], not the rule's whole amount
 * (Figma Iteration 10 "Access action", as iOS's ConversationGatePanel). A rule any holding
 * satisfies, or one on the reserve, has no other token to buy, so it adds cash: "Add $3.67 to
 * Join". With no rate to state the shortfall, it falls back to "Buy More NYC" or "Add Cash".
 * Always enabled.
 *
 * [funding] sends a buy nothing held can pay for straight to Add Money rather than to a buy
 * screen that would only offer Add Money one step later.
 */
@Composable
private fun BuyButton(
    rule: ChatRuleRequirement.MinimumBalance,
    shortfall: Fiat?,
    currency: RuleCurrency?,
    toJoin: Boolean,
    funding: GateFunding,
    onAction: ChatActionHandler,
) {
    val mint = rule.mints.firstOrNull()?.let { Mint(it.bytes) }
    val tokenToBuy = mint?.takeUnless { it == Mint.usdf }
    val name = currency?.nameInRequirement
    val text = when {
        tokenToBuy == null && shortfall != null -> stringResource(
            if (toJoin) R.string.action_addToJoin else R.string.action_addToChat,
            ruleAmountLabel(shortfall, null),
        )
        tokenToBuy == null -> stringResource(R.string.action_addCash)
        shortfall != null -> stringResource(
            if (toJoin) R.string.action_buyToJoin else R.string.action_buyToChat,
            ruleAmountLabel(shortfall, name),
        )
        name != null -> stringResource(R.string.action_buyMoreToken, name)
        else -> stringResource(R.string.action_buyMore)
    }
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        onClick = {
            onAction(
                if (tokenToBuy == null || funding == GateFunding.AddMoney) ChatAction.AddCash
                else ChatAction.BuyGateToken(tokenToBuy)
            )
        },
    )
}
