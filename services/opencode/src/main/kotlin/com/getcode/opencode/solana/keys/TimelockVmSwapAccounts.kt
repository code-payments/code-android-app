package com.getcode.opencode.solana.keys

data class TimelockVmSwapAccounts(
    val pda: ProgramDerivedAccount,
    val ata: ProgramDerivedAccount,
) {
    companion object
}
