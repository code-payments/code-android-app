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
        assertEquals(StatusChannel.entries - StatusChannel.Chat, others.covered)
        assertEquals(Summary.ThingsToFix(1), status.summary)
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
        val status = evaluateNotificationStatus(healthy.blocking(StatusChannel.Chat), RegistrationState.Checking)

        assertEquals(CheckStatus.Checking, status.flipcashRows.single().status)
        assertEquals(Summary.Checking, status.summary)
        assertFalse(status.allPassing)
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
            lastRecord = PushRegistration(Instant.fromEpochSeconds(0), PushRegistrationResult.Ok, PushRegistrationSource.StatusPage),
            tokenSuffix = "a1b2c3",
            formatTime = { "T" },
        )

        assertEquals(
            """
            Flipcash notification report
            2026-10-28 09:41 (UTC-4)
            App 3.4.1 (812) · Android 16 (API 36) · Google Pixel 9

            Notifications allowed: pass (enabled true)
            Channel channel_chat: fail (importance 0, group blocked false)
            Channel channel_deposit_withdrawal: pass (importance 4, group blocked false)
            Channel channel_buy_sell: pass (importance 4, group blocked false)
            Channel channel_gain: pass (importance 4, group blocked false)
            Channel channel_contact_join: pass (importance 4, group blocked false)
            Channel fcm_fallback_notification_channel: pass (importance 4, group blocked false)
            Battery optimization: pass (ignoring true)
            Background activity: warning (restricted false, bucket rare)
            Google Play services: pass (code 0)
            Registered for push: pass (ok; last ok, statusPage, T)

            Token: …a1b2c3 (last 6)
            """.trimIndent(),
            report,
        )
    }
}
