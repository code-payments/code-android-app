package xyz.flipchat.services.internal.network.repository

import com.getcode.model.KinAmount
import com.getcode.services.model.EcdsaTuple
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.internal.data.mapper.KinMapper
import xyz.flipchat.services.internal.network.service.TransactionService
import javax.inject.Inject

class RealTransactionRepository @Inject constructor(
    private val storedEcda: () -> EcdsaTuple,
    private val service: TransactionService,
    private val kinMapper: KinMapper
) : TransactionRepository {

    override suspend fun requestAirdrop(): Result<KinAmount> {
        val owner = storedEcda().algorithm ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.requestAirdrop(owner)
            .map { kinMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }
}