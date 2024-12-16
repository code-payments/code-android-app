package xyz.flipchat.app.beta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface BetaFlags {
    fun set(flag: BetaFlag, value: Boolean)
    suspend fun get(flag: BetaFlag): Boolean
    fun observe(flag: BetaFlag): StateFlow<Boolean>
    fun observe(): StateFlow<List<BetaFeature>>
    fun reset(flag: BetaFlag)
    fun reset()
}

object NoOpBetaFlags: BetaFlags {
    override fun set(flag: BetaFlag, value: Boolean) = Unit

    override suspend fun get(flag: BetaFlag): Boolean = false

    override fun observe(flag: BetaFlag): StateFlow<Boolean> = MutableStateFlow(false)

    override fun observe(): StateFlow<List<BetaFeature>> =
        MutableStateFlow(BetaFlag.entries.map { BetaFeature(it, it.default) })

    override fun reset(flag: BetaFlag) = Unit
    override fun reset() = Unit

}