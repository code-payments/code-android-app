package com.flipcash.app.messenger.internal

import com.flipcash.analytics.MuteDuration
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.services.models.chat.ChatType

/** `Chat Muted` for the answer MuteChat gave to the option picked. */
internal fun FlipcashAnalytics.trackMuted(chatType: ChatType, option: MuteOption, result: Result<*>) {
    track(
        ChatEvents.muted(
            chatType = chatType.analytics,
            duration = option.analytics,
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
        )
    )
}

/** `Chat Unmuted` for the answer UnmuteChat gave. */
internal fun FlipcashAnalytics.trackUnmuted(chatType: ChatType, result: Result<*>) {
    track(
        ChatEvents.unmuted(
            chatType = chatType.analytics,
            state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
            error = result.exceptionOrNull()?.chatResult,
        )
    )
}

private val MuteOption.analytics: MuteDuration
    get() = when (this) {
        MuteOption.OneHour -> MuteDuration.ONE_HOUR
        MuteOption.EightHours -> MuteDuration.EIGHT_HOURS
        MuteOption.OneWeek -> MuteDuration.ONE_WEEK
        MuteOption.Forever -> MuteDuration.ALWAYS
    }
