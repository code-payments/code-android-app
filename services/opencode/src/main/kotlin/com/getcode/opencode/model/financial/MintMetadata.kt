package com.getcode.opencode.model.financial

import com.getcode.solana.keys.PublicKey


/**
 * Represents metadata associated with a mint account.
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
data class MintMetadata(
    val address: PublicKey,
    val decimals: Int,
    val name: String,
    val symbol: String,
    val description: String,
    val imageUrl: String,
    val vmMetadata: VmMetadata?,
    val launchpadMetadata: LaunchpadMetadata?
)

/**
 * Represents metadata associated with a VM.
 *
 * @property vm VM address
 * @property authority Authority that subsidizes and authorizes all transactions against the VM
 * @property lockDurationInDays Lock duration of Virtual Timelock Accounts on the VM, currently hardcoded
 * to 21 days
 */
data class VmMetadata(
    val vm: PublicKey,
    val authority: PublicKey,
    val lockDurationInDays: Int // currently hardcoded to 21 days
)

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
)

