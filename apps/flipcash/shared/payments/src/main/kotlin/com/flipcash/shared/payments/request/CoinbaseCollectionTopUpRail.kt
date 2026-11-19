package com.flipcash.shared.payments.request

import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.CoinbaseOnRampOutcome
import com.flipcash.app.onramp.OnrampOrder
import com.flipcash.app.onramp.OrderDeliveryResult
import com.flipcash.app.tokens.UsdcDepositSweep
import com.getcode.opencode.model.accounts.AccountCluster
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * [CollectionTopUpRail] on Coinbase guest checkout. Google Pay opens in the app-level webview
 * that [CoinbaseOnRampController]'s state drives; the delivered USDC becomes Dollars through
 * [UsdcDepositSweep], as for add money.
 */
internal class CoinbaseCollectionTopUpRail @Inject constructor(
    private val controller: CoinbaseOnRampController,
    private val sweep: UsdcDepositSweep,
) : CollectionTopUpRail {

    override suspend fun order(usdCents: Long): Result<TopUpOrder> {
        controller.checkPurchaseGates().onFailure { return Result.failure(it) }
        return controller.placeTopUpOrder(usdCents).map { TopUpOrder(it.orderId, it.paymentLink) }
    }

    override suspend fun pay(order: TopUpOrder): TopUpPayment = coroutineScope {
        // Subscribed before the webview opens: outcomes don't replay.
        val end = async(start = CoroutineStart.UNDISPATCHED) {
            var authorized = false
            val outcome = controller.outcomes
                .filter { it.orderId == order.orderId }
                .onEach { if (it is CoinbaseOnRampOutcome.Authorized) authorized = true }
                .first { it !is CoinbaseOnRampOutcome.Authorized }
            when (outcome) {
                is CoinbaseOnRampOutcome.Succeeded -> TopUpPayment.Succeeded
                // Once authorized, a failure or a close doesn't say the order won't settle.
                is CoinbaseOnRampOutcome.Failed,
                is CoinbaseOnRampOutcome.Cancelled -> if (authorized) TopUpPayment.Unknown else TopUpPayment.NotCharged
                is CoinbaseOnRampOutcome.Authorized -> TopUpPayment.Unknown
            }
        }
        controller.startTopUpPayment(OnrampOrder(order.orderId, order.paymentLink))
        end.await()
    }

    override suspend fun awaitDelivered(orderId: String): TopUpDelivery =
        when (controller.awaitOrderDelivered(orderId)) {
            is OrderDeliveryResult.Delivered -> TopUpDelivery.Delivered
            OrderDeliveryResult.Failed -> TopUpDelivery.Failed
            OrderDeliveryResult.TimedOut -> TopUpDelivery.TimedOut
        }

    override fun sweep(owner: AccountCluster) = sweep.execute(owner)
}

/** The Coinbase controller lives per activity, so the rail does too. */
@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class CollectionTopUpModule {

    @Binds
    abstract fun bindCollectionTopUpRail(impl: CoinbaseCollectionTopUpRail): CollectionTopUpRail
}
