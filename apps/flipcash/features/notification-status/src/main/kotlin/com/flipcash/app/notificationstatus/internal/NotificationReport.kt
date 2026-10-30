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
    /** The store's newest record, from any call site. */
    lastRecord: PushRegistration?,
    /** The token this page's latest run sent; null when it sent none, and so saved nothing. */
    tokenSuffix: String?,
    formatTime: (Instant) -> String,
): String = buildString {
    appendLine("Flipcash notification report")
    appendLine(header.generatedAt)
    appendLine("App ${header.appVersion} · ${header.osVersion} · ${header.device}")
    appendLine()

    val status = evaluateNotificationStatus(device, registration)
    fun CheckStatus.label() = name.lowercase()

    appendLine("Notifications Allowed: ${CheckRow.Permission(device.notificationsAllowed).status.label()} (enabled ${device.notificationsAllowed})")
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
                appendLine("Battery Optimization: ${row.status.label()} (ignoring ${device.ignoringBatteryOptimizations})")
            is CheckRow.Background ->
                appendLine("Background Activity: ${row.status.label()} (restricted ${row.restricted}, bucket ${row.bucket})")
            is CheckRow.GooglePlay ->
                appendLine("Google Play Services: ${row.status.label()} (code ${device.playServicesCode})")
            is CheckRow.Registration -> {
                // This page's latest run, never an older saved record.
                val line = when (val state = row.state) {
                    RegistrationState.Checking -> row.status.label()
                    is RegistrationState.Finished -> {
                        // A run saves a record exactly when it sent a token.
                        val detail = lastRecord?.takeIf { tokenSuffix != null }?.let { record ->
                            // "<result>, <source>, <time>", the result left out when it is ok.
                            listOfNotNull(
                                record.result.takeIf { it != PushRegistrationResult.Ok }?.key,
                                record.source.key,
                                formatTime(record.at),
                            ).joinToString()
                        } ?: state.outcome.key
                        "${row.status.label()} ($detail)"
                    }
                }
                appendLine("Registered for Push: $line")
                // From any call site, so it survives a run that saved nothing.
                appendLine("Last ok: ${lastRecord?.lastOkAt?.let(formatTime) ?: "never"}")
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
