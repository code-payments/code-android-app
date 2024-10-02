package com.getcode.solana.instructions.programs

import com.getcode.network.repository.decodeBase58
import com.getcode.solana.keys.PublicKey

enum class SysVar(val value: String) {
    clock             ("SysvarC1ock11111111111111111111111111111111"),
    epochSchedule     ("SysvarEpochSchedu1e111111111111111111111111"),
    fees              ("SysvarFees111111111111111111111111111111111"),
    instructions      ("Sysvar1nstructions1111111111111111111111111"),
    recentBlockhashes ("SysvarRecentB1ockHashes11111111111111111111"),
    rent              ("SysvarRent111111111111111111111111111111111"),
    slotHashes        ("SysvarS1otHashes111111111111111111111111111"),
    slotHistory       ("SysvarS1otHistory11111111111111111111111111"),
    stackHistory      ("SysvarStakeHistory1111111111111111111111111");

    fun address(): PublicKey = PublicKey(this.value.decodeBase58().toList())
}