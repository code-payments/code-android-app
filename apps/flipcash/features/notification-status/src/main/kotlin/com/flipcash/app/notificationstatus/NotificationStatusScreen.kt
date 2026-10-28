package com.flipcash.app.notificationstatus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.notificationstatus.internal.Fix
import com.flipcash.app.notificationstatus.internal.NotificationStatusContent
import com.flipcash.app.notificationstatus.internal.NotificationStatusViewModel
import com.flipcash.app.notificationstatus.internal.NotificationStatusViewModel.Event
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberNotificationPermission
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun NotificationStatusScreen() {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val viewModel = hiltViewModel<NotificationStatusViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val permission = rememberNotificationPermission { viewModel.dispatchEvent(Event.Refresh) }

    // Covers coming back from system settings after a Fix.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.dispatchEvent(Event.Refresh)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_notificationStatus),
            onBackIconClicked = { navigator.pop() },
        )
        NotificationStatusContent(
            state = state,
            onFix = { fix ->
                when (fix) {
                    Fix.TurnOn -> {
                        // Ask while Android still allows it; after that only settings can turn it on.
                        val askable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            permission.status in setOf(PermissionResult.NotRequested, PermissionResult.Denied)
                        if (askable) permission.launch() else context.openAppNotificationSettings()
                    }
                    is Fix.ChannelSettings -> context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, fix.channel.id),
                    )
                    Fix.BatterySettings ->
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    Fix.AppDetails -> context.startActivity(IntentUtils.appSettings(context))
                    Fix.ResolvePlayServices -> state.device?.let { device ->
                        GoogleApiAvailability.getInstance()
                            .getErrorResolutionPendingIntent(context, device.playServicesCode, 0)
                            ?.send()
                    }
                    Fix.TryAgain -> viewModel.dispatchEvent(Event.TryAgain)
                }
            },
            onToggleExpanded = { viewModel.dispatchEvent(Event.ToggleExpanded) },
            onSendTest = { viewModel.dispatchEvent(Event.SendTest) },
            onShareReport = { viewModel.dispatchEvent(Event.ShareReportRequested) },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.ShareReport>()
            .onEach { context.startActivity(IntentUtils.share(it.text)) }
            .launchIn(this)
    }
}

private fun Context.openAppNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}
