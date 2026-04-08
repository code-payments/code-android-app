package com.flipcash.app.payments.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InternalPaymentControllerTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resources: ResourceHelper = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createController(): InternalPaymentController {
        return InternalPaymentController(
            resources = resources,
            userManager = userManager,
        )
    }

    // --- StateFlow.update tests ---

    @Test
    fun `cancelRequest resets state to Default`() = runTest {
        val controller = createController()

        // cancelRequest calls _state.update { PaymentState.Default } regardless of current state
        controller.cancelRequest(fromUser = false)
        advanceUntilIdle()

        assertEquals(PaymentState.Default, controller.state.value)
    }

    @Test
    fun `initial state is Default`() = runTest {
        val controller = createController()
        assertEquals(PaymentState.Default, controller.state.value)
    }

    // --- SharedFlow.emit tests ---

    @Test
    fun `cancelRequest from user emits OnPaymentCancelled`() = runTest {
        val controller = createController()

        controller.paymentEvents.test {
            controller.cancelRequest(fromUser = true)
            val event = awaitItem()
            assertIs<PaymentEvent.OnPaymentCancelled>(event)
        }
    }

    // --- handlePaymentError tests (private method via reflection) ---

    @Test
    fun `handlePaymentError emits error event and resets state`() = runTest {
        val controller = createController()

        val events = mutableListOf<PaymentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.paymentEvents.collect { events.add(it) }
        }

        val error = RuntimeException("test error")
        invokeHandlePaymentError(controller, error)
        advanceUntilIdle()

        assertEquals(PaymentState.Default, controller.state.value)
        val errorEvent = events.filterIsInstance<PaymentEvent.OnPaymentError>().firstOrNull()
        assertIs<PaymentEvent.OnPaymentError>(errorEvent)
        assertEquals(error, errorEvent.error)
    }

    @Test
    fun `handlePaymentError with InsufficientBalance does not show bottom bar`() = runTest {
        val controller = createController()

        val events = mutableListOf<PaymentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.paymentEvents.collect { events.add(it) }
        }

        val error = PaymentError.InsufficientBalance()
        invokeHandlePaymentError(controller, error)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.isEmpty())
        assertTrue(events.any { it is PaymentEvent.OnPaymentError })
        assertEquals(PaymentState.Default, controller.state.value)
    }

    @Test
    fun `handlePaymentError with NoOwnerForDistribution shows error`() = runTest {
        every { resources.getString(any()) } returns "error_message"
        val controller = createController()

        val events = mutableListOf<PaymentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.paymentEvents.collect { events.add(it) }
        }

        val error = PaymentError.NoOwnerForDistribution()
        invokeHandlePaymentError(controller, error)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.isNotEmpty())
        assertTrue(events.any { it is PaymentEvent.OnPaymentError })
        assertEquals(PaymentState.Default, controller.state.value)
    }

    private suspend fun invokeHandlePaymentError(
        controller: InternalPaymentController,
        error: Throwable
    ) {
        val function = controller::class.declaredMemberFunctions
            .first { it.name == "handlePaymentError" }
        function.isAccessible = true
        function.callSuspend(controller, error)
    }
}
