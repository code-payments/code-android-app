package com.flipcash.app.menu.internal

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UsernameGateTest {

    private fun usd(amount: Double) = Fiat(fiat = amount, currencyCode = CurrencyCode.USD)

    @Test
    fun `a claimed handle spends the nudge, whatever the balance`() {
        assertEquals(
            UsernameGate.Claimed,
            usernameGate(username = "mcansh", minimum = usd(25.0), balance = Fiat.Zero),
        )
    }

    @Test
    fun `a blank handle counts as unclaimed`() {
        assertIs<UsernameGate.Locked>(
            usernameGate(username = "   ", minimum = usd(25.0), balance = Fiat.Zero),
        )
    }

    @Test
    fun `a zero minimum fails open`() {
        assertEquals(
            UsernameGate.Unlocked,
            usernameGate(username = null, minimum = Fiat.Zero, balance = Fiat.Zero),
        )
    }

    @Test
    fun `exactly the minimum unlocks`() {
        assertEquals(
            UsernameGate.Unlocked,
            usernameGate(username = null, minimum = usd(25.0), balance = usd(25.0)),
        )
    }

    @Test
    fun `above the minimum unlocks`() {
        assertEquals(
            UsernameGate.Unlocked,
            usernameGate(username = null, minimum = usd(25.0), balance = usd(25.01)),
        )
    }

    @Test
    fun `below the minimum reports the shortfall and how far along it is`() {
        val gate = usernameGate(username = null, minimum = usd(25.0), balance = usd(20.0))

        assertIs<UsernameGate.Locked>(gate)
        assertEquals(usd(25.0), gate.minimum)
        assertEquals(usd(5.0), gate.shortfall)
        assertEquals(0.8f, gate.fraction)
    }

    @Test
    fun `an empty balance is zero progress, not a divide by zero`() {
        val gate = usernameGate(username = null, minimum = usd(25.0), balance = Fiat.Zero)

        assertIs<UsernameGate.Locked>(gate)
        assertEquals(usd(25.0), gate.shortfall)
        assertEquals(0f, gate.fraction)
    }

    @Test
    fun `a negative balance clamps to zero progress rather than a backwards bar`() {
        val gate = usernameGate(username = null, minimum = usd(25.0), balance = usd(-5.0))

        assertIs<UsernameGate.Locked>(gate)
        assertEquals(0f, gate.fraction)
    }
}
