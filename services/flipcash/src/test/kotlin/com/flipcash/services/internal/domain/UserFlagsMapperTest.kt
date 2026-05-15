package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService
import com.codeinc.flipcash.gen.account.v1.userFlags
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UserFlagsMapperTest {

    private val mapper = UserFlagsMapper()

    @Test
    fun `maps boolean flags`() {
        val proto = userFlags {
            isRegisteredAccount = true
            isStaff = true
            requiresIapForRegistration = true
        }

        val result = mapper.map(proto)

        assertTrue(result.isRegistered)
        assertTrue(result.isStaff)
        assertTrue(result.requiresIapForRegistration)
    }

    @Test
    fun `maps Coinbase Virtual provider`() {
        val proto = userFlags {
            preferredOnRampProvider = FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_VIRTUAL
        }

        val result = mapper.map(proto)
        val provider = result.preferredOnRampProvider

        assertTrue(provider is OnRampProvider.Coinbase)
        assertEquals(OnRampType.Virtual, provider.type)
    }

    @Test
    fun `maps Coinbase Physical Debit provider`() {
        val proto = userFlags {
            preferredOnRampProvider = FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_PHYSICAL_DEBIT
        }

        val result = mapper.map(proto)
        val provider = result.preferredOnRampProvider

        assertTrue(provider is OnRampProvider.Coinbase)
        assertEquals(OnRampType.PhysicalDebit, provider.type)
    }

    @Test
    fun `maps Coinbase Physical Credit provider`() {
        val proto = userFlags {
            preferredOnRampProvider = FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_PHYSICAL_CREDIT
        }

        val result = mapper.map(proto)
        val provider = result.preferredOnRampProvider

        assertTrue(provider is OnRampProvider.Coinbase)
        assertEquals(OnRampType.PhysicalCredit, provider.type)
    }

    @Test
    fun `maps wallet providers`() {
        val proto = FlipcashAccountService.UserFlags.newBuilder()
            .addAllSupportedOnRampProviders(
                listOf(
                    FlipcashAccountService.UserFlags.OnRampProvider.PHANTOM,
                    FlipcashAccountService.UserFlags.OnRampProvider.SOLFLARE,
                    FlipcashAccountService.UserFlags.OnRampProvider.BACKPACK,
                    FlipcashAccountService.UserFlags.OnRampProvider.MANUAL_DEPOSIT,
                )
            ).build()

        val result = mapper.map(proto)

        assertEquals(
            listOf(
                OnRampProvider.Phantom,
                OnRampProvider.Solflare,
                OnRampProvider.Backpack,
                OnRampProvider.ManualDeposit,
            ),
            result.supportedOnRampProviders,
        )
    }

    @Test
    fun `UNKNOWN preferred provider maps to null`() {
        val proto = userFlags {
            preferredOnRampProvider = FlipcashAccountService.UserFlags.OnRampProvider.UNKNOWN_ON_RAMP_PROVIDER
        }

        val result = mapper.map(proto)

        assertNull(result.preferredOnRampProvider)
    }

    @Test
    fun `maps bill exchange data timeout`() {
        val proto = FlipcashAccountService.UserFlags.newBuilder()
            .setBillExchangeDataTimeout(
                com.google.protobuf.Duration.newBuilder().setSeconds(30).build()
            ).build()

        val result = mapper.map(proto)

        assertEquals(30.seconds, result.billExchangeDataTimeout)
    }

    @Test
    fun `maps new currency purchase amount`() {
        val proto = userFlags {
            newCurrencyPurchaseAmount = 5_000_000L
        }

        val result = mapper.map(proto)

        assertEquals(5_000_000L, result.newCurrencyPurchaseAmount.quarks)
    }

    @Test
    fun `maps minimum version`() {
        val proto = userFlags {
            minBuildNumber = 42
        }

        val result = mapper.map(proto)

        assertEquals(42, result.minimumVersion)
    }
}
