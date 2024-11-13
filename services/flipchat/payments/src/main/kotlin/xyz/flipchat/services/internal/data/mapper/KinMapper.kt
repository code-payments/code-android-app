package xyz.flipchat.services.internal.data.mapper

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.KinAmount
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.internal.network.protomapping.fromProtoExchangeData
import javax.inject.Inject

class KinMapper @Inject constructor(): Mapper<TransactionService.ExchangeData, KinAmount> {
    override fun map(from: TransactionService.ExchangeData): KinAmount {
        return KinAmount.fromProtoExchangeData(from)
    }
}