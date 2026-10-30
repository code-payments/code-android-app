package com.flipcash.app.notificationstatus.internal

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.flipcash.app.notifications.NotificationChannels
import com.flipcash.app.notifications.currentStandbyBucket
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads the phone's notification settings into a [DeviceSnapshot]. */
internal class DeviceSnapshotReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(): DeviceSnapshot {
        val notificationManager = context.getSystemService<NotificationManager>()!!
        val playServices = GoogleApiAvailability.getInstance()
        val playCode = playServices.isGooglePlayServicesAvailable(context)

        return DeviceSnapshot(
            notificationsAllowed = notificationsAllowed(notificationManager),
            channels = channels(notificationManager),
            ignoringBatteryOptimizations = context.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            backgroundRestricted = context.getSystemService<ActivityManager>()?.isBackgroundRestricted == true,
            standbyBucket = context.currentStandbyBucket(),
            playServices = when (playCode) {
                ConnectionResult.SUCCESS -> PlayServices.Available
                ConnectionResult.SERVICE_UPDATING,
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> PlayServices.NeedsUpdate
                else -> PlayServices.Unavailable
            },
            playServicesCode = playCode,
            playServicesResolvable = playServices.isUserResolvableError(playCode),
        )
    }

    private fun notificationsAllowed(manager: NotificationManager): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    private fun channels(manager: NotificationManager): List<ChannelSetting> {
        // Channels are created at app start; this covers a check that runs first.
        NotificationChannels.createAll(context, NotificationManagerCompat.from(context))
        return StatusChannel.entries.mapNotNull { status ->
            val channel = manager.getNotificationChannel(status.id) ?: return@mapNotNull null
            ChannelSetting(
                channel = status,
                importance = channel.importance,
                groupBlocked = channel.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked } == true,
            )
        }
    }
}
