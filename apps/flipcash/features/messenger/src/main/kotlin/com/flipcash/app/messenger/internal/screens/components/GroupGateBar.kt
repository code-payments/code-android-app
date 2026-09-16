package com.flipcash.app.messenger.internal.screens.components

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
import com.flipcash.app.messenger.internal.GroupAccess
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeButton

/**
 * What stands where the composer does for someone who is not in the group.
 *
 * Node 10125:19153. The caption states the bar and the button is the one thing that clears it, so
 * both come from the same [GroupAccess.Blocked.unmet] the transcript's blur is decided by — there is
 * no second evaluation here that could disagree with it.
 */
@Composable
internal fun GroupGateBar(
    access: GroupAccess,
    ticker: String?,
    modifier: Modifier = Modifier,
) {
    val onAction = LocalChatActionHandler.current
    val unmet = (access as? GroupAccess.Blocked)?.unmet
    val balance = unmet as? ChatRuleRequirement.MinimumBalance

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        val caption = when {
            balance != null && ticker != null -> stringResource(
                R.string.subtitle_chatGate_minimumBalance,
                balance.amount.formatted(),
                ticker,
            )
            balance != null -> stringResource(
                R.string.subtitle_chatGate_minimumBalance_anyToken,
                balance.amount.formatted(),
            )
            unmet == ChatRuleRequirement.Staff ->
                stringResource(R.string.subtitle_chatGate_staffOnly)
            // Eligible, and Membered for the frame between a successful join and the membership
            // flag arriving back through observeMetadata.
            else -> null
        }

        if (caption != null) {
            Text(
                text = caption,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (balance != null) {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_buyMoreToken, ticker.orEmpty()),
                // Nothing to buy until the symbol resolves: the label would read "Buy More $" and
                // the token screen has no mint to open.
                enabled = ticker != null,
                onClick = {
                    balance.mints.firstOrNull()
                        ?.let { onAction(ChatAction.ViewToken(Mint(it.bytes))) }
                },
            )
        } else {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_joinChat),
                // A staff rule is not something the user can go and satisfy, so the button says
                // what it would do and refuses to do it, rather than sending a join the server
                // will reject.
                enabled = unmet == null,
                onClick = { onAction(ChatAction.JoinChat) },
            )
        }
    }
}
