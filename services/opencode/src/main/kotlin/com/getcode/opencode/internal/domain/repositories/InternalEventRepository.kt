package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.events.Events
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.repositories.EventRepository
import com.hoc081098.channeleventbus.ChannelEvent
import com.hoc081098.channeleventbus.ChannelEventBus
import com.hoc081098.channeleventbus.ChannelEventBusException
import com.hoc081098.channeleventbus.ChannelEventKey
import com.hoc081098.channeleventbus.ValidationBeforeClosing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class InternalEventRepository @Inject constructor(
    eventBus: ChannelEventBus,
    private val balanceController: BalanceController,
    private val transactionController: TransactionController,
): EventRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        eventBus.handle(Events.FetchBalance) {
            scope.launch {
                balanceController.fetchBalance()
            }
        }

        eventBus.handle(Events.OnLoggedIn) {
            scope.launch {
                balanceController.onUserLoggedIn(it.owner)
            }
        }

        eventBus.handle(Events.RequestFirstAirdrop) {
            scope.launch {
                transactionController.airdrop(
                    type = AirdropType.GetFirstCrypto,
                    destination = it.owner.authority.keyPair
                )
            }
        }

        eventBus.handle(Events.UpdateLimits) {
            scope.launch {
                transactionController.updateLimits(
                    owner = it.owner,
                    force = it.force
                )
            }
        }
    }

    private fun <T : ChannelEvent<T>> ChannelEventBus.handle(
        key: ChannelEventKey<T>,
        onEvent: (T) -> Unit
    ) {
        try {
            closeKey(key, setOf(ValidationBeforeClosing.REQUIRE_BUS_IS_EXISTING))
        } catch (e: ChannelEventBusException.CloseException.BusDoesNotExist) {
            //This is an expected exception that will occur first time running the code
        }

        receiveAsFlow(key)
            .onEach {
                onEvent(it)
            }
            .launchIn(scope)
    }
}