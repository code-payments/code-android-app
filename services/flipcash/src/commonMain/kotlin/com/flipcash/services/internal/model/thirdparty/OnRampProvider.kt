package com.flipcash.services.internal.model.thirdparty

sealed interface OnRampProvider {

    data object Unknown : OnRampProvider

    sealed interface Defined
    sealed interface ThirdParty: Defined
    sealed interface UsesDeeplinks: Defined

    data object ManualDeposit : OnRampProvider, Defined

    data class Coinbase(val type: OnRampType) : OnRampProvider, ThirdParty

    data object Phantom: OnRampProvider, ThirdParty, UsesDeeplinks

    companion object {
        val types: List<OnRampProvider.Defined> = listOf(
            Phantom,
            ManualDeposit
        )
    }
}

enum class OnRampType {
    Virtual,
    PhysicalDebit,
    PhysicalCredit,
}

