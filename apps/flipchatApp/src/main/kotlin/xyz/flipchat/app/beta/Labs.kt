package xyz.flipchat.app.beta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface Labs {
    fun set(flag: Lab, value: Boolean)
    suspend fun get(flag: Lab): Boolean
    fun observe(flag: Lab): StateFlow<Boolean>
    fun observe(): StateFlow<List<BetaFeature>>
    fun reset(flag: Lab)
    fun reset()
}

object NoOpLabs: Labs {
    override fun set(flag: Lab, value: Boolean) = Unit

    override suspend fun get(flag: Lab): Boolean = false

    override fun observe(flag: Lab): StateFlow<Boolean> = MutableStateFlow(false)

    override fun observe(): StateFlow<List<BetaFeature>> =
        MutableStateFlow(Lab.entries.map { BetaFeature(it, it.default) })

    override fun reset(flag: Lab) = Unit
    override fun reset() = Unit

}