package com.flipcash.app.notificationstatus.internal

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.push.PushRegistrar
import com.flipcash.app.auth.push.PushRegistration
import com.flipcash.app.auth.push.PushRegistrationAttempt
import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.app.auth.push.PushRegistrationResult
import com.flipcash.app.auth.push.PushRegistrationSource
import com.flipcash.app.notifications.TestNotificationPoster
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@HiltViewModel
internal class NotificationStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: DeviceSnapshotReader,
    private val registrar: PushRegistrar,
    private val testPoster: TestNotificationPoster,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel<NotificationStatusViewModel.State, NotificationStatusViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val device: DeviceSnapshot? = null,
        val registration: RegistrationState = RegistrationState.Checking,
        val lastRegistration: PushRegistration? = null,
        val tokenSuffix: String? = null,
        val expanded: Boolean = false,
        val testSent: Boolean = false,
    ) {
        val status: NotificationStatus get() = evaluateNotificationStatus(device, registration)
    }

    sealed interface Event {
        /** Re-read the phone's settings: on open and on every return to the foreground. */
        data object Refresh : Event
        data class DeviceRead(val device: DeviceSnapshot) : Event
        data object RegistrationStarted : Event
        data class RegistrationFinished(val attempt: PushRegistrationAttempt, val saved: PushRegistration?) : Event
        data object TryAgain : Event
        data object ToggleExpanded : Event
        data object SendTest : Event
        data object TestScheduled : Event
        data object ShareReportRequested : Event
        data class ShareReport(val text: String) : Event
    }

    private var registration: Job? = null

    init {
        eventFlow
            .filterIsInstance<Event.Refresh>()
            .onEach { dispatchEvent(Event.DeviceRead(withContext(dispatchers.IO) { reader.read() })) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.TryAgain>()
            .onEach { register() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendTest>()
            .onEach {
                // Permission may have gone while the page was open; re-check before scheduling.
                val device = withContext(dispatchers.IO) { reader.read() }
                dispatchEvent(Event.DeviceRead(device))
                if (device.notificationsAllowed) {
                    testPoster.schedule()
                    dispatchEvent(Event.TestScheduled)
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareReportRequested>()
            .onEach { dispatchEvent(Event.ShareReport(report())) }
            .launchIn(viewModelScope)

        dispatchEvent(Event.Refresh)
        register()
    }

    private fun register() {
        if (registration?.isActive == true) return
        val state = stateFlow.value
        // A rejected token won't be accepted again; replace it first.
        val resetToken = (state.registration as? RegistrationState.Finished)?.outcome ==
            PushRegistrationOutcome.InvalidPushToken
        registration = viewModelScope.launch {
            dispatchEvent(Event.RegistrationStarted)
            val attempt = registrar.register(
                source = PushRegistrationSource.StatusPage,
                resetToken = resetToken,
                timeout = RegistrationTimeout,
            )
            dispatchEvent(Event.RegistrationFinished(attempt, registrar.lastSaved()))
        }
    }

    private suspend fun report(): String {
        val state = stateFlow.value
        val device = state.device ?: withContext(dispatchers.IO) { reader.read() }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val zone = ZoneId.systemDefault()
        fun format(time: ZonedDateTime) =
            "${time.format(ReportTimeFormat)} (UTC${utcOffset(time)})"
        val header = ReportHeader(
            generatedAt = format(ZonedDateTime.now(zone)),
            appVersion = "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        return buildNotificationReport(
            header = header,
            device = device,
            registration = state.registration,
            lastRecord = state.lastRegistration,
            tokenSuffix = state.tokenSuffix,
            formatTime = { format(ZonedDateTime.ofInstant(it.toJavaInstant(), zone)) },
        )
    }

    internal companion object {
        val RegistrationTimeout = 10.seconds

        private val ReportTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        // "-4", "+5:30", "+0"
        private fun utcOffset(time: ZonedDateTime): String {
            val seconds = time.offset.totalSeconds
            val sign = if (seconds < 0) "-" else "+"
            val hours = Math.abs(seconds) / 3600
            val minutes = Math.abs(seconds) % 3600 / 60
            return if (minutes == 0) "$sign$hours" else "$sign$hours:%02d".format(minutes)
        }

        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.DeviceRead -> { { copy(device = event.device) } }
                is Event.RegistrationStarted -> { { copy(registration = RegistrationState.Checking) } }
                is Event.RegistrationFinished -> { {
                    val attempt = event.attempt
                    val lastConfirmedAt: Instant? = attempt.previous
                        ?.takeIf { it.result == PushRegistrationResult.Ok }
                        ?.at
                    copy(
                        registration = RegistrationState.Finished(attempt.outcome, lastConfirmedAt),
                        lastRegistration = event.saved,
                        tokenSuffix = attempt.tokenSuffix ?: tokenSuffix,
                    )
                } }
                is Event.ToggleExpanded -> { { copy(expanded = !expanded) } }
                is Event.TestScheduled -> { { copy(testSent = true) } }
                is Event.Refresh,
                is Event.TryAgain,
                is Event.SendTest,
                is Event.ShareReportRequested,
                is Event.ShareReport -> { { this } }
            }
        }
    }
}
