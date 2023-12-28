package com.getcode.model

import com.codeinc.gen.account.v1.AccountService
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType

data class AccountInfo (
    /// The account's derivation index for applicable account types. When this field
    /// doesn't apply, a zero value is provided.
    var index: Int,

    /// The type of token account, which infers its intended use.
    var accountType: AccountType,

    /// The token account's address
    var address: PublicKey,

    /// The owner of the token account, which can also be thought of as a parent
    /// account that links to one or more token accounts. This is provided when
    /// available.
    var owner: PublicKey?,

    /// The token account's authority, which has access to moving funds for the
    /// account. This can be the owner account under certain circumstances (eg.
    /// ATA, primary account). This is provided when available.
    var authority: PublicKey?,

    /// The source of truth for the balance calculation.
    var balanceSource: BalanceSource,

    /// The Kin balance in quarks, as observed by Code. This may not reflect the
    /// value on the blockchain and could be non-zero even if the account hasn't
    /// been created. Use balance_source to determine how this value was calculated.
    var balance: Kin,

    /// The state of the account as it pertains to Code's ability to manage funds.
    var managementState: ManagementState,

    /// The state of the account on the blockchain.
    var blockchainState: BlockchainState,

    /// Whether an account is claimed. This only applies to relevant account types
    /// (eg. REMOTE_SEND_GIFT_CARD).
    var claimState: ClaimState,

    /// For temporary incoming accounts only. Flag indicates whether client must
    /// actively try rotating it by issuing a ReceivePayments intent. In general,
    /// clients should wait as long as possible until this flag is true or requiring
    /// the funds to send their next payment.
    var mustRotate: Boolean,

    /// For account types used as an intermediary for sending money between two
    /// users (eg. REMOTE_SEND_GIFT_CARD), this represents the original exchange
    /// data used to fund the account. Over time, this value will become stale:
    ///  1. Exchange rates will fluctuate, so the total fiat amount will differ.
    ///  2. External entities can deposit additional funds into the account, so
    ///     the balance, in quarks, may be greater than the original quark value.
    ///  3. The balance could have been received, so the total balance can show
    ///     as zero.
    var originalKinAmount: KinAmount?,

    /// The relationship with a third party that this account has established with.
    /// This only applies to relevant account types (eg. RELATIONSHIP).
    var relationship: Relationship?

) {
    companion object {
        fun newInstance(info: AccountService.TokenAccountInfo): AccountInfo? {
            val accountType = AccountType.newInstance(info.accountType, info.relationship) ?: return null
            val address = PublicKey(info.address.value.toByteArray().toList())
            val balanceSource = BalanceSource.getInstance(info.balanceSource) ?: return null

            val managementState = ManagementState.getInstance(info.managementState) ?: return null
            val blockchainState = BlockchainState.getInstance(info.blockchainState) ?: return null
            val claimState = ClaimState.getInstance(info.claimState) ?: return null

            val owner = PublicKey(info.owner.value.toByteArray().toList())
            val authority = PublicKey(info.authority.value.toByteArray().toList())

            val originalCurrency = CurrencyCode.tryValueOf(info.originalExchangeData.currency)

            val originalKinAmount = originalCurrency?.let {
                KinAmount.newInstance(
                    kin = Kin(quarks = info.originalExchangeData.quarks),
                    rate = Rate(
                        fx = info.originalExchangeData.exchangeRate,
                        currency = originalCurrency
                    )
                )
            }

            val relationship = Domain.from(info.relationship.domain.value)
                ?.let { Relationship(it) }

            return AccountInfo(
                index = info.index.toInt(),
                accountType = accountType,
                address = address,
                owner = owner,
                authority = authority,
                balanceSource = balanceSource,
                balance = Kin(quarks = info.balance),
                managementState = managementState,
                blockchainState = blockchainState,
                claimState = claimState,
                mustRotate = info.mustRotate,
                originalKinAmount = originalKinAmount,
                relationship = relationship
            )

        }
    }

    enum class ManagementState {
        /// The state of the account is unknown. This may be returned when the
        /// data source is unstable and a reliable state cannot be determined.
        Unknown,

        /// Code does not maintain a management state and won't move funds for this
        /// account.
        None,

        /// The account is in the process of transitioning to the LOCKED state.
        Locking,

        /// The account's funds are locked and Code has co-signing authority.
        Locked,

        /// The account is in the process of transitioning to the UNLOCKED state.
        Unlocking,

        /// The account's funds are unlocked and Code no longer has co-signing
        /// authority. The account must transition to the LOCKED state to have
        /// management capabilities.
        Unlocked,

        /// The account is in the process of transitioning to the CLOSED state.
        Closing,

        /// The account has been closed and doesn't exist on the blockchain.
        /// Subsequently, it also has a zero balance.
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
        /// could not be fetched by server.
        Unknown,

        /// The account has not yet been claimed.
        NotClaimed,

        /// The account is claimed. Attempting to claim it will fail.
        Claimed,

        /// The account hasn't been claimed, but is expired. Funds will move
        /// back to the issuer. Attempting to claim it will fail.
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
        /// The account's balance could not be determined. This may be returned when
        /// the data source is unstable and a reliable balance cannot be determined.
        Unknown,

        /// The account's balance was fetched directly from a finalized state on the
        /// blockchain.
        Blockchain,

        /// The account's balance was calculated using cached values in Code. Accuracy
        /// is only guaranteed when management_state is LOCKED.
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

    data class Relationship(val domain: Domain)
}