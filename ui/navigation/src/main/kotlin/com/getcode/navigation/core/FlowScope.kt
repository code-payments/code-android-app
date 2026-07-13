package com.getcode.navigation.core

import android.os.Parcelable

/**
 * The bounded navigation scope a [CodeNavigator] belongs to when it is a flow's inner navigator.
 *
 * A step never touches this directly — it calls CodeNavigator.navigateBackWithResult /
 * CodeNavigator.dismiss, which delegate here. The implementation lives in
 * [com.getcode.navigation.flow.FlowHost], where the flow-exit callback and enclosing-sheet
 * context are already available. This keeps [CodeNavigator] free of flow/sheet mechanics.
 */
interface FlowScope {
    /** True when this flow is the root content of a bottom sheet. */
    val isSheetRoot: Boolean

    /** Complete the flow, delivering [result] to whoever launched it. */
    fun exitWithResult(result: Parcelable)

    /**
     * Leave the entire scope (the whole flow, animating the sheet out first when the flow is a
     * sheet root). Equivalent to a user pressing the close-X.
     *
     * For a sheet-root flow, the enclosing sheet entry is removed from the outer backstack by the
     * animation handler before the flow's exit callback runs — so the host's onExit must not pop
     * the sheet entry again.
     */
    fun dismiss()
}
