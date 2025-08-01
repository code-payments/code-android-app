package com.flipcash.services.internal.model.thirdparty

sealed interface OnRampProvider {

    data object Unknown : OnRampProvider

    sealed interface Defined
    sealed interface ThirdParty: Defined

    data object CryptoDeposit : OnRampProvider, Defined

    data class Coinbase(val type: OnRampType) : OnRampProvider, ThirdParty
}

enum class OnRampType {
    Virtual,
    PhysicalDebit,
    PhysicalCredit,
}

