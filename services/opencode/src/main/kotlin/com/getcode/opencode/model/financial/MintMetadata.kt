package com.getcode.opencode.model.financial

import android.graphics.Color
import android.icu.util.ULocale
import android.os.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

data class TokenWithBalance(
    val token: Token,
    val balance: Fiat
)

data class TokenWithLocalizedBalance(
    val token: Token,
    val balance: LocalFiat
)

typealias Token = MintMetadata

val MintMetadata.Companion.usdc: Token
    get() = MintMetadata(
        address = Mint.usdc,
        decimals = 6,
        name = "USDC",
        symbol = "USDC",
        description = "",
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
        launchpadMetadata = null
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
 * can be used for calculating price, market cap, etc. based on the exponential
 * bonding curve
 */
@Parcelize
data class MintMetadata(
    val address: Mint,
    val decimals: Int,
    val name: String,
    val symbol: String,
    val description: String,
    val imageUrl: String,
    val vmMetadata: VmMetadata,
    val launchpadMetadata: LaunchpadMetadata?
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
) : Parcelable

/**
 * Represents metadata associated with a launchpad.
 *
 * @property currencyConfig The address of the currency config
 * @property liquidityPool The address of the liquidity pool
 * @property seed The random seed used during currency creation
 * @property authority The address of the authority for the currency
 * @property mintVault The address where this mint's tokens are locked against the liquidity pool
 * @property coreMintVault The address where core mint tokens are locked against the liquidity pool
 * @property coreMintFees The address where core mint fees are paid
 * @property currentCirculatingSupplyQuarks The current circulating mint token supply in quarks
 * @property coreMintLockedQuarks The current core mint quarks locked in the liquidity pool
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
    val coreMintFees: PublicKey,
    val currentCirculatingSupplyQuarks: Long,
    val coreMintLockedQuarks: Long,
    val sellFeeBps: Int, // currently hardcoded to 1%
    val billCustomizations: TokenBillCustomizations?,
) : Parcelable

@Parcelize
data class TokenBillCustomizations(
    val background: BillBackground,
    val icon: ByteArray?,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenBillCustomizations

        if (background != other.background) return false
        if (!icon.contentEquals(other.icon)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + icon.contentHashCode()
        return result
    }
}

@Parcelize
sealed interface BillBackground: Parcelable {
    data class Solid(val colorHex: String): BillBackground
    {
        companion object {
            fun from(colorInt: Int): Solid {
                return Solid(String.format("#%06X", 0xFFFFFF and colorInt))
            }
        }
    }
    data class Gradient(val colors: List<String>): BillBackground
    {
        companion object {
            fun from(colorInts: List<Int>): Gradient {
                return Gradient(colorInts.map { String.format("#%06X", 0xFFFFFF and it) })
            }
        }
    }
}

