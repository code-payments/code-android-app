package com.flipcash.app.shareable.internal

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.chooser.ChooserResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flipcash.app.shareable.ShareSheetController.Companion.ACTION_CASH_LINK_SHARED
import com.flipcash.app.shareable.ShareSheetController.Companion.EXTRA_COPIED_TO_CLIPBOARD
import com.getcode.utils.TraceType
import com.getcode.utils.trace


class ShareBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Retrieve the selected component (if provided, it means user selected an app in the share sheet)
        val clickedComponent: ComponentName? = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        if (clickedComponent != null) {
            trace(
                tag = "ShareSheet",
                message = "User shared cashlink to: ${clickedComponent.packageName}",
                type = TraceType.User
            )

            // Send internal broadcast
            val internalIntent = Intent(ACTION_CASH_LINK_SHARED).apply {
                putExtra(Intent.EXTRA_CHOSEN_COMPONENT, clickedComponent.packageName)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(internalIntent)
            return
        }

        // No component means the user took one of the Sharesheet's own actions instead of picking an
        // app -- the Copy button in the preview header is one. Since API 35 those arrive here as
        // EXTRA_CHOOSER_RESULT with nothing else attached; before that the Sharesheet reports them
        // not at all, and InternalShareSheetController's clipboard check is the only way to notice.
        if (isSystemCopy(intent)) {
            trace(
                tag = "ShareSheet",
                message = "User copied cashlink to clipboard",
                type = TraceType.User
            )

            val internalIntent = Intent(ACTION_CASH_LINK_SHARED).apply {
                putExtra(EXTRA_COPIED_TO_CLIPBOARD, true)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(internalIntent)
        }
    }

    private fun isSystemCopy(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val result = intent.getParcelableExtra(Intent.EXTRA_CHOOSER_RESULT, ChooserResult::class.java)
        return result?.type == ChooserResult.CHOOSER_RESULT_COPY
    }
}
