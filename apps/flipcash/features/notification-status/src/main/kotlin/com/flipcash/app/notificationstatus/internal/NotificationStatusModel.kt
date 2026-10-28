package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.app.notifications.NotificationChannels
import kotlin.time.Instant

// The status page's model: a snapshot of the phone's settings plus the registration result in,
// ordered rows and a summary out. No Android types, so it can move to shared code later.

enum class CheckStatus { Pass, Warning, Fail, Checking, Unknown }

/**
 * The channels the page checks, in the order their rows show. A blocked channel is a Fail or a
 * Warning by what the user misses, not by the channel's importance: a missed contact-join alert
 * costs little, so it is only a Warning.
 */
enum class StatusChannel(val id: String, val whenBlocked: CheckStatus) {
    Chat(NotificationChannels.CHANNEL_CHAT, CheckStatus.Fail),
    DepositWithdrawal(NotificationChannels.CHANNEL_DEPOSIT_WITHDRAWAL, CheckStatus.Fail),
    BuySell(NotificationChannels.CHANNEL_BUY_SELL, CheckStatus.Fail),
    Gain(NotificationChannels.CHANNEL_GAIN, CheckStatus.Fail),
    ContactJoin(NotificationChannels.CHANNEL_CONTACT_JOIN, CheckStatus.Warning),
    General(NotificationChannels.CHANNEL_DEFAULT, CheckStatus.Warning),
}

/** @param importance the channel's raw importance; [IMPORTANCE_NONE] means the user turned it off. */
data class ChannelSetting(
    val channel: StatusChannel,
    val importance: Int,
    val groupBlocked: Boolean = false,
) {
    // A blocked group counts as every channel in it being blocked.
    val isBlocked: Boolean get() = importance == IMPORTANCE_NONE || groupBlocked

    companion object {
        const val IMPORTANCE_NONE = 0
    }
}

enum class PlayServices { Available, NeedsUpdate, Unavailable }

data class DeviceSnapshot(
    val notificationsAllowed: Boolean,
    val channels: List<ChannelSetting>,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
    /** A `bucketLabel` value: "active", "working_set", "frequent", "rare", "restricted", … */
    val standbyBucket: String,
    val playServices: PlayServices,
    val playServicesCode: Int = 0,
    val playServicesResolvable: Boolean = false,
)

sealed interface RegistrationState {
    data object Checking : RegistrationState

    /** The check was interrupted before it finished. */
    data object Unknown : RegistrationState

    /** @param lastConfirmedAt when the saved record last said `ok`, if it did. */
    data class Finished(
        val outcome: PushRegistrationOutcome,
        val lastConfirmedAt: Instant? = null,
    ) : RegistrationState
}

sealed interface Fix {
    /** Ask for the permission when Android still allows it, else open the app's notification settings. */
    data object TurnOn : Fix
    data class ChannelSettings(val channel: StatusChannel) : Fix
    data object BatterySettings : Fix
    data object AppDetails : Fix
    data object ResolvePlayServices : Fix
    data object TryAgain : Fix
}

sealed interface CheckRow {
    val status: CheckStatus
    val fix: Fix?

    data class Permission(val allowed: Boolean) : CheckRow {
        override val status = if (allowed) CheckStatus.Pass else CheckStatus.Fail
        override val fix = if (allowed) null else Fix.TurnOn
    }

    data class BlockedChannel(val channel: StatusChannel) : CheckRow {
        override val status = channel.whenBlocked
        override val fix = Fix.ChannelSettings(channel)
    }

    /** The channels that are on and have no row of their own. */
    data class OtherChannels(val covered: List<StatusChannel>) : CheckRow {
        override val status = CheckStatus.Pass
        override val fix: Fix? = null
    }

    data class Battery(val optimized: Boolean) : CheckRow {
        override val status = if (optimized) CheckStatus.Warning else CheckStatus.Pass
        override val fix = if (optimized) Fix.BatterySettings else null
    }

    data class Background(val restricted: Boolean, val bucket: String) : CheckRow {
        override val status = when {
            restricted -> CheckStatus.Fail
            bucket in limitingBuckets -> CheckStatus.Warning
            else -> CheckStatus.Pass
        }
        override val fix = if (status == CheckStatus.Pass) null else Fix.AppDetails
    }

    data class GooglePlay(val state: PlayServices, val resolvable: Boolean) : CheckRow {
        override val status = when (state) {
            PlayServices.Available -> CheckStatus.Pass
            PlayServices.NeedsUpdate -> CheckStatus.Warning
            PlayServices.Unavailable -> CheckStatus.Fail
        }
        override val fix = if (status != CheckStatus.Pass && resolvable) Fix.ResolvePlayServices else null
    }

    data class Registration(val state: RegistrationState) : CheckRow {
        override val status = when (state) {
            RegistrationState.Checking -> CheckStatus.Checking
            RegistrationState.Unknown -> CheckStatus.Unknown
            is RegistrationState.Finished -> when (state.outcome) {
                PushRegistrationOutcome.Ok -> CheckStatus.Pass
                // The server's copy is probably still good if it was confirmed before.
                PushRegistrationOutcome.Transport ->
                    if (state.lastConfirmedAt != null) CheckStatus.Warning else CheckStatus.Fail
                PushRegistrationOutcome.NoToken,
                PushRegistrationOutcome.InvalidPushToken,
                PushRegistrationOutcome.Failed -> CheckStatus.Fail
            }
        }
        override val fix = if (status == CheckStatus.Warning || status == CheckStatus.Fail) Fix.TryAgain else null
    }
}

sealed interface Summary {
    data object Working : Summary
    data object Checking : Summary
    data object NotificationsOff : Summary
    data class ThingsToFix(val count: Int) : Summary
    data class ThingsToCheck(val count: Int) : Summary
}

data class NotificationStatus(
    val summary: Summary,
    /** "ON THIS PHONE" */
    val phoneRows: List<CheckRow>,
    /** "WITH FLIPCASH" */
    val flipcashRows: List<CheckRow>,
) {
    val rows: List<CheckRow> get() = phoneRows + flipcashRows

    /** When true the page folds both groups into one "N checks passed" row. */
    val allPassing: Boolean get() = summary == Summary.Working

    /** A local test needs only the permission, not a working registration. */
    val canSendTest: Boolean
        get() = phoneRows.none { it is CheckRow.Permission && it.status == CheckStatus.Fail }
}

private val limitingBuckets = setOf("rare", "restricted")

fun evaluateNotificationStatus(
    device: DeviceSnapshot,
    registration: RegistrationState,
): NotificationStatus {
    val phoneRows = buildList {
        add(CheckRow.Permission(device.notificationsAllowed))
        // Channel rows mean nothing while notifications are off app-wide.
        if (device.notificationsAllowed) {
            val byChannel = device.channels.associateBy { it.channel }
            val blocked = StatusChannel.entries.filter { byChannel[it]?.isBlocked == true }
            blocked.forEach { add(CheckRow.BlockedChannel(it)) }
            val covered = StatusChannel.entries - blocked.toSet()
            if (covered.isNotEmpty()) add(CheckRow.OtherChannels(covered))
        }
        add(CheckRow.Battery(optimized = !device.ignoringBatteryOptimizations))
        add(CheckRow.Background(device.backgroundRestricted, device.standbyBucket))
        add(CheckRow.GooglePlay(device.playServices, device.playServicesResolvable))
    }
    val flipcashRows = listOf(CheckRow.Registration(registration))
    val rows = phoneRows + flipcashRows

    val fails = rows.count { it.status == CheckStatus.Fail }
    // An interrupted check is something to look at, not a pass.
    val warnings = rows.count { it.status == CheckStatus.Warning || it.status == CheckStatus.Unknown }
    val summary = when {
        rows.any { it.status == CheckStatus.Checking } -> Summary.Checking
        !device.notificationsAllowed -> Summary.NotificationsOff
        fails > 0 -> Summary.ThingsToFix(fails)
        warnings > 0 -> Summary.ThingsToCheck(warnings)
        else -> Summary.Working
    }

    return NotificationStatus(summary, phoneRows, flipcashRows)
}
