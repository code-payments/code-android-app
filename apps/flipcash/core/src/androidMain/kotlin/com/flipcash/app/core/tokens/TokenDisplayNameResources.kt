package com.flipcash.app.core.tokens

// kmp-audit: ResourceHelper.getString takes an Android @StringRes id


import com.flipcash.core.R
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper

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
