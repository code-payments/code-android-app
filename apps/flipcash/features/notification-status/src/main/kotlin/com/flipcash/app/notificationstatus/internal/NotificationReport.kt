package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistration
import com.flipcash.app.auth.push.PushRegistrationResult
import kotlin.time.Instant

data class ReportHeader(
    /** e.g. "2026-10-28 09:41 (UTC-4)" */
    val generatedAt: String,
    val appVersion: String,
    val osVersion: String,
    val device: String,
)

/**
 * The plain-text report support asks for. Every check line carries its raw value in brackets so it
 * reads without a screenshot. It never includes the full token, the installation ID, the account ID
 * or any user names.
 */
fun buildNotificationReport(
    header: ReportHeader,
    device: DeviceSnapshot,
    registration: RegistrationState,
    lastRecord: PushRegistration?,
    tokenSuffix: String?,
    formatTime: (Instant) -> String,
): String = buildString {
    appendLine("Flipcash notification report")
    appendLine(header.generatedAt)
    appendLine("App ${header.appVersion} · ${header.osVersion} · ${header.device}")
    appendLine()

    val status = evaluateNotificationStatus(device, registration)
    fun CheckStatus.label() = name.lowercase()

    appendLine("Notifications allowed: ${CheckRow.Permission(device.notificationsAllowed).status.label()} (enabled ${device.notificationsAllowed})")
    // Every channel, whether or not the page shows a row for it.
    StatusChannel.entries.forEach { channel ->
        val setting = device.channels.firstOrNull { it.channel == channel }
        // As on the page: only a blocked channel is a problem.
        val channelStatus = if (setting?.isBlocked == true) channel.whenBlocked else CheckStatus.Pass
        val raw = setting?.let { "importance ${it.importance}, group blocked ${it.groupBlocked}" } ?: "missing"
        appendLine("Channel ${channel.id}: ${channelStatus.label()} ($raw)")
    }
    status.rows.forEach { row ->
        when (row) {
            is CheckRow.Battery ->
                appendLine("Battery optimization: ${row.status.label()} (ignoring ${device.ignoringBatteryOptimizations})")
            is CheckRow.Background ->
                appendLine("Background activity: ${row.status.label()} (restricted ${row.restricted}, bucket ${row.bucket})")
            is CheckRow.GooglePlay ->
                appendLine("Google Play services: ${row.status.label()} (code ${device.playServicesCode})")
            is CheckRow.Registration -> {
                // "<status> (<result>, <source>, <time>)", the result left out when it is ok.
                val saved = lastRecord?.let { record ->
                    listOfNotNull(
                        record.result.takeIf { it != PushRegistrationResult.Ok }?.key,
                        record.source.key,
                        formatTime(record.at),
                    ).joinToString()
                } ?: "none saved"
                appendLine("Registered for push: ${row.status.label()} ($saved)")
            }
            // The report always reads the device first, so it never holds a Pending or Rechecking row.
            is CheckRow.Permission, is CheckRow.BlockedChannel, is CheckRow.OtherChannels,
            is CheckRow.Pending, is CheckRow.Rechecking -> Unit
        }
    }
    appendLine()
    append("Token: ")
    append(tokenSuffix?.let { "…$it (last 6)" } ?: "none")
}
