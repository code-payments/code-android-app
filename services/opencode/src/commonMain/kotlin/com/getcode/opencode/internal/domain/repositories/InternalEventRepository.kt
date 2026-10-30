package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.events.Events
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.repositories.EventRepository
import com.hoc081098.channeleventbus.ChannelEvent
import com.hoc081098.channeleventbus.ChannelEventBus
import com.hoc081098.channeleventbus.ChannelEventBusException
import com.hoc081098.channeleventbus.ChannelEventKey
import com.hoc081098.channeleventbus.ValidationBeforeClosing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class InternalEventRepository @Inject constructor(
    eventBus: ChannelEventBus,
    private val accountController: AccountController,
    private val transactionController: TransactionController,
    private val sessionListeners: Set<@JvmSuppressWildcards SessionListener>,
): EventRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        eventBus.handle(Events.FetchBalance) {
            scope.launch {
                notifyListeners { it.onBalanceUpdateRequested() }
            }
        }

        eventBus.handle(Events.OnLoggedIn) {
            scope.launch {
                // Before the fan-out, and not part of it: the listeners' own work reads the
                // account list this seeds.
                accountController.onUserLoggedIn(it.owner)
                notifyListeners { listener -> listener.onUserLoggedIn(it.owner) }
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

    /**
     * Delivers a session event to every listener at once.
     *
     * The listeners are an unordered multibinding of independent subsystems — tokens, chat,
     * contacts — so there is no order to preserve, and each one's first act on login is to talk to
     * Room or the network. Delivering them from a plain loop ran them strictly in sequence, which
     * on the login path made the last listener wait out the other two before it could even start.
     * The [coroutineScope] keeps the old completion semantics: this returns when they all have.
     */
    private suspend fun notifyListeners(block: suspend (SessionListener) -> Unit) = coroutineScope {
        sessionListeners.forEach { listener ->
            launch { block(listener) }
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