package com.flipcash.libs.currency.math.internal

internal const val DefaultMintDecimals       = 10
internal const val DefaultMintMaxTokenSupply = 21_000_000 // 21mm tokens
internal const val DefaultMintQuarksPerUnit  = 10_000_000_000
internal const val DefaultMintMaxQuarkSupply = DefaultMintMaxTokenSupply * DefaultMintQuarksPerUnit // 21mm tokens with 10 decimals