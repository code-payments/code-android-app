package com.flipcash.services.internal.model.thirdparty

sealed interface OnRampProvider {

    data object Unknown : OnRampProvider

    sealed interface Defined
    sealed interface ThirdParty: Defined
    sealed interface UsesDeeplinks: Defined

    data object ManualDeposit : OnRampProvider, Defined

    data class Coinbase(val type: OnRampType) : OnRampProvider, ThirdParty

    data object Phantom: OnRampProvider, ThirdParty, UsesDeeplinks
    data object Solflare: OnRampProvider, ThirdParty, UsesDeeplinks
    data object Backpack: OnRampProvider, ThirdParty, UsesDeeplinks

    companion object {
        val types: List<OnRampProvider.Defined> = listOf(
            Phantom,
            Solflare,
            Backpack,
            ManualDeposit
        )
    }
}

enum class OnRampType {
    Virtual,
    PhysicalDebit,
    PhysicalCredit,
}

