package com.flipcash.app.core.tokens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper

/**
 * Whether this token is the reserve — the one holding the app counts as plain dollars.
 *
 * Worth a name because the reserve is written differently wherever it is named, and the mint
 * comparison on its own does not say why a screen is asking.
 */
val Token.isReserve: Boolean
    get() = address == Mint.usdf

/**
 * What the user calls this token.
 *
 * The reserve is branded Dollars everywhere they meet it; [Token.name] off the wire is "USDF", which
 * is the mint, not the thing they hold. Every other token is called what it is named — the name, not
 * the ticker, which is what the balance list and the currency picker show.
 */
fun Token.brandedName(resources: ResourceHelper): String = when {
    isReserve -> resources.getString(R.string.displayName_dollars)
    else -> name
}

/** [brandedName] where a composable has the resources to hand rather than an injected [ResourceHelper]. */
@Composable
fun Token.brandedName(): String = when {
    isReserve -> stringResource(R.string.displayName_dollars)
    else -> name
}
