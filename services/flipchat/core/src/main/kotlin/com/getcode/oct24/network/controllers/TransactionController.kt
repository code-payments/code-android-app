package com.getcode.oct24.network.controllers

import com.getcode.model.KinAmount
import xyz.flipchat.services.internal.network.repository.TransactionRepository
import javax.inject.Inject

class TransactionController @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend fun requestAirdrop(): Result<KinAmount> {
        return repository.requestAirdrop()
    }
}