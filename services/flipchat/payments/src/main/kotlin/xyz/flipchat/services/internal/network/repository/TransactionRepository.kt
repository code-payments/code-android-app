package xyz.flipchat.services.internal.network.repository

import com.getcode.model.KinAmount

interface TransactionRepository {
    suspend fun requestAirdrop(): Result<KinAmount>
}