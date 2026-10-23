package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.solana.keys.TimelockVmSwapAccounts
import com.getcode.solana.keys.PublicKey

fun Token.timelockSwapAccounts(owner: PublicKey): TimelockVmSwapAccounts = TimelockVmSwapAccounts.newInstance(owner, this)