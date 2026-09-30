package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.internal.WorkManagerRosterReconcileScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** A chat has at most one full roster read queued, and it waits for a network. */
@RunWith(RobolectricTestRunner::class)
class RosterReconcileSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val subject = WorkManagerRosterReconcileScheduler(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun queued(chatId: ChatId): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerRosterReconcileScheduler.uniqueName(chatId))
            .get()

    @Test
    fun `scheduling twice queues one read`() {
        val chatId = ChatId("c0ffee")

        subject.schedule(chatId)
        subject.schedule(chatId)

        val work = queued(chatId).single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `each chat gets its own read`() {
        subject.schedule(ChatId("c0ffee"))
        subject.schedule(ChatId("decade"))

        assertEquals(1, queued(ChatId("c0ffee")).size)
        assertEquals(1, queued(ChatId("decade")).size)
    }
}
