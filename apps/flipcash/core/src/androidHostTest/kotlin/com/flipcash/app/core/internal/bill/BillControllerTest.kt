package com.flipcash.app.core.internal.bill

import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.BillToast
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.getcode.opencode.managers.BillTransactionManager
import com.getcode.opencode.model.financial.Fiat
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillControllerTest {

    private val transactionManager: BillTransactionManager = mockk(relaxed = true)
    private val userFlags: UserFlagsCoordinator = mockk(relaxed = true)

    private val controller = BillController(
        transactionManager = transactionManager,
        userFlags = userFlags,
    )

    // region initial state

    @Test
    fun `initial state is Default`() {
        val state = controller.state.value

        assertNull(state.bill)
        assertFalse(state.showToast)
        assertNull(state.toast)
        assertNull(state.valuation)
        assertNull(state.primaryAction)
        assertNull(state.secondaryAction)
    }

    // endregion

    // region update

    @Test
    fun `update modifies state via function`() {
        val toast = BillToast(amount = Fiat(1.0), isDeposit = true)

        controller.update { it.copy(showToast = true, toast = toast) }

        val state = controller.state.value
        assertTrue(state.showToast)
        assertEquals(toast, state.toast)
    }

    // endregion

    // region reset

    @Test
    fun `reset sets state to Default preserving toast`() {
        val toast = BillToast(amount = Fiat(2.0), isDeposit = false)
        controller.update { it.copy(showToast = true, toast = toast) }

        controller.reset()

        val state = controller.state.value
        assertNull(state.bill)
        assertFalse(state.showToast)
        assertEquals(toast, state.toast)
        assertNull(state.valuation)
    }

    @Test
    fun `reset calls transactionManager reset`() {
        controller.reset()

        verify(exactly = 1) { transactionManager.reset() }
    }

    // endregion
}
