package com.flipcash.app.userflags

import com.flipcash.app.userflags.UserFlagsCoordinator.Overrides
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.services.models.TipPresets
import com.flipcash.services.models.UserFlags
import com.getcode.opencode.model.financial.Fiat
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ResolvedUserFlagsTest {

    @Test
    fun `resolve without overrides exposes the server value for every flag`() {
        val resolved = ServerFlags.resolve(Overrides.None)

        assertEquals(true, resolved.isStaff.effectiveValue)
        assertEquals(true, resolved.isRegistered.effectiveValue)
        assertEquals(true, resolved.requiresIapForRegistration.effectiveValue)
        assertEquals(OnRampProvider.ManualDeposit, resolved.preferredOnRampProvider.effectiveValue)
        assertEquals(listOf(OnRampProvider.ManualDeposit), resolved.supportedOnRampProviders.effectiveValue)
        assertEquals(100, resolved.minimumVersion.effectiveValue)
        assertEquals(30.seconds, resolved.billExchangeDataTimeout.effectiveValue)
        assertEquals(Fiat(quarks = 1_000_000L), resolved.newCurrencyPurchaseAmount.effectiveValue)
        assertEquals(Fiat(quarks = 2_000_000L), resolved.newCurrencyFeeAmount.effectiveValue)
        assertEquals(Fiat(quarks = 3_000_000L), resolved.withdrawalFeeAmount.effectiveValue)
        assertEquals(UsdcLiquidtyPool.Flipcash, resolved.usdcOnRampLiquidityPool.effectiveValue)
        assertEquals(true, resolved.enablePhoneNumberSend.effectiveValue)
        assertEquals(Fiat(quarks = 4_000_000L), resolved.minimumHolderAmountForLeaderboard.effectiveValue)
        assertEquals(true, resolved.requireCoinbaseEmailVerification.effectiveValue)
        assertEquals(ServerFlags.tipPresets, resolved.tipPresets.effectiveValue)
    }

    @Test
    fun `resolve without overrides marks nothing as overridden`() {
        val resolved = ServerFlags.resolve(Overrides.None)
        OverrideCases.forEach { case ->
            assertFalse(case.isOverridden(resolved), "${case.name} reports an override for Overrides.None")
        }
    }

    @Test
    fun `each override lands on its own field and leaves the others alone`() {
        val baseline = ServerFlags.resolve(Overrides.None)

        OverrideCases.forEach { case ->
            val resolved = ServerFlags.resolve(case.applyTo(Overrides.None))

            assertTrue(case.isOverridden(resolved), "${case.name} override was dropped")
            case.assertOverrideApplied(resolved)

            OverrideCases.filterNot { it === case }.forEach { other ->
                assertFalse(
                    other.isOverridden(resolved),
                    "overriding ${case.name} also overrode ${other.name}",
                )
                other.assertMatchesBaseline(
                    resolved = resolved,
                    baseline = baseline,
                    message = "overriding ${case.name} changed the value of ${other.name}",
                )
            }
        }
    }

    @Test
    fun `all overrides applied together each land on their own field`() {
        val overrides = OverrideCases.fold(Overrides.None) { acc, case -> case.applyTo(acc) }
        val resolved = ServerFlags.resolve(overrides)

        OverrideCases.forEach { case ->
            assertTrue(case.isOverridden(resolved), "${case.name} override was dropped")
            case.assertOverrideApplied(resolved)
        }
    }

    /**
     * Guards the coverage of the tests above: a new field on [Overrides] has to gain a case here,
     * which is what forces its wiring in `resolve()` to be exercised.
     */
    @Test
    fun `every Overrides field has a case`() {
        val declared = Overrides::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(declared, OverrideCases.map { it.name }.toSet())
    }
}

/**
 * One overridable flag: how to set it on [Overrides], and where it should surface
 * in [ResolvedUserFlags]. [name] matches the [Overrides] property name.
 */
private class OverrideCase<T>(
    val name: String,
    private val overrideValue: T,
    private val set: Overrides.(FieldOverride<T>) -> Overrides,
    private val select: (ResolvedUserFlags) -> ResolvedFlag<T>,
) {
    fun applyTo(overrides: Overrides): Overrides = overrides.set(FieldOverride.Value(overrideValue))

    fun isOverridden(resolved: ResolvedUserFlags): Boolean = select(resolved).isOverridden

    fun assertOverrideApplied(resolved: ResolvedUserFlags) {
        assertEquals(overrideValue, select(resolved).effectiveValue, "$name resolved to the wrong value")
    }

    fun assertMatchesBaseline(
        resolved: ResolvedUserFlags,
        baseline: ResolvedUserFlags,
        message: String,
    ) {
        assertEquals(select(baseline).effectiveValue, select(resolved).effectiveValue, message)
    }
}

private val ServerFlags = UserFlags(
    isStaff = true,
    isRegistered = true,
    requiresIapForRegistration = true,
    preferredOnRampProvider = OnRampProvider.ManualDeposit,
    supportedOnRampProviders = listOf(OnRampProvider.ManualDeposit, OnRampProvider.Unknown),
    minimumVersion = 100,
    billExchangeDataTimeout = 30.seconds,
    newCurrencyPurchaseAmount = Fiat(quarks = 1_000_000L),
    newCurrencyFeeAmount = Fiat(quarks = 2_000_000L),
    withdrawalFeeAmount = Fiat(quarks = 3_000_000L),
    preferredUsdcOnRampLiquidityPool = UsdcLiquidtyPool.Flipcash,
    enablePhoneNumberSend = true,
    minimumHolderValue = Fiat(quarks = 4_000_000L),
    requireCoinbaseEmailVerification = true,
    tipPresets = listOf(
        TipPresets(region = "US", minimum = 1.0, low = 2.0, medium = 3.0, high = 4.0),
    ),
)

// Every value here differs from the matching server value above, so a flag that reads the
// wrong override — or none at all — cannot pass by coincidence.
private val OverrideCases: List<OverrideCase<*>> = listOf(
    OverrideCase<OnRampProvider.Defined?>(
        name = "preferredOnRampProvider",
        overrideValue = OnRampProvider.Phantom,
        set = { copy(preferredOnRampProvider = it) },
        select = { it.preferredOnRampProvider },
    ),
    OverrideCase(
        name = "supportedOnRampProviders",
        overrideValue = listOf<OnRampProvider.Defined>(OnRampProvider.Coinbase(OnRampType.Virtual)),
        set = { copy(supportedOnRampProviders = it) },
        select = { it.supportedOnRampProviders },
    ),
    OverrideCase<Int?>(
        name = "minimumVersion",
        overrideValue = 999,
        set = { copy(minimumVersion = it) },
        select = { it.minimumVersion },
    ),
    OverrideCase(
        name = "billExchangeDataTimeout",
        overrideValue = 90.seconds,
        set = { copy(billExchangeDataTimeout = it) },
        select = { it.billExchangeDataTimeout },
    ),
    OverrideCase(
        name = "newCurrencyPurchaseAmount",
        overrideValue = Fiat(quarks = 11_000_000L),
        set = { copy(newCurrencyPurchaseAmount = it) },
        select = { it.newCurrencyPurchaseAmount },
    ),
    OverrideCase(
        name = "newCurrencyFeeAmount",
        overrideValue = Fiat(quarks = 12_000_000L),
        set = { copy(newCurrencyFeeAmount = it) },
        select = { it.newCurrencyFeeAmount },
    ),
    OverrideCase(
        name = "withdrawalFeeAmount",
        overrideValue = Fiat(quarks = 13_000_000L),
        set = { copy(withdrawalFeeAmount = it) },
        select = { it.withdrawalFeeAmount },
    ),
    OverrideCase(
        name = "preferredUsdcOnRampLiquidityPool",
        overrideValue = UsdcLiquidtyPool.CoinbaseStableSwapper,
        set = { copy(preferredUsdcOnRampLiquidityPool = it) },
        select = { it.usdcOnRampLiquidityPool },
    ),
    OverrideCase(
        name = "minimumHolderAmountForLeaderboard",
        overrideValue = Fiat(quarks = 14_000_000L),
        set = { copy(minimumHolderAmountForLeaderboard = it) },
        select = { it.minimumHolderAmountForLeaderboard },
    ),
    OverrideCase(
        name = "requireCoinbaseEmailVerification",
        overrideValue = false,
        set = { copy(requireCoinbaseEmailVerification = it) },
        select = { it.requireCoinbaseEmailVerification },
    ),
)
