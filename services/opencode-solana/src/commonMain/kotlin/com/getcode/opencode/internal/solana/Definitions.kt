package com.getcode.opencode.internal.solana

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

@InternalOpenCodeApi public val subsidizer = PublicKey(Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR").toList())
@InternalOpenCodeApi public val vmAuthority = Mint(Base58.decode("cash11ndAmdKFEnG2wrQQ5Zqvr1kN9htxxLyoPLYFUV").toList())