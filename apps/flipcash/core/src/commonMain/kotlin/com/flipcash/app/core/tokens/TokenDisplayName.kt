package com.flipcash.app.core.tokens

import androidx.compose.runtime.Composable
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.displayName_dollars
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import org.jetbrains.compose.resources.stringResource

/**
 * Whether this token is the reserve — the one holding the app counts as plain dollars.
 *
 * Worth a name because the reserve is written differently wherever it is named, and the mint
 * comparison on its own does not say why a screen is asking.
 */
val Token.isReserve: Boolean
    get() = address == Mint.usdf

/** [brandedName] where a composable has the resources to hand. */
@Composable
fun Token.brandedName(): String = when {
    isReserve -> stringResource(Res.string.displayName_dollars)
    else -> name
}
