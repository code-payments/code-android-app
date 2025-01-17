package xyz.flipchat.chat

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.domain.BillController
import com.getcode.network.BalanceController
import com.getcode.network.repository.PaymentRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.CurrencyUtils
import xyz.flipchat.services.PaymentController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TipController @Inject constructor(
    paymentRepository: PaymentRepository,
    currencyUtils: CurrencyUtils,
    private val resources: ResourceHelper,
    private val billController: BillController,
    private val balanceController: BalanceController,
    private val roomController: RoomController,
) : PaymentController(
    paymentRepository, resources, billController, balanceController, currencyUtils
) {

}