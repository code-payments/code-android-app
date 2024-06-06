package dev.bmcreations.tipkit

import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import dev.bmcreations.tipkit.data.TipPresentation

val LocalTipProvider =
    staticCompositionLocalOf<TipProvider> { NoOpTipProvider() }

abstract class TipProvider {
    abstract fun show(data: TipPresentation)
    abstract fun dismiss()
    abstract fun onActionClicked(action: TipAction)

    open val isTipShowing: Boolean = false

    open val debugEnabled: Boolean = true
    open val logTag: String = "TipProvider"

    fun debugLog(message: String) {
        if (debugEnabled) {
            Log.d(logTag, message)
        }
    }
}

class NoOpTipProvider : TipProvider() {
    override fun show(data: TipPresentation) = Unit
    override fun dismiss() = Unit

    override fun onActionClicked(action: TipAction) = Unit
}