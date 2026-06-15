package com.flipcash.app.internal.toast

import android.content.Context
import android.widget.Toast
import com.flipcash.app.core.toast.SystemToastController

internal class AndroidSystemToastController(
    private val context: Context,
) : SystemToastController {
    private var activeToast: Toast? = null

    private fun show(toast: Toast, replacePrevious: Boolean) {
        if (replacePrevious) {
            activeToast?.cancel()
        }
        activeToast = toast
        toast.show()
    }

    override fun showToast(message: String, replacePrevious: Boolean) {
        show(Toast.makeText(context, message, Toast.LENGTH_LONG), replacePrevious)
    }

    override fun showToast(messageRes: Int, vararg args: Any, replacePrevious: Boolean) {
        show(Toast.makeText(context, context.getString(messageRes, *args), Toast.LENGTH_SHORT), replacePrevious)
    }

    override fun showQuantityToast(pluralRes: Int, quantity: Int, vararg args: Any, replacePrevious: Boolean) {
        show(Toast.makeText(context, context.resources.getQuantityString(pluralRes, quantity, *args), Toast.LENGTH_SHORT), replacePrevious)
    }
}
