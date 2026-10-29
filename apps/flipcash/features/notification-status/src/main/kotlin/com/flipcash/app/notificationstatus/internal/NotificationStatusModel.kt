package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.app.notifications.NotificationChannels
import kotlin.time.Instant

// The status page's model: a snapshot of the phone's settings plus the registration result in,
// ordered rows and a summary out. No Android types, so it can move to shared code later.

enum class CheckStatus { Pass, Warning, Fail, Checking }

/**
 * The channels the page checks, in the order their rows show. A blocked channel is a Fail or a
 * Warning by what the user misses, not by the channel's importance: a missed contact-join alert
 * costs little, so it is only a Warning.
 *
 * [hasRow] is false for a channel that only gates the test button: it gets no row of its own and
 * isn't listed among the other categories, but the report still covers it.
 */
enum class StatusChannel(val id: String, val whenBlocked: CheckStatus, val hasRow: Boolean = true) {
    Chat(NotificationChannels.CHANNEL_CHAT, CheckStatus.Fail),
    DepositWithdrawal(NotificationChannels.CHANNEL_DEPOSIT_WITHDRAWAL, CheckStatus.Fail),
    BuySell(NotificationChannels.CHANNEL_BUY_SELL, CheckStatus.Fail),
    Gain(NotificationChannels.CHANNEL_GAIN, CheckStatus.Fail),
    ContactJoin(NotificationChannels.CHANNEL_CONTACT_JOIN, CheckStatus.Warning),
    General(NotificationChannels.CHANNEL_DEFAULT, CheckStatus.Warning),
    TestPush(NotificationChannels.CHANNEL_TEST_PUSH, CheckStatus.Warning, hasRow = false),
    ;

    companion object {
        /** The channels with a row of their own, or a place in the other-categories list. */
        val withRows: List<StatusChannel> = entries.filter { it.hasRow }
    }
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

    /** Stays the same while the row's status changes, so the page can update it in place. */
    val key: String

    /** A phone check whose settings haven't been read yet. */
    data class Pending(override val key: String) : CheckRow {
        override val status = CheckStatus.Checking
        override val fix: Fix? = null
    }

    /**
     * A row being checked again: it keeps its place, title and fix until the new result is in. The
     * page shows the fix disabled while the row is Checking.
     */
    data class Rechecking(val previous: CheckRow) : CheckRow {
        override val key get() = previous.key
        override val status = CheckStatus.Checking
        override val fix: Fix? get() = previous.fix
    }

    data class Permission(val allowed: Boolean) : CheckRow {
        override val key get() = PermissionKey
        override val status = if (allowed) CheckStatus.Pass else CheckStatus.Fail
        override val fix = if (allowed) null else Fix.TurnOn
    }

    data class BlockedChannel(val channel: StatusChannel) : CheckRow {
        override val key get() = channelKey(channel)
        override val status = channel.whenBlocked
        override val fix = Fix.ChannelSettings(channel)
    }

    /** The channels that are on and have no row of their own. */
    data class OtherChannels(val covered: List<StatusChannel>) : CheckRow {
        override val key get() = OtherChannelsKey
        override val status = CheckStatus.Pass
        override val fix: Fix? = null
    }

    data class Battery(val optimized: Boolean) : CheckRow {
        override val key get() = BatteryKey
        override val status = if (optimized) CheckStatus.Warning else CheckStatus.Pass
        override val fix = if (optimized) Fix.BatterySettings else null
    }

    data class Background(val restricted: Boolean, val bucket: String) : CheckRow {
        override val key get() = BackgroundKey
        override val status = when {
            restricted -> CheckStatus.Fail
            bucket in limitingBuckets -> CheckStatus.Warning
            else -> CheckStatus.Pass
        }
        override val fix = if (status == CheckStatus.Pass) null else Fix.AppDetails
    }

    data class GooglePlay(val state: PlayServices, val resolvable: Boolean) : CheckRow {
        override val key get() = GooglePlayKey
        override val status = when (state) {
            PlayServices.Available -> CheckStatus.Pass
            PlayServices.NeedsUpdate -> CheckStatus.Warning
            PlayServices.Unavailable -> CheckStatus.Fail
        }
        override val fix = if (status != CheckStatus.Pass && resolvable) Fix.ResolvePlayServices else null
    }

    /** @param previous the page's last finished run, whose fix stays while [state] is Checking. */
    data class Registration(
        val state: RegistrationState,
        val previous: RegistrationState.Finished? = null,
    ) : CheckRow {
        override val key get() = RegistrationKey
        override val status = when (state) {
            RegistrationState.Checking -> CheckStatus.Checking
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
        override val fix: Fix? = when (status) {
            CheckStatus.Warning, CheckStatus.Fail -> Fix.TryAgain
            CheckStatus.Checking -> previous?.let { Registration(it).fix }
            CheckStatus.Pass -> null
        }
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
    /** [TestPushChannel] is turned off. It has no row, so this is read off the phone's settings. */
    val testChannelOff: Boolean = false,
) {
    val rows: List<CheckRow> get() = phoneRows + flipcashRows

    /** When true the page folds both groups into one "N checks passed" row. */
    val allPassing: Boolean get() = summary == Summary.Working

    /**
     * Why the test button is disabled, or null when it isn't. The server's test push needs the
     * permission and the channel it lands on, not a working registration: the server may still
     * hold an earlier token.
     */
    val testBlocked: TestBlocked?
        get() {
            // A re-run doesn't take the test away while the settings are read again.
            val settled = phoneRows.map { (it as? CheckRow.Rechecking)?.previous ?: it }
            return when {
                settled.none { it is CheckRow.Permission && it.status == CheckStatus.Pass } ->
                    TestBlocked.NotificationsOff
                testChannelOff -> TestBlocked.ChannelOff
                else -> null
            }
        }

    val canSendTest: Boolean get() = testBlocked == null
}

enum class TestBlocked { NotificationsOff, ChannelOff }

/** The page marks its test as in flight, so the push posts on a channel of its own that pops up. */
val TestPushChannel = StatusChannel.TestPush

const val PermissionKey = "permission"
const val OtherChannelsKey = "channels"
const val BatteryKey = "battery"
const val BackgroundKey = "background"
const val GooglePlayKey = "play"
const val RegistrationKey = "registration"

fun channelKey(channel: StatusChannel): String = "channel:${channel.id}"

/**
 * Every row "ON THIS PHONE" can hold, in the order it shows them. A refresh can add or remove
 * channel rows; the order stays put, so rows animate in and out rather than reshuffling.
 */
val PhoneRowKeys: List<String> =
    listOf(PermissionKey) + StatusChannel.withRows.map(::channelKey) +
        listOf(OtherChannelsKey, BatteryKey, BackgroundKey, GooglePlayKey)

// The rows a phone with nothing blocked shows: the likely final set, laid out while the read runs.
private val PendingPhoneRows: List<CheckRow> =
    listOf(PermissionKey, OtherChannelsKey, BatteryKey, BackgroundKey, GooglePlayKey).map(CheckRow::Pending)

private val limitingBuckets = setOf("rare", "restricted")

/**
 * [device] is null until the phone's settings have been read. [rechecking] marks a re-run in
 * progress: [device] is the last read, and every row it gives shows Checking in its place until
 * the new read arrives. [previousRegistration] is the page's last finished registration, kept while
 * [registration] runs again.
 */
fun evaluateNotificationStatus(
    device: DeviceSnapshot?,
    registration: RegistrationState,
    rechecking: Boolean = false,
    previousRegistration: RegistrationState.Finished? = null,
): NotificationStatus {
    val phoneRows: List<CheckRow> = if (device == null) PendingPhoneRows else buildList<CheckRow> {
        add(CheckRow.Permission(device.notificationsAllowed))
        // Channel rows mean nothing while notifications are off app-wide.
        if (device.notificationsAllowed) {
            val byChannel = device.channels.associateBy { it.channel }
            val blocked = StatusChannel.withRows.filter { byChannel[it]?.isBlocked == true }
            blocked.forEach { add(CheckRow.BlockedChannel(it)) }
            val covered = StatusChannel.withRows - blocked.toSet()
            if (covered.isNotEmpty()) add(CheckRow.OtherChannels(covered))
        }
        add(CheckRow.Battery(optimized = !device.ignoringBatteryOptimizations))
        add(CheckRow.Background(device.backgroundRestricted, device.standbyBucket))
        add(CheckRow.GooglePlay(device.playServices, device.playServicesResolvable))
    }.let { rows -> if (rechecking) rows.map(CheckRow::Rechecking) else rows }
    val flipcashRows = listOf(CheckRow.Registration(registration, previousRegistration))
    val rows = phoneRows + flipcashRows

    val fails = rows.count { it.status == CheckStatus.Fail }
    val warnings = rows.count { it.status == CheckStatus.Warning }
    // A finished problem wins over a row still checking: Fail, then Warning, then Checking.
    val summary = when {
        rows.any { it is CheckRow.Permission && it.status == CheckStatus.Fail } -> Summary.NotificationsOff
        fails > 0 -> Summary.ThingsToFix(fails)
        warnings > 0 -> Summary.ThingsToCheck(warnings)
        rows.any { it.status == CheckStatus.Checking } -> Summary.Checking
        else -> Summary.Working
    }

    val testChannelOff = device?.channels?.firstOrNull { it.channel == TestPushChannel }?.isBlocked == true

    return NotificationStatus(summary, phoneRows, flipcashRows, testChannelOff)
}
