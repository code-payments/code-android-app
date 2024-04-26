package com.getcode.network.repository

import com.getcode.model.BuyModuleFeature
import com.getcode.model.PrefsBool
import com.getcode.model.RequestKinFeature
import com.getcode.model.TipCardFeature
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Collates [BetaOptions] with server availability (stored in [PrefRepository]).
 */
class FeatureRepository @Inject constructor(
    betaFlags: BetaFlagsRepository,
    prefRepository: PrefRepository,
) {
    val buyModule = combine(
        betaFlags.observe().map { it.buyModuleEnabled },
        prefRepository.observeOrDefault(PrefsBool.BUY_MODULE_AVAILABLE, false)
        ) { enabled, available -> BuyModuleFeature(enabled, available) }

    val tipCards = betaFlags.observe().map { TipCardFeature(it.tipsEnabled) }

    val requestKin = betaFlags.observe().map { RequestKinFeature(it.giveRequestsEnabled) }
}