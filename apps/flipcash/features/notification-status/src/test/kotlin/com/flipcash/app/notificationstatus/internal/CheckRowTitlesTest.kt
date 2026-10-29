package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.core.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class CheckRowTitlesTest {

    @Test
    fun `a row keeps its check name as the title whatever its state`() {
        // The title is picked by key, so pending, checking, passing and problem rows share it.
        val rows = listOf(
            CheckRow.Pending(BatteryKey),
            CheckRow.Rechecking(CheckRow.Battery(optimized = true)),
            CheckRow.Battery(optimized = false),
            CheckRow.Battery(optimized = true),
        )
        rows.forEach { assertEquals(R.string.title_notificationCheckBattery, checkTitle(it.key), it.toString()) }
    }

    @Test
    fun `each blocked channel row is titled by its channel`() {
        val expected = mapOf(
            StatusChannel.Chat to R.string.title_notificationCheckChat,
            StatusChannel.DepositWithdrawal to R.string.title_notificationCheckDeposits,
            StatusChannel.BuySell to R.string.title_notificationCheckBuySell,
            StatusChannel.Gain to R.string.title_notificationCheckGain,
            StatusChannel.ContactJoin to R.string.title_notificationCheckContacts,
            StatusChannel.General to R.string.title_notificationCheckGeneral,
        )
        assertEquals(StatusChannel.withRows.toSet(), expected.keys)
        expected.forEach { (channel, title) ->
            assertEquals(title, checkTitle(CheckRow.BlockedChannel(channel).key))
        }
    }

    @Test
    fun `registration row is Registered for Push in every outcome`() {
        val states = listOf(RegistrationState.Checking) +
            PushRegistrationOutcome.entries.map { RegistrationState.Finished(it) }
        states.forEach {
            assertEquals(R.string.title_notificationCheckRegistered, checkTitle(CheckRow.Registration(it).key))
        }
    }

    @Test
    fun `a registration still running has no problem line`() {
        // Its detail is "Contacting Flipcash" alone.
        assertNull(problemLine(CheckRow.Registration(RegistrationState.Checking)))
    }

    @Test
    fun `every phone row key has a title`() {
        (PhoneRowKeys + RegistrationKey).forEach { checkTitle(it) }
    }

    @Test
    fun `problem sentence leads the detail of a warning or fail`() {
        assertEquals(
            R.string.subtitle_notificationCheckBatteryOnProblem,
            problemLine(CheckRow.Battery(optimized = true)),
        )
        assertEquals(
            R.string.subtitle_notificationCheckNotAllowedProblem,
            problemLine(CheckRow.Permission(allowed = false)),
        )
        assertEquals(
            R.string.subtitle_notificationCheckUnreachableProblem,
            problemLine(CheckRow.Registration(RegistrationState.Finished(PushRegistrationOutcome.Transport))),
        )
    }

    @Test
    fun `every warning or fail row has a problem line and no passing row does`() {
        val rows = buildList {
            add(CheckRow.Permission(allowed = true))
            add(CheckRow.Permission(allowed = false))
            StatusChannel.withRows.forEach { add(CheckRow.BlockedChannel(it)) }
            add(CheckRow.OtherChannels(StatusChannel.withRows))
            add(CheckRow.Battery(optimized = true))
            add(CheckRow.Battery(optimized = false))
            listOf("active", "rare").forEach { add(CheckRow.Background(restricted = false, bucket = it)) }
            add(CheckRow.Background(restricted = true, bucket = "active"))
            PlayServices.entries.forEach { add(CheckRow.GooglePlay(it, resolvable = true)) }
            PushRegistrationOutcome.entries.forEach {
                add(CheckRow.Registration(RegistrationState.Finished(it)))
                add(CheckRow.Registration(RegistrationState.Finished(it, lastConfirmedAt = Instant.DISTANT_PAST)))
            }
        }
        rows.forEach { row ->
            if (row.status == CheckStatus.Pass) {
                assertNull(problemLine(row), row.toString())
            } else {
                assertNotNull(problemLine(row), row.toString())
            }
        }
    }
}
