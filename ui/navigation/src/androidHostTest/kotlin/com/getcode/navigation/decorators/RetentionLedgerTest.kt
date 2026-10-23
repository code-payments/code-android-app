package com.getcode.navigation.decorators

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetentionLedgerTest {

    private val tabs = setOf("Wallet", "Chats", "TipCard", "Scanner")

    private fun ledger() = RetentionLedger { it in tabs }

    @Test
    fun `a retained key survives its pop`() {
        assertTrue(ledger().onPopped("Wallet"))
    }

    @Test
    fun `an unretained key does not`() {
        assertFalse(ledger().onPopped("CurrencyInfo(usdc)"))
    }

    @Test
    fun `release drops everything held`() {
        val ledger = ledger()
        ledger.onPopped("Wallet")
        ledger.onPopped("Scanner")
        ledger.onPopped("CurrencyInfo(usdc)")

        assertEquals(listOf("Wallet", "Scanner"), ledger.release())
    }

    @Test
    fun `release drops each key once`() {
        val ledger = ledger()
        ledger.onPopped("Wallet")
        ledger.release()

        assertEquals(emptyList(), ledger.release())
    }

    @Test
    fun `switching between the same two tabs holds one entry each`() {
        val ledger = ledger()
        repeat(3) {
            ledger.onPopped("Wallet")
            ledger.onPopped("Scanner")
        }

        assertEquals(listOf("Wallet", "Scanner"), ledger.release())
    }

    // A pop is reported only once the entry's content has left composition, so on logout the pops of
    // the tab homes land after the back stack has already stopped containing any. Retaining them then
    // would leave the signed-out account's ViewModels waiting for whoever signs in next.
    @Test
    fun `a pop arriving after release is not retained`() {
        val ledger = ledger()
        ledger.release()

        assertFalse(ledger.onPopped("Wallet"))
    }

    @Test
    fun `a pop arriving after release is not held for a later release`() {
        val ledger = ledger()
        ledger.release()
        ledger.onPopped("Wallet")

        assertEquals(emptyList(), ledger.release())
    }

    @Test
    fun `rendering a retained key resumes retention`() {
        val ledger = ledger()
        ledger.release()
        ledger.onRendered("Wallet")

        assertTrue(ledger.onPopped("Wallet"))
    }

    @Test
    fun `rendering an unretained key does not resume retention`() {
        val ledger = ledger()
        ledger.release()
        ledger.onRendered("CurrencyInfo(usdc)")

        assertFalse(ledger.onPopped("Wallet"))
    }
}
