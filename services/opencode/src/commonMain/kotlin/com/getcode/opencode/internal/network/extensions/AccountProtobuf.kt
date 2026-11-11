package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.account.v1.OcpAccountService
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.solana.keys.PublicKey

// protobuf-java decoding of the account and limits models. The Wire decoding of the same messages
// lives in :services:opencode-client; both must produce the same values.

fun AccountInfo.Companion.newInstance(info: OcpAccountService.TokenAccountInfo): AccountInfo? {
    val accountType =
        AccountType.newInstance(info.accountType) ?: return null
    val address = PublicKey(info.address.value.toByteArray().toList())
    val balanceSource = AccountInfo.BalanceSource.getInstance(info.balanceSource) ?: return null
    val managementState = AccountInfo.ManagementState.getInstance(info.managementState) ?: return null
    val blockchainState = AccountInfo.BlockchainState.getInstance(info.blockchainState) ?: return null
    val claimState = AccountInfo.ClaimState.getInstance(info.claimState) ?: return null

    val owner = PublicKey(info.owner.value.toByteArray().toList())
    val authority =
        PublicKey(info.authority.value.toByteArray().toList())

    val exchangeData = info.originalExchangeData.toModel()

    return AccountInfo(
        index = info.index.toInt(),
        accountType = accountType,
        address = address,
        owner = owner,
        authority = authority,
        balanceSource = balanceSource,
        balance = info.balance,
        managementState = managementState,
        blockchainState = blockchainState,
        claimState = claimState,
        originalExchangeData = exchangeData,
        mint = info.mint.toMint(),
        createdAt = info.createdAt.seconds * 1000L,
        isGiftCardIssuer = info.isGiftCardIssuer,
        usdCostBasis = info.usdCostBasis,
    )
}

fun AccountInfo.ManagementState.Companion.getInstance(state: OcpAccountService.TokenAccountInfo.ManagementState): AccountInfo.ManagementState? {
    return when (state) {
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNKNOWN -> AccountInfo.ManagementState.Unknown
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_NONE -> AccountInfo.ManagementState.None
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKING -> AccountInfo.ManagementState.Locking
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED -> AccountInfo.ManagementState.Locked
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKING -> AccountInfo.ManagementState.Unlocking
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKED -> AccountInfo.ManagementState.Unlocked
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSING -> AccountInfo.ManagementState.Closing
        OcpAccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSED -> AccountInfo.ManagementState.Closed
        OcpAccountService.TokenAccountInfo.ManagementState.UNRECOGNIZED -> null
    }
}

fun AccountInfo.BlockchainState.Companion.getInstance(state: OcpAccountService.TokenAccountInfo.BlockchainState): AccountInfo.BlockchainState? {
    return when (state) {
        OcpAccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_UNKNOWN -> AccountInfo.BlockchainState.Unknown
        OcpAccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_DOES_NOT_EXIST -> AccountInfo.BlockchainState.DoesntExist
        OcpAccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_EXISTS -> AccountInfo.BlockchainState.Exists
        OcpAccountService.TokenAccountInfo.BlockchainState.UNRECOGNIZED -> null
    }
}

fun AccountInfo.ClaimState.Companion.getInstance(state: OcpAccountService.TokenAccountInfo.ClaimState): AccountInfo.ClaimState? {
    return when (state) {
        OcpAccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_UNKNOWN -> AccountInfo.ClaimState.Unknown
        OcpAccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_NOT_CLAIMED -> AccountInfo.ClaimState.NotClaimed
        OcpAccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_CLAIMED -> AccountInfo.ClaimState.Claimed
        OcpAccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_EXPIRED -> AccountInfo.ClaimState.Expired
        OcpAccountService.TokenAccountInfo.ClaimState.UNRECOGNIZED -> null
    }
}

fun AccountInfo.BalanceSource.Companion.getInstance(source: OcpAccountService.TokenAccountInfo.BalanceSource): AccountInfo.BalanceSource? {
    return when (source) {
        OcpAccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_UNKNOWN -> AccountInfo.BalanceSource.Unknown
        OcpAccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_BLOCKCHAIN -> AccountInfo.BalanceSource.Blockchain
        OcpAccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_CACHE -> AccountInfo.BalanceSource.Cache
        OcpAccountService.TokenAccountInfo.BalanceSource.UNRECOGNIZED -> null
    }
}

@JvmName("newInstanceFromProtobuf")
fun Limits.Companion.newInstance(
    sinceDate: Long,
    fetchDate: Long,
    sendLimits: Map<String, OcpTransactionService.SendLimit>,
    usdTransactedSinceConsumption: Double,
): Limits = Limits.newInstance(
    sinceDate = sinceDate,
    fetchDate = fetchDate,
    sendLimits = sendLimits.mapValues { (_, v) ->
        SendLimit(
            nextTransaction = v.nextTransaction.toDouble(),
            maxPerDay = v.maxPerDay.toDouble(),
            maxPerTransaction = v.maxPerTransaction.toDouble(),
        )
    },
    usdTransactedSinceConsumption = usdTransactedSinceConsumption,
)
