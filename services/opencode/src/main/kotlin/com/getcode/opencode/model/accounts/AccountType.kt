package com.getcode.opencode.model.accounts

import com.codeinc.opencode.gen.common.v1.Model

sealed interface AccountType {
    data object Primary : AccountType
    data object RemoteSend: AccountType

    data object AssociatedToken: AccountType

    data object Swap: AccountType

    data object Pool: AccountType

    data object Unknown: AccountType

    fun sortOrder() = when (this) {
        Primary -> 0
        Swap -> 10
        RemoteSend -> 12
        AssociatedToken -> 15
        Pool -> 16
        Unknown -> 999
    }

    fun getAccountType(): Model.AccountType {
        return when (this) {
            Primary -> Model.AccountType.PRIMARY
            RemoteSend -> Model.AccountType.REMOTE_SEND_GIFT_CARD
            Swap -> Model.AccountType.SWAP
            AssociatedToken -> Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT
            Pool -> Model.AccountType.POOL
            Unknown -> Model.AccountType.UNKNOWN
        }
    }

    companion object {
        fun newInstance(accountType: Model.AccountType): AccountType? {
            return when (accountType) {
                Model.AccountType.PRIMARY -> Primary
                Model.AccountType.UNKNOWN -> null
                Model.AccountType.REMOTE_SEND_GIFT_CARD -> RemoteSend
                Model.AccountType.UNRECOGNIZED -> null
                Model.AccountType.SWAP -> Swap
                Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT -> AssociatedToken
                Model.AccountType.POOL -> Pool
            }
        }
    }
}