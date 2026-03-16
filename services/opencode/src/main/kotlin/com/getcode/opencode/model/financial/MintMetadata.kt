package com.getcode.opencode.model.financial

import android.os.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.extensions.deriveVmOmnibusAddress
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.time.Instant

data class TokenWithBalance(
    val token: Token,
    val balance: Fiat,
    val appreciation: Fiat = Fiat.MIN_VALUE,
    val displayName: String = token.name,
) {
    val isReserves: Boolean
        get() = token.address == Mint.usdf

    val costBasis: Fiat
        get() = balance - appreciation
}

data class TokenWithLocalizedBalance(
    val token: Token,
    val balance: LocalFiat,
    val appreciation: LocalFiat = LocalFiat.MIN_VALUE,
    val displayName: String = token.name,
) {
    val isReserves: Boolean
        get() = token.address == Mint.usdf

    val costBasis: LocalFiat
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
    internal val omnibus: PublicKey
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