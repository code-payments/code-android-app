package com.getcode.navigation.core

import android.os.Parcelable

/**
 * The bounded navigation scope a [CodeNavigator] belongs to when it is a flow's inner navigator.
 *
 * A step never touches this directly. The CodeNavigator methods that delegate here
 * (navigateBackWithResult / dismiss) are added in a later task, and this interface's
 * implementation lives in [com.getcode.navigation.flow.FlowHost], where the flow-exit callback
 * and enclosing-sheet context are already available. This keeps [CodeNavigator] free of
 * flow/sheet mechanics.
 */
interface FlowScope {
    /** True when this flow is the root content of a bottom sheet. */
    val isSheetRoot: Boolean

    /** Complete the flow, delivering [result] to whoever launched it. */
    fun exitWithResult(result: Parcelable)

    /**
     * Leave the entire scope (the whole flow, animating the sheet out first when the flow is a
     * sheet root). Equivalent to a user pressing the close-X.
     */
    fun dismiss()
}
