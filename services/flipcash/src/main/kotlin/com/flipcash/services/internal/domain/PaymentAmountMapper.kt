package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.common.v1.Common
import com.flipcash.services.internal.model.common.PaymentAmount
import com.codeinc.flipcash.gen.common.v1.Common.PaymentAmount as RpcPaymentAmount
import com.getcode.opencode.internal.domain.mapper.Mapper
import javax.inject.Inject

internal class PaymentAmountMapper @Inject constructor(): Mapper<RpcPaymentAmount, PaymentAmount> {
    override fun map(from: Common.PaymentAmount): PaymentAmount {
        return PaymentAmount(
            currency = from.currency,
            nativeAmount = from.nativeAmount,
            quarks = from.quarks
        )
    }
}