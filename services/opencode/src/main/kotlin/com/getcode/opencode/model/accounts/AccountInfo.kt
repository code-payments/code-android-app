package com.getcode.opencode.model.accounts

import com.codeinc.opencode.gen.account.v1.AccountService
import com.getcode.opencode.internal.network.extensions.toModel
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

data class AccountInfo(
    /**
     * The token account's address
     */
    val address: PublicKey,
    /**
     * The owner of the token account, which can also be thought of as a parent
     * account that links to one or more token accounts. This is provided when
     * available.
     */
    val owner: PublicKey?,
    /**
     * The token account's authority, which has access to moving funds for the
     * account. This can be the owner account under certain circumstances (eg.
     * ATA, primary account). This is provided when available.
     */
    val authority: PublicKey?,

    /**
     * The type of token account, which infers its intended use.
     */
    val accountType: AccountType,

    /**
     * The account's derivation index for applicable account types. When this field
     * doesn't apply, a zero value is provided.
     */
    val index: Int,

    /**
     * The source of truth for the balance calculation.
     */
    val balanceSource: BalanceSource,

    /**
     * The balance in quarks, as observed by Code. This may not reflect the value
     * on the blockchain and could be non-zero even if the account hasn't been created.
     * Use [balanceSource] to determine how this value was calculated.
     */
    val balance: Fiat,

    /**
     * The state of the account as it pertains to Code's ability to manage funds.
     */
    val managementState: ManagementState,

    /**
     * The state of the account on the blockchain.
     */
    val blockchainState: BlockchainState,

    /**
     * Whether an account is claimed. This only applies to relevant account types
     * (e.g. [com.codeinc.opencode.gen.common.v1.Model.AccountType.REMOTE_SEND_GIFT_CARD]
     */
    val claimState: ClaimState,

    /**
     * For account types used as an intermediary for sending money between two
     * users (eg. [com.codeinc.opencode.gen.common.v1.Model.AccountType.REMOTE_SEND_GIFT_CARD]),
     * this represents the original exchange data used to fund the account.
     * Over time, this value will become stale:
     *  1. Exchange rates will fluctuate, so the total fiat amount will differ.
     *  2. External entities can deposit additional funds into the account, so
     *  the balance, in quarks, may be greater than the original quark value.
     *  3. The balance could have been received, so the total balance can show
     *  as zero.
     */
    val originalExchangeData: ExchangeData.WithRate,

    /**
     * The token account's mint
     */
    val mint: Mint,

    /**
     * Time the account was created, if available. For Code accounts, this is
     * the time of intent submission. Otherwise, for external accounts, it is
     * the time created on the blockchain.
     */
    val createdAt: Long?,

    ) {
    companion object {
        fun newInstance(info: AccountService.TokenAccountInfo): AccountInfo? {
            val accountType =
                AccountType.newInstance(info.accountType) ?: return null
            val address = PublicKey(info.address.value.toByteArray().toList())
            val balanceSource = BalanceSource.getInstance(info.balanceSource) ?: return null
            val managementState = ManagementState.getInstance(info.managementState) ?: return null
            val blockchainState = BlockchainState.getInstance(info.blockchainState) ?: return null
            val claimState = ClaimState.getInstance(info.claimState) ?: return null

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
                balance = Fiat(info.balance.toULong()),
                managementState = managementState,
                blockchainState = blockchainState,
                claimState = claimState,
                originalExchangeData = exchangeData,
                mint = info.mint.toPublicKey(),
                createdAt = info.createdAt.seconds * 1000L
            )
        }
    }

    enum class ManagementState {
        /**
         * The state of the account is unknown. This may be returned when the
         * data source is unstable and a reliable state cannot be determined.
         */
        Unknown,

        /**
         * Code does not maintain a management state and won't move funds for this
         * account.
         */
        None,

        /**
         * The account is in the process of transitioning to the [Locked] state.
         */
        Locking,

        /**
         * The account's funds are locked and Code has co-signing authority.
         */
        Locked,

        /**
         * The account is in the process of transitioning to the [Unlocked] state.
         */
        Unlocking,

        /**
         * The account's funds are unlocked and Code no longer has co-signing
         * authority. The account must transition to the [Locked] state to have
         * management capabilities.
         */
        Unlocked,

        /**
         * The account is in the process of transitioning to the [Closed] state.
         */
        Closing,

        /**
         * The account has been closed and doesn't exist on the blockchain.
         * Subsequently, it also has a zero balance.
         */
        Closed;

        companion object {
            fun getInstance(state: AccountService.TokenAccountInfo.ManagementState): ManagementState? {
                return when (state) {
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNKNOWN -> Unknown
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_NONE -> None
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKING -> Locking
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED -> Locked
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKING -> Unlocking
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKED -> Unlocked
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSING -> Closing
                    AccountService.TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSED -> Closed
                    AccountService.TokenAccountInfo.ManagementState.UNRECOGNIZED -> null
                }

            }
        }
    }

    enum class BlockchainState {
        /// The state of the account is unknown. This may be returned when the
        /// data source is unstable and a reliable state cannot be determined.
        Unknown,

        /// The account does not exist on the blockchain.
        DoesntExist,

        /// The account is created and exists on the blockchain.
        Exists;

        companion object {
            fun getInstance(state: AccountService.TokenAccountInfo.BlockchainState): BlockchainState? {
                return when (state) {
                    AccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_UNKNOWN -> Unknown
                    AccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_DOES_NOT_EXIST -> DoesntExist
                    AccountService.TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_EXISTS -> Exists
                    AccountService.TokenAccountInfo.BlockchainState.UNRECOGNIZED -> null
                }
            }
        }
    }

    enum class ClaimState {
        /**
         * The account doesn't have a concept of being claimed, or the state
         * could not be fetched by server.
         */
        Unknown,

        /**
         * The account has not yet been claimed.
         */
        NotClaimed,

        /**
         * The account is claimed. Attempting to claim it will fail.
         */
        Claimed,

        /**
         * The account hasn't been claimed, but is expired. Funds will move
         * back to the issuer. Attempting to claim it will fail.
         */
        Expired;

        companion object {
            fun getInstance(state: AccountService.TokenAccountInfo.ClaimState): ClaimState? {
                return when (state) {
                    AccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_UNKNOWN -> Unknown
                    AccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_NOT_CLAIMED -> NotClaimed
                    AccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_CLAIMED -> Claimed
                    AccountService.TokenAccountInfo.ClaimState.CLAIM_STATE_EXPIRED -> Expired
                    AccountService.TokenAccountInfo.ClaimState.UNRECOGNIZED -> null
                }
            }
        }
    }

    enum class BalanceSource {
        /**
         * The account's balance could not be determined. This may be returned when
         * the data source is unstable and a reliable balance cannot be determined.
         */
        Unknown,

        /**
         * The account's balance was fetched directly from a finalized state on the
         * blockchain.
         */
        Blockchain,

        /**
         * The account's balance was calculated using cached values in Code. Accuracy
         * is only guaranteed when [managementState] is [ManagementState.Locked].
         */
        Cache;

        companion object {
            fun getInstance(source: AccountService.TokenAccountInfo.BalanceSource): BalanceSource? {
                return when (source) {
                    AccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_UNKNOWN -> Unknown
                    AccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_BLOCKCHAIN -> Blockchain
                    AccountService.TokenAccountInfo.BalanceSource.BALANCE_SOURCE_CACHE -> Cache
                    AccountService.TokenAccountInfo.BalanceSource.UNRECOGNIZED -> null
                }
            }

        }
    }
}

val AccountInfo.displayName: String
    get() = when (val type = accountType) {
        AccountType.Incoming -> "Incoming $index"
        AccountType.Outgoing -> "Outgoing $index"
        AccountType.Primary -> "Primary"
        AccountType.RemoteSend -> "Remote Send"
        AccountType.Swap -> "Swap (USDC)"
    }

// An account is deemed unuseable in Code if the management
// state for said account is no longer `locked`. Some accounts may
// be allowed to operated in an 'unlocked' or another state
val AccountInfo.unusable: Boolean
    get() = if (managementState == AccountInfo.ManagementState.None) {
        // If the account is not managed
        // by Code, it is always useable
        false
    } else {
        managementState != AccountInfo.ManagementState.Locked
    }