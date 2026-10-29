package com.flipcash.app.notificationstatus.internal

import androidx.annotation.StringRes
import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.core.R

/**
 * A row's title: its check name, picked by [key] alone, so it reads the same pending, checking,
 * passing or not. The mark and the Fix button show a problem; the title never says it.
 *
 * @param key a [CheckRow.key]
 */
@StringRes
fun checkTitle(key: String): Int = when (key) {
    PermissionKey -> R.string.title_notificationCheckAllowed
    OtherChannelsKey -> R.string.title_notificationCheckOtherCategories
    BatteryKey -> R.string.title_notificationCheckBattery
    BackgroundKey -> R.string.title_notificationCheckBackground
    GooglePlayKey -> R.string.title_notificationCheckPlayServices
    RegistrationKey -> R.string.title_notificationCheckRegistered
    else -> when (StatusChannel.entries.firstOrNull { channelKey(it) == key }) {
        StatusChannel.Chat -> R.string.title_notificationCheckChat
        StatusChannel.DepositWithdrawal -> R.string.title_notificationCheckDeposits
        StatusChannel.BuySell -> R.string.title_notificationCheckBuySell
        StatusChannel.Gain -> R.string.title_notificationCheckGain
        StatusChannel.ContactJoin -> R.string.title_notificationCheckContacts
        StatusChannel.General -> R.string.title_notificationCheckGeneral
        // evaluateNotificationStatus gives it no row: it only gates the test button.
        StatusChannel.TestPush, null -> error("$key has no row")
    }
}

/**
 * The sentence that leads a settled row's detail when it has a problem, "Battery optimization is
 * on", with any explanation on the line after it. Null for a passing row, whose detail is one line,
 * and for a registration still running, whose detail is only "Contacting Flipcash".
 */
@StringRes
fun problemLine(row: CheckRow): Int? = when (row) {
    is CheckRow.Pending, is CheckRow.Rechecking, is CheckRow.OtherChannels -> null
    is CheckRow.Permission -> R.string.subtitle_notificationCheckNotAllowedProblem.takeUnless { row.allowed }
    is CheckRow.BlockedChannel -> when (row.channel) {
        StatusChannel.Chat -> R.string.subtitle_notificationCheckChatOffProblem
        StatusChannel.DepositWithdrawal -> R.string.subtitle_notificationCheckDepositsOffProblem
        StatusChannel.BuySell -> R.string.subtitle_notificationCheckBuySellOffProblem
        StatusChannel.Gain -> R.string.subtitle_notificationCheckGainOffProblem
        StatusChannel.ContactJoin -> R.string.subtitle_notificationCheckContactsOffProblem
        StatusChannel.General -> R.string.subtitle_notificationCheckGeneralOffProblem
        StatusChannel.TestPush -> error("${row.channel.id} has no row")
    }
    is CheckRow.Battery -> R.string.subtitle_notificationCheckBatteryOnProblem.takeIf { row.optimized }
    is CheckRow.Background -> when (row.status) {
        CheckStatus.Fail -> R.string.subtitle_notificationCheckBackgroundRestrictedProblem
        CheckStatus.Warning -> R.string.subtitle_notificationCheckBackgroundLimited
        else -> null
    }
    is CheckRow.GooglePlay -> when (row.status) {
        CheckStatus.Fail -> R.string.subtitle_notificationCheckPlayServicesMissingProblem
        CheckStatus.Warning -> R.string.subtitle_notificationCheckPlayServicesUpdate
        else -> null
    }
    is CheckRow.Registration -> when (val state = row.state) {
        RegistrationState.Checking -> null
        is RegistrationState.Finished -> when (state.outcome) {
            PushRegistrationOutcome.Ok -> null
            PushRegistrationOutcome.NoToken -> R.string.subtitle_notificationCheckNoTokenProblem
            PushRegistrationOutcome.InvalidPushToken -> R.string.subtitle_notificationCheckTokenRejectedProblem
            PushRegistrationOutcome.Transport -> R.string.subtitle_notificationCheckUnreachableProblem
            PushRegistrationOutcome.Failed -> R.string.subtitle_notificationCheckFailedProblem
        }
    }
}
