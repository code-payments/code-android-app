package dev.bmcreations.tipkit.engines

import androidx.compose.runtime.staticCompositionLocalOf
import dev.bmcreations.tipkit.NoOpTipProvider
import dev.bmcreations.tipkit.Tip
import dev.bmcreations.tipkit.TipProvider

interface TipInterface

val LocalTipsEngine = staticCompositionLocalOf<TipsEngine?> { null }

class TipsEngine(
    private val eventsEngine: EventEngine
) {
    var tips: TipInterface = object : TipInterface {}
        private set

    private var provider: TipProvider = NoOpTipProvider()

    val flows: MutableMap<String, List<Tip>> = mutableMapOf()

    fun configure(implementation: TipInterface) {
        tips = implementation
    }

    fun setProvider(provider: TipProvider) {
        this.provider = provider
    }

    fun invalidateAllTips() {
        provider.dismiss()
        eventsEngine.clearCompletions()
        eventsEngine.removeAllOccurrences()
    }

    fun associateTipWithFlow(tip: Tip, order: Int, flowId: String) {
        val tipsInFlow = flows[flowId].orEmpty()
        if (tipsInFlow.isEmpty() || tipsInFlow.lastIndex < order) {
            flows[flowId] = tipsInFlow + tip
        } else {
            tipsInFlow.toMutableList()[order] = tip
            flows[flowId] = tipsInFlow
        }
    }
}