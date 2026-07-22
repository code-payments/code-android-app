package com.flipcash.app.core.bill

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BillStateTest {

    private fun testToken(): Token = MintMetadata(
        address = Mint.usdf,
        decimals = 6,
        name = "Test",
        symbol = "TST",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = PublicKey.fromBase58("11111111111111111111111111111111"),
            authority = PublicKey.fromBase58("11111111111111111111111111111111"),
            lockDurationInDays = 21
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private fun testLocalFiat(): LocalFiat = LocalFiat(
        underlyingTokenAmount = Fiat(5.0),
        nativeAmount = Fiat(5.0),
        rate = Rate.oneToOne,
        mint = Mint.usdf,
    )

    // region Default state

    @Test
    fun `Default BillState has null bill and no toast`() {
        val state = BillState.Default

        assertNull(state.bill)
        assertFalse(state.showToast)
        assertNull(state.toast)
        assertNull(state.valuation)
        assertNull(state.primaryAction)
        assertNull(state.secondaryAction)
    }

    // endregion

    // region canSwipeToDismiss

    @Test
    fun `canSwipeToDismiss returns true when bill allows gestures`() {
        val bill = Scannable.CashBill(
            token = testToken(),
            amount = testLocalFiat(),
            disableGestures = false,
        )
        val state = BillState.Default.copy(bill = bill)

        assertTrue(state.canSwipeToDismiss)
    }

    @Test
    fun `canSwipeToDismiss returns false when bill disableGestures is true`() {
        val bill = Scannable.CashBill(
            token = testToken(),
            amount = testLocalFiat(),
            disableGestures = true,
        )
        val state = BillState.Default.copy(bill = bill)

        assertFalse(state.canSwipeToDismiss)
    }

    @Test
    fun `canSwipeToDismiss returns false when bill is null`() {
        val state = BillState.Default

        assertFalse(state.canSwipeToDismiss)
    }

    // endregion

    // region confirmationDelayMillis

    @Test
    fun `confirmationDelayMillis converts Duration correctly`() {
        val bill = Scannable.CashBill(
            token = testToken(),
            amount = testLocalFiat(),
            confirmationDelay = 3.seconds,
        )
        val state = BillState.Default.copy(bill = bill)

        assertEquals(3000, state.confirmationDelayMillis)
    }

    @Test
    fun `confirmationDelayMillis returns 0 for null bill`() {
        val state = BillState.Default

        assertEquals(0, state.confirmationDelayMillis)
    }

    // endregion

    // region Scannable.CashBill metadata and canFlip

    @Test
    fun `Bill Cash metadata extracts token and amount`() {
        val token = testToken()
        val amount = testLocalFiat()
        val data = listOf<Byte>(1, 2, 3)

        val bill = Scannable.CashBill(
            token = token,
            amount = amount,
            data = data,
        )

        val metadata = bill.metadata
        assertEquals(token, metadata.token)
        assertEquals(amount, metadata.amount)
        assertEquals(data, metadata.data)
        assertNull(metadata.request)
    }

    @Test
    fun `Bill Cash canFlip is always false`() {
        val bill = Scannable.CashBill(
            token = testToken(),
            amount = testLocalFiat(),
        )

        assertFalse(bill.canFlip)
    }

    // endregion
}
