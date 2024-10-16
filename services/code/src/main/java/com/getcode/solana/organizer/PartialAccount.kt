package com.getcode.solana.organizer

import com.getcode.model.Kin

data class PartialAccount(val cluster: Lazy<AccountCluster>, var partialBalance: Kin = Kin.fromKin(0)) {
    fun getCluster() = cluster.value
}