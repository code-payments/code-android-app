package com.getcode.network.repository

import com.getcode.model.BalanceCurrencyFeature
import com.getcode.model.BuyModuleFeature
import com.getcode.model.PrefsBool
import com.getcode.model.RequestKinFeature
import com.getcode.model.TipCardFeature
import com.getcode.model.TipCardOnHomeScreenFeature
import com.getcode.model.ConversationCashFeature
import com.getcode.model.ConversationsFeature
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
    val tipCardOnHomeScreen = betaFlags.observe().map { TipCardOnHomeScreenFeature(it.tipCardOnHomeScreen) }
    val conversations = betaFlags.observe().map { ConversationsFeature(it.conversationsEnabled) }
    val conversationsCash = betaFlags.observe().map { ConversationCashFeature(it.conversationCashEnabled) }

    val requestKin = betaFlags.observe().map { RequestKinFeature(it.giveRequestsEnabled) }

    val balanceCurrencySelection = betaFlags.observe().map { BalanceCurrencyFeature(it.balanceCurrencySelectionEnabled) }

    suspend fun isEnabled(feature: PrefsBool): Boolean = betaFlags.isEnabled(feature)
}