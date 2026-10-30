package com.flipcash.app.messenger.internal

import com.flipcash.analytics.SilenceDuration
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.services.models.SilenceUserError
import com.flipcash.features.messenger.R
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/** The confirmation toast follows the server's answer: it names the member and the length picked, and a failure leaves it to the error bar. */
@OptIn(ExperimentalCoroutinesApi::class)
class SilenceUserViewModelTest {

    private val chatController = mockk<ChatController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val analytics = RecordingAnalytics()
    private val toasts = mockk<SystemToastController>(relaxed = true)
    private val chatId = ChatId(ByteArray(32) { 3 }.toList())
    private val userId = listOf<Byte>(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun viewModel() = SilenceUserViewModel(chatController, resources, toasts, analytics)

    @Test
    fun `each option toasts its own length after a successful silence`() {
        coEvery { chatController.silenceUser(chatId, userId, any()) } returns Result.success(Unit)

        SilenceOption.entries.forEach { viewModel().silence(chatId, userId, "Ada", it) }

        verify(exactly = 1) { toasts.showToast(R.string.toast_silenced_oneHour, "Ada", replacePrevious = true) }
        verify(exactly = 1) { toasts.showToast(R.string.toast_silenced_eightHours, "Ada", replacePrevious = true) }
        verify(exactly = 1) { toasts.showToast(R.string.toast_silenced_oneWeek, "Ada", replacePrevious = true) }
        verify(exactly = 1) { toasts.showToast(R.string.toast_silenced_forever, "Ada", replacePrevious = true) }
    }

    @Test
    fun `a failed silence shows no toast`() {
        coEvery { chatController.silenceUser(chatId, userId, any()) } returns Result.failure(Throwable("denied"))

        viewModel().silence(chatId, userId, "Ada", SilenceOption.OneHour)

        verify(exactly = 0) { toasts.showToast(any<Int>(), *anyVararg(), replacePrevious = any()) }
    }

    @Test
    fun `each option sends Member Silenced with its own duration`() {
        coEvery { chatController.silenceUser(chatId, userId, any()) } returns Result.success(Unit)

        SilenceOption.entries.forEach { viewModel().silence(chatId, userId, "Ada", it) }

        assertEquals(
            listOf(
                SilenceDuration.ONE_HOUR,
                SilenceDuration.EIGHT_HOURS,
                SilenceDuration.ONE_WEEK,
                SilenceDuration.FOREVER,
            ).map { GroupEvents.memberSilenced(duration = it, state = AnalyticsState.SUCCESS, error = null) },
            analytics.events,
        )
    }

    @Test
    fun `a failed silence sends Member Silenced with the error`() {
        coEvery { chatController.silenceUser(chatId, userId, any()) } returns
            Result.failure(SilenceUserError.CannotRestrictCreator())

        viewModel().silence(chatId, userId, "Ada", SilenceOption.OneWeek)

        assertEquals(
            listOf(
                GroupEvents.memberSilenced(
                    duration = SilenceDuration.ONE_WEEK,
                    state = AnalyticsState.FAILURE,
                    error = "CannotRestrictCreator",
                )
            ),
            analytics.events,
        )
    }
}
