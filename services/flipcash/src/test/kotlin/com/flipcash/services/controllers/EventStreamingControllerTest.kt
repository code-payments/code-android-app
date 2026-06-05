package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class EventStreamingControllerTest {

    private val repository = FakeEventStreamingRepository()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = EventStreamingController(repository, userManager)

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    @Test
    fun `open does nothing when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        controller.open(scope)

        // No stream opened
        assert(!repository.opened)
    }

    @Test
    fun `open creates stream when account cluster exists`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        controller.open(scope)

        assert(repository.opened)
    }

    @Test
    fun `close destroys the stream reference`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        controller.open(scope)

        controller.close()

        assertNotNull(repository.lastStreamRef)
        verify { repository.lastStreamRef!!.destroy() }
    }

    @Test
    fun `chatUpdates SharedFlow is accessible`() {
        assertNotNull(controller.chatUpdates)
    }
}

private class FakeEventStreamingRepository : EventStreamingRepository {
    var opened = false
    var lastStreamRef: EventStreamReference? = null

    override fun openEventStream(
        scope: CoroutineScope,
        owner: Ed25519.KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onError: (Throwable) -> Unit,
    ): EventStreamReference {
        opened = true
        val ref = mockk<EventStreamReference>(relaxed = true)
        lastStreamRef = ref
        return ref
    }
}
