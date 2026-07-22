package com.flipcash.app.core.bill

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScannableTest {

    private fun tokenWith(mint: Mint): Token = MintMetadata(
        address = mint,
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

    private fun localFiat(): LocalFiat = LocalFiat(
        underlyingTokenAmount = Fiat(5.0),
        nativeAmount = Fiat(5.0),
        rate = Rate.oneToOne,
        mint = Mint.usdf,
    )

    private val nonUsdfMint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v") // USDC — not USDF

    @Test
    fun `forToken returns GoldBar for usdf mint`() {
        val bill = Scannable.Payable.forToken(token = tokenWith(Mint.usdf), amount = localFiat())
        assertIs<Scannable.GoldBar>(bill)
    }

    @Test
    fun `forToken returns CashBill for non-usdf mint`() {
        val bill = Scannable.Payable.forToken(token = tokenWith(nonUsdfMint), amount = localFiat())
        assertIs<Scannable.CashBill>(bill)
    }

    @Test
    fun `forToken preserves passed-through fields`() {
        val bill = Scannable.Payable.forToken(
            token = tokenWith(nonUsdfMint),
            amount = localFiat(),
            didReceive = true,
            kind = Scannable.Payable.Kind.airdrop,
        )
        assertTrue(bill.didReceive)
        assertEquals(Scannable.Payable.Kind.airdrop, bill.kind)
    }

    @Test
    fun `stamped replaces code and nonce on CashBill and keeps its type`() {
        val bill = Scannable.CashBill(token = tokenWith(nonUsdfMint), amount = localFiat())
        val code = listOf<Byte>(1, 2, 3)
        val nonce = listOf<Byte>(9, 8)
        val stamped = bill.stamped(code = code, nonce = nonce)
        assertIs<Scannable.CashBill>(stamped)
        assertEquals(code, stamped.data)
        assertEquals(nonce, stamped.nonce)
        assertEquals(bill.token, stamped.token)
    }

    @Test
    fun `stamped keeps GoldBar type`() {
        val bar = Scannable.GoldBar(token = tokenWith(Mint.usdf), amount = localFiat())
        val stamped = bar.stamped(code = listOf(7), nonce = emptyList())
        assertIs<Scannable.GoldBar>(stamped)
        assertEquals(listOf<Byte>(7), stamped.data)
    }
}
