package com.getcode.opencode.model.accounts

import com.codeinc.opencode.gen.common.v1.Model

sealed interface AccountType {
    data object Primary : AccountType
    data object Incoming : AccountType
    data object Outgoing : AccountType
    data object RemoteSend: AccountType

    data object AssociatedToken: AccountType

    data object Swap: AccountType

    data object Pool: AccountType

    fun sortOrder() = when (this) {
        Primary -> 0
        Incoming -> 1
        Outgoing -> 2
        Swap -> 10
        RemoteSend -> 12
        AssociatedToken -> 15
        Pool -> 16
    }

    fun getAccountType(): Model.AccountType {
        return when (this) {
            Primary -> Model.AccountType.PRIMARY
            Incoming -> Model.AccountType.TEMPORARY_INCOMING
            Outgoing -> Model.AccountType.TEMPORARY_OUTGOING
            RemoteSend -> Model.AccountType.REMOTE_SEND_GIFT_CARD
            Swap -> Model.AccountType.SWAP
            AssociatedToken -> Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT
            Pool -> Model.AccountType.POOL
        }
    }

    companion object {
        fun newInstance(accountType: Model.AccountType): AccountType? {
            return when (accountType) {
                Model.AccountType.PRIMARY -> Primary
                Model.AccountType.TEMPORARY_INCOMING -> Incoming
                Model.AccountType.TEMPORARY_OUTGOING -> Outgoing
                Model.AccountType.BUCKET_1_KIN -> null
                Model.AccountType.BUCKET_10_KIN -> null
                Model.AccountType.BUCKET_100_KIN -> null
                Model.AccountType.BUCKET_1_000_KIN -> null
                Model.AccountType.BUCKET_10_000_KIN -> null
                Model.AccountType.BUCKET_100_000_KIN -> null
                Model.AccountType.BUCKET_1_000_000_KIN -> null
                Model.AccountType.UNKNOWN -> null
                Model.AccountType.LEGACY_PRIMARY_2022 -> Primary
                Model.AccountType.REMOTE_SEND_GIFT_CARD -> RemoteSend
                Model.AccountType.UNRECOGNIZED -> null
                Model.AccountType.RELATIONSHIP -> null
                Model.AccountType.SWAP -> Swap
                Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT -> AssociatedToken
                Model.AccountType.POOL -> Pool
            }
        }
    }
}