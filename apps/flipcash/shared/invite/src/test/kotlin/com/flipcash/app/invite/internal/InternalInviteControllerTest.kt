package com.flipcash.app.invite.internal

import android.content.Context
import android.content.pm.PackageManager
import com.flipcash.app.invite.InviteChannelType
import com.flipcash.libs.coroutines.TestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InternalInviteControllerTest {

    private val packageManager: PackageManager = mockk {
        every { queryIntentActivities(any(), any<Int>()) } returns emptyList()
        every { getApplicationInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
    }
    private val context: Context = mockk {
        every { packageManager } returns this@InternalInviteControllerTest.packageManager
    }

    @Test
    fun `construction does not query the package manager`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        val controller = controller(dispatcher)

        assertTrue(controller.channels.value.isEmpty())
        verify(exactly = 0) { context.packageManager }
    }

    @Test
    fun `channels resolve on the io dispatcher`() = runTest {
        val controller = controller(StandardTestDispatcher(testScheduler))

        advanceUntilIdle()

        assertEquals(
            listOf(InviteChannelType.Sms, InviteChannelType.More),
            controller.channels.value.map { it.type },
        )
    }

    private fun controller(dispatcher: TestDispatcher) = InternalInviteController(
        context = context,
        resources = mockk(relaxed = true),
        shareController = mockk(relaxed = true),
        dispatchers = TestDispatcherProvider(dispatcher),
    )
}
