package com.getcode.network.repository

import com.getcode.model.BalanceCurrencyFeature
import com.getcode.model.BetaFlag
import com.getcode.model.BuyModuleFeature
import com.getcode.model.Feature
import com.getcode.model.PrefsBool
import com.getcode.model.RequestKinFeature
import com.getcode.model.TipCardFeature
import com.getcode.model.TipChatCashFeature
import com.getcode.model.TipChatFeature
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Collates [BetaOptions] with server availability (stored in [PrefRepository]).
 */
class FeatureRepository @Inject constructor(
    private val betaFlags: BetaFlagsRepository,
    prefRepository: PrefRepository,
) {
    val buyModule = combine(
        betaFlags.observe().map { it.buyModuleEnabled },
        prefRepository.observeOrDefault(PrefsBool.BUY_MODULE_AVAILABLE, false)
        ) { enabled, available -> BuyModuleFeature(enabled, available) }

    val tipCards = betaFlags.observe().map { TipCardFeature(it.tipsEnabled) }

    val tipChat = betaFlags.observe().map { TipChatFeature(it.tipsChatEnabled) }
    val tipChatCash = betaFlags.observe().map { TipChatCashFeature(it.tipsChatCashEnabled) }

    val requestKin = betaFlags.observe().map { RequestKinFeature(it.giveRequestsEnabled) }

    val balanceCurrencySelection = betaFlags.observe().map { BalanceCurrencyFeature(it.balanceCurrencySelectionEnabled) }

    suspend fun isEnabled(feature: PrefsBool): Boolean = betaFlags.isEnabled(feature)
}