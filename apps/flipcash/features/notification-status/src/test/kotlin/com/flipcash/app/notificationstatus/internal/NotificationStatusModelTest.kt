package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistration
import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.app.auth.push.PushRegistrationResult
import com.flipcash.app.auth.push.PushRegistrationSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// Numbered tests follow the spec's Test cases table (docs/specs/notification-status.md).
class NotificationStatusModelTest {

    private val allOn = StatusChannel.entries.map { ChannelSetting(it, importance = 4) }

    private val healthy = DeviceSnapshot(
        notificationsAllowed = true,
        channels = allOn,
        ignoringBatteryOptimizations = true,
        backgroundRestricted = false,
        standbyBucket = "active",
        playServices = PlayServices.Available,
    )

    private val registered = RegistrationState.Finished(PushRegistrationOutcome.Ok)

    private fun DeviceSnapshot.blocking(vararg channels: StatusChannel) = copy(
        channels = this.channels.map { if (it.channel in channels) it.copy(importance = 0) else it },
    )

    @Test
    fun `1 every check passes - working and folded into six rows`() {
        val status = evaluateNotificationStatus(healthy, registered)

        assertEquals(Summary.Working, status.summary)
        assertTrue(status.allPassing)
        // a1, a3, a4, a5, a6, a7
        assertEquals(6, status.rows.size)
        assertTrue(status.canSendTest)
    }

    @Test
    fun `2 permission denied - notifications off, channel rows hidden, test disabled`() {
        val status = evaluateNotificationStatus(
            healthy.copy(notificationsAllowed = false).blocking(StatusChannel.Chat),
            registered,
        )

        assertEquals(Summary.NotificationsOff, status.summary)
        assertTrue(status.rows.none { it is CheckRow.BlockedChannel || it is CheckRow.OtherChannels })
        // The registration row and the power rows stay.
        assertTrue(status.rows.any { it is CheckRow.Battery })
        assertTrue(status.rows.any { it is CheckRow.Background })
        assertTrue(status.rows.any { it is CheckRow.Registration })
        assertEquals(Fix.TurnOn, status.rows.first().fix)
        assertFalse(status.canSendTest)
    }

    @Test
    fun `5 chat blocked - its own Fail row with a channel fix, a3 lists the other five`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.Chat), registered)

        val chat = status.phoneRows.filterIsInstance<CheckRow.BlockedChannel>().single()
        assertEquals(StatusChannel.Chat, chat.channel)
        assertEquals(CheckStatus.Fail, chat.status)
        assertEquals(Fix.ChannelSettings(StatusChannel.Chat), chat.fix)
        val others = status.phoneRows.filterIsInstance<CheckRow.OtherChannels>().single()
        assertEquals(StatusChannel.withRows - StatusChannel.Chat, others.covered)
        assertEquals(Summary.ThingsToFix(1), status.summary)
        // The test push doesn't land on the chat channel, so chat being off doesn't block it.
        assertTrue(status.canSendTest)
    }

    @Test
    fun `test channel blocked - the test is disabled, with no row of its own and not in a3`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.TestPush), registered)

        assertTrue(status.phoneRows.none { it is CheckRow.BlockedChannel })
        val others = status.phoneRows.filterIsInstance<CheckRow.OtherChannels>().single()
        assertFalse(StatusChannel.TestPush in others.covered)
        assertEquals(TestBlocked.ChannelOff, status.testBlocked)
        assertFalse(status.canSendTest)
        // Nothing the user misses: the page reads as working, only the test waits on it.
        assertEquals(Summary.Working, status.summary)
    }

    @Test
    fun `a3 never lists the test channel`() {
        val others = evaluateNotificationStatus(healthy, registered)
            .phoneRows.filterIsInstance<CheckRow.OtherChannels>().single()

        assertEquals(StatusChannel.withRows, others.covered)
        assertFalse(StatusChannel.TestPush in others.covered)
    }

    @Test
    fun `general blocked - its own row, and the test stays enabled on its own channel`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.General), registered)

        val general = status.phoneRows.filterIsInstance<CheckRow.BlockedChannel>().single()
        assertEquals(Fix.ChannelSettings(StatusChannel.General), general.fix)
        assertEquals(null, status.testBlocked)
    }

    @Test
    fun `notifications off wins over the test channel being off as the reason the test is disabled`() {
        val status = evaluateNotificationStatus(
            healthy.copy(notificationsAllowed = false).blocking(StatusChannel.TestPush),
            registered,
        )

        assertEquals(TestBlocked.NotificationsOff, status.testBlocked)
    }

    @Test
    fun `a blocked channel other than chat leaves the test enabled`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.Gain), registered)

        assertEquals(null, status.testBlocked)
    }

    @Test
    fun `6 contact join blocked - Warning, not Fail`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.ContactJoin), registered)

        val row = status.phoneRows.filterIsInstance<CheckRow.BlockedChannel>().single()
        assertEquals(CheckStatus.Warning, row.status)
        assertEquals(Summary.ThingsToCheck(1), status.summary)
    }

    @Test
    fun `7 blocked group - each of its channels gets its own row`() {
        val social = setOf(StatusChannel.Chat, StatusChannel.ContactJoin)
        val device = healthy.copy(
            channels = allOn.map { if (it.channel in social) it.copy(groupBlocked = true) else it },
        )

        val status = evaluateNotificationStatus(device, registered)

        assertEquals(social, status.phoneRows.filterIsInstance<CheckRow.BlockedChannel>().map { it.channel }.toSet())
        assertEquals(Summary.ThingsToFix(1), status.summary)
    }

    @Test
    fun `a3 is hidden when every channel is blocked`() {
        val status = evaluateNotificationStatus(healthy.blocking(*StatusChannel.entries.toTypedArray()), registered)

        assertTrue(status.rows.none { it is CheckRow.OtherChannels })
        assertEquals(6, status.rows.count { it is CheckRow.BlockedChannel })
    }

    @Test
    fun `8 battery optimization on and bucket rare - two things to check`() {
        val status = evaluateNotificationStatus(
            healthy.copy(ignoringBatteryOptimizations = false, standbyBucket = "rare"),
            registered,
        )

        assertEquals(Summary.ThingsToCheck(2), status.summary)
        assertEquals(Fix.BatterySettings, status.rows.filterIsInstance<CheckRow.Battery>().single().fix)
        assertEquals(Fix.AppDetails, status.rows.filterIsInstance<CheckRow.Background>().single().fix)
        assertTrue(status.canSendTest)
    }

    @Test
    fun `9 background restricted - a5 fails`() {
        val status = evaluateNotificationStatus(healthy.copy(backgroundRestricted = true), registered)

        assertEquals(CheckStatus.Fail, status.rows.filterIsInstance<CheckRow.Background>().single().status)
    }

    @Test
    fun `10 invalid push token - registration fails with try again`() {
        val status = evaluateNotificationStatus(
            healthy,
            RegistrationState.Finished(PushRegistrationOutcome.InvalidPushToken),
        )

        val row = status.flipcashRows.single()
        assertEquals(CheckStatus.Fail, row.status)
        assertEquals(Fix.TryAgain, row.fix)
        // A local test doesn't need the registration.
        assertTrue(status.canSendTest)
    }

    @Test
    fun `11 timeout with no saved ok - Fail`() {
        val status = evaluateNotificationStatus(healthy, RegistrationState.Finished(PushRegistrationOutcome.Transport))

        assertEquals(CheckStatus.Fail, status.flipcashRows.single().status)
    }

    @Test
    fun `11b timeout with a saved ok - Warning`() {
        val status = evaluateNotificationStatus(
            healthy,
            RegistrationState.Finished(PushRegistrationOutcome.Transport, lastConfirmedAt = Instant.fromEpochSeconds(1)),
        )

        assertEquals(CheckStatus.Warning, status.flipcashRows.single().status)
        assertEquals(Fix.TryAgain, status.flipcashRows.single().fix)
        assertEquals(Summary.ThingsToCheck(1), status.summary)
    }

    @Test
    fun `12 registration in flight - row and summary are checking`() {
        val status = evaluateNotificationStatus(healthy, RegistrationState.Checking)

        assertEquals(CheckStatus.Checking, status.flipcashRows.single().status)
        assertEquals(Summary.Checking, status.summary)
        assertFalse(status.allPassing)
    }

    @Test
    fun `a finished fail wins over registration still checking`() {
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.Chat), RegistrationState.Checking)

        assertEquals(Summary.ThingsToFix(1), status.summary)
    }

    @Test
    fun `a finished warning wins over registration still checking`() {
        val status = evaluateNotificationStatus(healthy.copy(ignoringBatteryOptimizations = false), RegistrationState.Checking)

        assertEquals(Summary.ThingsToCheck(1), status.summary)
    }

    @Test
    fun `a failed registration wins over phone rows still checking`() {
        val status = evaluateNotificationStatus(
            device = null,
            registration = RegistrationState.Finished(PushRegistrationOutcome.Failed),
        )

        assertEquals(Summary.ThingsToFix(1), status.summary)
    }

    @Test
    fun `13 one fail and one warning - summary uses the fail`() {
        val status = evaluateNotificationStatus(
            healthy.copy(ignoringBatteryOptimizations = false, backgroundRestricted = true),
            registered,
        )

        assertEquals(Summary.ThingsToFix(1), status.summary)
    }

    @Test
    fun `rows keep table order - a failing row is not moved up`() {
        val status = evaluateNotificationStatus(
            healthy.copy(backgroundRestricted = true).blocking(StatusChannel.Gain),
            registered,
        )

        assertEquals(
            listOf(
                CheckRow.Permission::class, CheckRow.BlockedChannel::class, CheckRow.OtherChannels::class,
                CheckRow.Battery::class, CheckRow.Background::class, CheckRow.GooglePlay::class,
            ),
            status.phoneRows.map { it::class },
        )
    }

    @Test
    fun `settings not read yet - the likely rows are laid out as checking`() {
        val status = evaluateNotificationStatus(device = null, registration = RegistrationState.Checking)

        assertEquals(Summary.Checking, status.summary)
        assertTrue(status.rows.all { it.status == CheckStatus.Checking })
        // Each placeholder's key is the row that replaces it on a healthy phone, so nothing moves.
        assertEquals(
            evaluateNotificationStatus(healthy, registered).rows.map { it.key },
            status.rows.map { it.key },
        )
        assertFalse(status.canSendTest)
    }

    @Test
    fun `check again - every row shows checking in its place, none added or removed`() {
        val devices = listOf(healthy, healthy.copy(notificationsAllowed = false), healthy.blocking(StatusChannel.Chat))
        devices.forEach { device ->
            val before = evaluateNotificationStatus(device, registered)
            val during = evaluateNotificationStatus(device, RegistrationState.Checking, rechecking = true)

            assertEquals(before.rows.map { it.key }, during.rows.map { it.key })
            assertTrue(during.rows.all { it.status == CheckStatus.Checking })
            assertEquals(Summary.Checking, during.summary)
            // The test button doesn't flicker off while the permission is read again.
            assertEquals(before.canSendTest, during.canSendTest)
        }
    }

    @Test
    fun `check again - fixes stay on their rows while they re-check`() {
        val failed = RegistrationState.Finished(PushRegistrationOutcome.NoToken)
        val device = healthy.blocking(StatusChannel.Chat).copy(ignoringBatteryOptimizations = false)
        val before = evaluateNotificationStatus(device, failed)
        val during = evaluateNotificationStatus(device, RegistrationState.Checking, rechecking = true, previousRegistration = failed)

        assertEquals(before.rows.map { it.fix }, during.rows.map { it.fix })
        assertEquals(Fix.TryAgain, during.flipcashRows.single().fix)
        assertTrue(during.rows.all { it.status == CheckStatus.Checking })
    }

    @Test
    fun `registration checking with no earlier run on the page has no fix`() {
        val status = evaluateNotificationStatus(healthy, RegistrationState.Checking)
        assertEquals(null, status.flipcashRows.single().fix)

        // An earlier pass leaves nothing to keep.
        val afterOk = evaluateNotificationStatus(healthy, RegistrationState.Checking, previousRegistration = registered)
        assertEquals(null, afterOk.flipcashRows.single().fix)
    }

    @Test
    fun `every phone row follows the fixed key order`() {
        val devices = listOf(
            healthy,
            healthy.blocking(StatusChannel.Chat, StatusChannel.Gain),
            healthy.blocking(*StatusChannel.entries.toTypedArray()),
            healthy.copy(notificationsAllowed = false),
        )
        devices.forEach { device ->
            val keys = evaluateNotificationStatus(device, registered).phoneRows.map { it.key }
            assertEquals(PhoneRowKeys.filter { it in keys }, keys)
        }
    }

    @Test
    fun `play services needing an update is a warning, missing is a fail`() {
        val update = evaluateNotificationStatus(
            healthy.copy(playServices = PlayServices.NeedsUpdate, playServicesResolvable = true),
            registered,
        ).rows.filterIsInstance<CheckRow.GooglePlay>().single()
        assertEquals(CheckStatus.Warning, update.status)
        assertEquals(Fix.ResolvePlayServices, update.fix)

        val missing = evaluateNotificationStatus(healthy.copy(playServices = PlayServices.Unavailable), registered)
            .rows.filterIsInstance<CheckRow.GooglePlay>().single()
        assertEquals(CheckStatus.Fail, missing.status)
        assertEquals(null, missing.fix)
    }

    @Test
    fun `report carries raw values and only the token suffix`() {
        val report = buildNotificationReport(
            header = ReportHeader("2026-10-28 09:41 (UTC-4)", "3.4.1 (812)", "Android 16 (API 36)", "Google Pixel 9"),
            device = healthy.blocking(StatusChannel.Chat).copy(standbyBucket = "rare"),
            registration = registered,
            lastRecord = PushRegistration(
                Instant.fromEpochSeconds(0),
                PushRegistrationResult.Ok,
                PushRegistrationSource.StatusPage,
                lastOkAt = Instant.fromEpochSeconds(0),
            ),
            tokenSuffix = "a1b2c3",
            formatTime = { "2026-10-28 09:41" },
        )

        assertEquals(
            """
            Flipcash notification report
            2026-10-28 09:41 (UTC-4)
            App 3.4.1 (812) · Android 16 (API 36) · Google Pixel 9

            Notifications Allowed: pass (enabled true)
            Channel channel_chat: fail (importance 0, group blocked false)
            Channel channel_deposit_withdrawal: pass (importance 4, group blocked false)
            Channel channel_buy_sell: pass (importance 4, group blocked false)
            Channel channel_gain: pass (importance 4, group blocked false)
            Channel channel_contact_join: pass (importance 4, group blocked false)
            Channel fcm_fallback_notification_channel: pass (importance 4, group blocked false)
            Channel channel_test_push: pass (importance 4, group blocked false)
            Battery Optimization: pass (ignoring true)
            Background Activity: warning (restricted false, bucket rare)
            Google Play Services: pass (code 0)
            Registered for Push: pass (statusPage, 2026-10-28 09:41)
            Last ok: 2026-10-28 09:41

            Token: …a1b2c3 (last 6)
            """.trimIndent(),
            report,
        )
    }

    @Test
    fun `report describes this page's latest run, never an older saved record`() {
        val olderOk = Instant.fromEpochSeconds(100)
        fun registrationLines(
            registration: RegistrationState,
            record: PushRegistration?,
            tokenSuffix: String?,
        ) = buildNotificationReport(
            header = ReportHeader("", "", "", ""),
            device = healthy,
            registration = registration,
            lastRecord = record,
            tokenSuffix = tokenSuffix,
            formatTime = { if (it == olderOk) "2026-10-27 08:00" else "2026-10-28 09:41" },
        ).lines().dropWhile { !it.startsWith("Registered for Push") }.take(2)

        // This run sent a token and saved a failure; the ok from earlier survives on its own line.
        assertEquals(
            listOf("Registered for Push: fail (invalidPushToken, statusPage, 2026-10-28 09:41)", "Last ok: 2026-10-27 08:00"),
            registrationLines(
                RegistrationState.Finished(PushRegistrationOutcome.InvalidPushToken),
                PushRegistration(Instant.fromEpochSeconds(0), PushRegistrationResult.InvalidPushToken, PushRegistrationSource.StatusPage, lastOkAt = olderOk),
                tokenSuffix = "a1b2c3",
            ),
        )
        // No token, so this run saved nothing: the older login record is not printed as its result.
        assertEquals(
            listOf("Registered for Push: fail (noToken)", "Last ok: 2026-10-27 08:00"),
            registrationLines(
                RegistrationState.Finished(PushRegistrationOutcome.NoToken),
                PushRegistration(olderOk, PushRegistrationResult.Ok, PushRegistrationSource.Login, lastOkAt = olderOk),
                tokenSuffix = null,
            ),
        )
        assertEquals(
            listOf("Registered for Push: fail (noToken)", "Last ok: never"),
            registrationLines(RegistrationState.Finished(PushRegistrationOutcome.NoToken), null, tokenSuffix = null),
        )
        // Still running.
        assertEquals(
            listOf("Registered for Push: checking", "Last ok: 2026-10-27 08:00"),
            registrationLines(
                RegistrationState.Checking,
                PushRegistration(olderOk, PushRegistrationResult.Ok, PushRegistrationSource.Login, lastOkAt = olderOk),
                tokenSuffix = "a1b2c3",
            ),
        )
    }
}
