package com.flipcash.app.blocklist

import app.cash.turbine.test
import com.flipcash.app.persistence.sources.BlockedUserDataSource
import com.flipcash.services.controllers.BlocklistController
import com.flipcash.shared.chat.ChatCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlocklistCoordinatorTest {

    private val userId = listOf<Byte>(1, 2, 3)
    private val blocked = MutableStateFlow(false)

    private val blocklistController = mockk<BlocklistController>()
    private val dataSource = mockk<BlockedUserDataSource>(relaxed = true) {
        every { observeIsBlocked(any()) } returns blocked
    }
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { generateChatId(any()) } returns Result.failure(IllegalStateException())
    }

    private val coordinator = BlocklistCoordinator(
        blocklistController = blocklistController,
        profileController = mockk(),
        dataSource = dataSource,
        chatCoordinator = chatCoordinator,
        userManager = mockk(relaxed = true),
    )

    @Test
    fun `observeIsBlocked passes the data source value through and drops repeats`() = runTest {
        coordinator.observeIsBlocked(userId).test {
            assertFalse(awaitItem())
            blocked.value = true
            assertTrue(awaitItem())
            blocked.value = false
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `blockUser writes the row through on success`() = runTest {
        coEvery { blocklistController.blockUser(userId) } returns Result.success(Unit)

        assertTrue(coordinator.blockUser(userId).isSuccess)

        coVerify { dataSource.insert(userId, any()) }
    }

    @Test
    fun `blockUser leaves the table alone on failure`() = runTest {
        coEvery { blocklistController.blockUser(userId) } returns Result.failure(IllegalStateException())

        assertTrue(coordinator.blockUser(userId).isFailure)

        coVerify(exactly = 0) { dataSource.insert(any(), any()) }
    }

    @Test
    fun `unblock deletes the row on success`() = runTest {
        coEvery { blocklistController.unblockUser(userId) } returns Result.success(Unit)

        assertTrue(coordinator.unblock(userId).isSuccess)

        coVerify { dataSource.delete(userId) }
    }

    @Test
    fun `unblock keeps the row on failure`() = runTest {
        coEvery { blocklistController.unblockUser(userId) } returns Result.failure(IllegalStateException())

        assertEquals(true, coordinator.unblock(userId).isFailure)

        coVerify(exactly = 0) { dataSource.delete(any()) }
    }
}
