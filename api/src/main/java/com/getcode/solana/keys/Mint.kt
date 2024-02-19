package com.getcode.solana.keys

import org.kin.sdk.base.tools.Base58

typealias Mint = PublicKey

val Mint.kin: Mint
    get() = PublicKey(Base58.decode("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6").toList())

val Mint.usdc
    get() = Mint(Base58.decode("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v").toList())