package com.getcode.opencode.model.accounts

sealed interface AccountType {
    data object Primary : AccountType
    data object RemoteSend: AccountType

    data object AssociatedToken: AccountType

    data object Swap: AccountType

    data object Pool: AccountType

    data object Unknown: AccountType

    fun sortOrder() = when (this) {
        Primary -> 0
        RemoteSend -> 1
        Swap -> 2
        AssociatedToken -> 3
        Pool -> 4
        Unknown -> 999
    }

    companion object
}
