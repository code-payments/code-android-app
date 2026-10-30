package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntentMultiTransferTest {

    private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })

    private val mint = Mint.usdf
    private val sourceVault = key(1)
    private val sourceAuthority = key(2)

    private val source: AccountCluster = mockk {
        val pair = mockk<Ed25519.KeyPair> {
            every { publicKeyBytes } returns sourceAuthority.byteArray
        }
        val derived = mockk<DerivedKey> { every { keyPair } returns pair }
        every { authority } returns derived
        every { vaultPublicKey } returns sourceVault
        every { authorityPublicKey } returns sourceAuthority
    }

    private fun verifiedState(currency: String = "cad") = VerifiedState(
        rateProto = verifiedCoreMintFiatExchangeRate {
            exchangeRate = coreMintFiatExchangeRate {
                currencyCode = currency
                exchangeRate = 1.5
            }
        },
        reserveProto = null,
    )

    private fun fiat(
        quarks: Long,
        currency: CurrencyCode = CurrencyCode.CAD,
        mint: Mint = this.mint,
    ) = LocalFiat(
        underlyingTokenAmount = Fiat(quarks = quarks, currencyCode = CurrencyCode.USD),
        nativeAmount = Fiat(fiat = quarks * 1.5 / 1_000_000, currencyCode = currency),
        rate = Rate(fx = 1.5, currency = currency),
        mint = mint,
    )

    private fun share(
        n: Int,
        quarks: Long = 1_000_000L * n,
        destination: PublicKey = key(10 + n),
        owner: PublicKey = key(100 + n),
        amount: LocalFiat = fiat(quarks),
    ) = IntentMultiTransfer.Share(destination, owner, amount)

    private fun shares(count: Int) = (1..count).map { share(it) }

    private fun create(
        shares: List<IntentMultiTransfer.Share> = shares(3),
        appMetadata: ByteArray? = null,
        state: VerifiedState = verifiedState(),
    ) = IntentMultiTransfer.create(
        shares = shares,
        mint = mint,
        sourceCluster = source,
        verifiedState = state,
        appMetadata = appMetadata,
    )

    @Test
    fun `emits one no privacy transfer per share in input order`() {
        val input = shares(3)
        val intent = create(input)

        val actions = intent.actionGroup.actions
        assertEquals(3, actions.size)
        actions.forEachIndexed { index, action ->
            assertEquals(index, action.id)
            val proto = action.action()
            assertTrue(proto.hasNoPrivacyTransfer())
            val transfer = proto.noPrivacyTransfer
            assertContentEquals(sourceVault.byteArray, transfer.source.value.toByteArray())
            assertContentEquals(sourceAuthority.byteArray, transfer.authority.value.toByteArray())
            assertContentEquals(input[index].destination.byteArray, transfer.destination.value.toByteArray())
            assertEquals(input[index].amount.underlyingTokenAmount.quarks, transfer.amount)
            assertContentEquals(mint.byteArray, transfer.mint.value.toByteArray())
            assertIs<ActionPublicTransfer>(action)
        }
    }

    @Test
    fun `metadata carries source mint and per payment owner and exchange data`() {
        val input = shares(3)
        val metadata = create(input).metadata().sendPublicMultiPayment

        assertContentEquals(sourceVault.byteArray, metadata.source.value.toByteArray())
        assertContentEquals(mint.byteArray, metadata.mint.value.toByteArray())
        assertEquals(3, metadata.paymentsCount)
        metadata.paymentsList.forEachIndexed { index, payment ->
            assertContentEquals(input[index].destination.byteArray, payment.destination.value.toByteArray())
            assertTrue(payment.hasDestinationOwner())
            assertContentEquals(input[index].destinationOwner.byteArray, payment.destinationOwner.value.toByteArray())
            assertTrue(payment.hasClientExchangeData())
            assertEquals(input[index].amount.underlyingTokenAmount.quarks, payment.clientExchangeData.quarks)
        }
    }

    @Test
    fun `app metadata is set when given`() {
        val bytes = byteArrayOf(1, 2, 3)
        val proto = create(appMetadata = bytes).metadata()

        assertTrue(proto.hasAppMetadata())
        assertContentEquals(bytes, proto.appMetadata.value.toByteArray())
    }

    @Test
    fun `app metadata is absent when not given`() {
        assertFalse(create().metadata().hasAppMetadata())
    }

    @Test
    fun `rejects fewer than the minimum payments`() {
        assertFailsWith<IllegalArgumentException> { create(shares(1)) }
    }

    @Test
    fun `accepts the maximum payments and rejects more`() {
        create(shares(IntentMultiTransfer.MAX_PAYMENTS))
        assertFailsWith<IllegalArgumentException> { create(shares(IntentMultiTransfer.MAX_PAYMENTS + 1)) }
    }

    @Test
    fun `rejects duplicate destinations`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, destination = key(11))))
        }
    }

    @Test
    fun `rejects duplicate owners`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, owner = key(101))))
        }
    }

    @Test
    fun `rejects destination equal to source vault`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, destination = sourceVault)))
        }
    }

    @Test
    fun `rejects owner equal to source authority`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, owner = sourceAuthority)))
        }
    }

    @Test
    fun `rejects a share in a different mint`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, amount = fiat(2_000_000, mint = Mint.usdc))))
        }
    }

    @Test
    fun `rejects shares in different currencies`() {
        assertFailsWith<IllegalArgumentException> {
            create(listOf(share(1), share(2, amount = fiat(2_000_000, currency = CurrencyCode.USD))))
        }
    }

    @Test
    fun `rejects shares whose currency differs from the verified rate`() {
        assertFailsWith<IllegalArgumentException> { create(state = verifiedState("usd")) }
    }
}
