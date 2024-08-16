package com.getcode.model

import com.getcode.network.repository.BetaOptions

sealed interface Feature {
    val enabled: Boolean
    val available: Boolean
}

data class BuyModuleFeature(
    override val enabled: Boolean = BetaOptions.Defaults.buyModuleEnabled,
    override val available: Boolean = false, // server driven availability
): Feature

data class TipCardFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipsEnabled,
    override val available: Boolean = true, // always available
): Feature

data class TipCardOnHomeScreenFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipCardOnHomeScreen,
    override val available: Boolean = true, // always available
): Feature

data class TipChatFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipsChatEnabled,
    override val available: Boolean = true, // always available
): Feature

data class TipChatCashFeature(
    override val enabled: Boolean = BetaOptions.Defaults.tipsChatCashEnabled,
    override val available: Boolean = true, // always available
): Feature


data class RequestKinFeature(
    override val enabled: Boolean = BetaOptions.Defaults.giveRequestsEnabled,
    override val available: Boolean = true, // always available
): Feature

data class BalanceCurrencyFeature(
    override val enabled: Boolean = BetaOptions.Defaults.balanceCurrencySelectionEnabled,
    override val available: Boolean = true, // always available
): Feature