package com.getcode.opencode.model.financial

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.libs.parcelable.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.extensions.deriveVmOmnibusAddress
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.libs.parcelable.IgnoredOnParcel
import com.getcode.libs.parcelable.Parcelize
import com.flipcash.libs.currency.math.decimal.Decimals
import com.getcode.opencode.internal.format.formatUsQuantity
import kotlin.time.Clock
import kotlin.time.Instant

data class TokenWithBalance(
    val token: Token,
    val balance: Fiat,
    val appreciation: Fiat = Fiat.MIN_VALUE,
    val displayName: String = token.name,
    /**
     * The on-chain amount [balance] was valued from, in the token's own quarks. Null when only the
     * USD value is known, as for a balance restored from the local database.
     */
    val tokenQuarks: Long? = null,
) {
    val isReserves: Boolean
        get() = token.address == Mint.usdf

    val costBasis: Fiat
        get() = balance - appreciation
}

typealias Token = MintMetadata

val MintMetadata.Companion.usdf: Token
    get() = MintMetadata(
        address = Mint.usdf,
        decimals = 6,
        name = "USDF",
        symbol = "USDF",
        description = "",
        createdAt = Instant.parse("2018-05-15T05:00:00Z"),
        imageUrl = "",
        vmMetadata = VmMetadata(
            authority = vmAuthority,
            vm = PublicKey.deriveVirtualMachineAccount(
                mint = Mint.usdf,
                authority = vmAuthority,
                lockout = TimelockDerivedAccounts.lockoutInDays.toUByte()
            ).publicKey,
            lockDurationInDays = TimelockDerivedAccounts.lockoutInDays.toInt()
        ),
        launchpadMetadata = null,
        socialLinks = emptyList(),
        billCustomizations = null,
        holderMetrics = HolderMetrics.None,
    )

val MintMetadata.Companion.usdc: Token
    get() = MintMetadata(
        address = Mint.usdc,
        decimals = 6,
        name = "USDC",
        symbol = "USDC",
        description = "",
        createdAt = Instant.parse("2018-05-15T05:00:00Z"),
        imageUrl = "",
        vmMetadata = VmMetadata(
            authority = vmAuthority,
            vm = PublicKey.deriveVirtualMachineAccount(
                mint = Mint.usdc,
                authority = vmAuthority,
                lockout = TimelockDerivedAccounts.lockoutInDays.toUByte()
            ).publicKey,
            lockDurationInDays = TimelockDerivedAccounts.lockoutInDays.toInt()
        ),
        launchpadMetadata = null,
        socialLinks = emptyList(),
        billCustomizations = null,
        holderMetrics = HolderMetrics.None,
    )

/**
 * Represents metadata associated with a token account.
 *
 * @property address Token mint address
 * @property decimals The number of decimals configured for the mint
 * @property name Currency name
 * @property symbol Currency ticker symbol
 * @property description Currency description
 * @property imageUrl URL to currency image
 * @property vmMetadata Available when a VM exists for the given mint, and can be used for deriving
 * VM deposit PDAs
 * @property launchpadMetadata Available when created by the launchpad via the currency creator program, and
 * can be used for calculating price, market cap, etc. based on the exponential bonding curve
 * @property billCustomizations Optional visual customizations for the bill for this token when give/grabbed
 */
@Parcelize
data class MintMetadata(
    val address: Mint,
    val decimals: Int,
    val name: String,
    val symbol: String,
    val createdAt: Instant?,
    val description: String,
    val imageUrl: String,
    val vmMetadata: VmMetadata,
    val launchpadMetadata: LaunchpadMetadata?,
    val billCustomizations: TokenBillCustomizations?,
    val socialLinks: List<SocialLink>,
    val holderMetrics: HolderMetrics,
    val marketCapMetrics: MarketCapMetrics = MarketCapMetrics.None,
) : Parcelable {
    fun marketCap(): Fiat? {
        val launchpad = launchpadMetadata ?: return null
        val currentCirculatingSupplyQuarks = launchpad.currentCirculatingSupplyQuarks
        return Estimator.currentMarketCap(currentCirculatingSupplyQuarks)
            .map {
                Fiat(it.toDouble(), CurrencyCode.USD)
            }.getOrNull()
    }

    companion object
}

/**
 * [quarks] of this mint, written out as a quantity of tokens.
 *
 * The unit a quark is a fraction of is the mint's own, so the scale has to come from
 * [MintMetadata.decimals] rather than a constant: the reserve has six, every launchpad mint has ten
 * (`DefaultMintQuarksPerUnit`). Ten of them puts a full supply at 2.1e17 quarks, past the range
 * where a `Double` still counts integers exactly, so the shift is done in [BigDecimal] — roughly
 * 900,000 tokens is where dividing by a power of ten in floating point starts losing the low
 * digits.
 *
 * Trailing zeros are dropped rather than padded out to [MintMetadata.decimals], since
 * "1,204.9050000000" states no more than "1,204.905".
 */
fun Token.formattedQuantity(quarks: Long): String {
    return formatUsQuantity(Decimals.valueOf(quarks).movePointLeft(decimals), maxFractionDigits = decimals)
}

/**
 * Represents metadata associated with a VM.
 *
 * @property vm VM address
 * @property authority Authority that subsidizes and authorizes all transactions against the VM
 * @property lockDurationInDays Lock duration of Virtual Timelock Accounts on the VM, currently hardcoded
 * to 21 days
 */
@Parcelize
data class VmMetadata(
    val vm: PublicKey,
    val authority: PublicKey,
    val lockDurationInDays: Int // currently hardcoded to 21 days
) : Parcelable {
    @InternalOpenCodeApi public val omnibus: PublicKey
        get() = PublicKey.deriveVmOmnibusAddress(vm = vm).publicKey
}

/**
 * Represents metadata associated with a launchpad.
 *
 * @property currencyConfig The address of the currency config
 * @property liquidityPool The address of the liquidity pool
 * @property seed The random seed used during currency creation
 * @property authority The address of the authority for the currency
 * @property mintVault The address where this mint's tokens are locked against the liquidity pool
 * @property coreMintVault The address where core mint tokens are locked against the liquidity pool
 * @property currentCirculatingSupplyQuarks The current circulating mint token supply in quarks
 * @property sellFeeBps Precent fee for sells in basis points, currently hardcoded to 1%
 */
@Parcelize
data class LaunchpadMetadata(
    val currencyConfig: PublicKey,
    val liquidityPool: PublicKey,
    val seed: PublicKey,
    val authority: PublicKey,
    val mintVault: PublicKey,
    val coreMintVault: PublicKey,
    val currentCirculatingSupplyQuarks: Long,
    val sellFeeBps: Int, // currently hardcoded to 1%
    val price: Fiat,
    val marketCap: Fiat,
) : Parcelable

/**
 * Represents social links for a currency.
 *
 * @property type The type of social media platform (e.g., "twitter", "website").
 * @property url The URL to the social media profile or website.
 */
@Parcelize
sealed interface SocialLink : Parcelable {
    val uri: String
    @Parcelize
    data class Website(val url: String) : SocialLink {
        @IgnoredOnParcel
        override val uri: String = url
    }

    @Parcelize
    data class X(val username: String) : SocialLink {
        @IgnoredOnParcel
        override val uri: String = "https://x.com/$username"
    }

    @Parcelize
    data class Telegram(val username: String) : SocialLink {
        @IgnoredOnParcel
        override val uri: String = "https://t.me/$username"
    }

    @Parcelize
    data class Discord(val inviteCode: String) : SocialLink {
        @IgnoredOnParcel
        override val uri: String = "https://discord.gg/$inviteCode"
    }
}

@Parcelize
data class HolderMetrics(
    val currentHolders: Long,
    val holderDeltas: List<HolderDelta>,
) : Parcelable {
    companion object {
        val None = HolderMetrics(0, emptyList())
    }

    fun deltaForWindow(window: WindowedRange): Long {
        return holderDeltas.firstOrNull { it.range == window }?.delta ?: 0
    }

    @Parcelize
    data class HolderDelta(
        val range: WindowedRange,
        val delta: Long,
    ) : Parcelable
}

@Parcelize
data class MarketCapMetrics(
    val currentMarketCap: Double,
    val marketCapDeltas: List<MarketCapDelta>,
) : Parcelable {
    companion object {
        val None = MarketCapMetrics(0.0, emptyList())
    }

    fun deltaForWindow(window: WindowedRange): Double {
        return marketCapDeltas.firstOrNull { it.range == window }?.delta ?: 0.0
    }

    @Parcelize
    data class MarketCapDelta(
        val range: WindowedRange,
        val delta: Double,
    ) : Parcelable
}