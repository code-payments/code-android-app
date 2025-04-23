package com.flipcash.app.core.internal.share

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
            val internalIntent = Intent(ShareSheetController.ACTION_CASH_LINK_SHARED).apply {
                putExtra(Intent.EXTRA_CHOSEN_COMPONENT, clickedComponent.packageName)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(internalIntent)
        }
    }
}