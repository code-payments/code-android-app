package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistration
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
        val channelStatus = when {
            setting == null -> CheckStatus.Unknown
            setting.isBlocked -> channel.whenBlocked
            else -> CheckStatus.Pass
        }
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
                val outcome = when (val state = row.state) {
                    RegistrationState.Checking -> "checking"
                    RegistrationState.Unknown -> "interrupted"
                    is RegistrationState.Finished -> state.outcome.name.lowercase()
                }
                val saved = lastRecord?.let { "${it.result.key}, ${it.source.key}, ${formatTime(it.at)}" } ?: "none saved"
                appendLine("Registered for push: ${row.status.label()} ($outcome; last $saved)")
            }
            is CheckRow.Permission, is CheckRow.BlockedChannel, is CheckRow.OtherChannels -> Unit
        }
    }
    appendLine()
    append("Token: ")
    append(tokenSuffix?.let { "…$it (last 6)" } ?: "none")
}
