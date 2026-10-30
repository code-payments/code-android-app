package com.getcode.opencode.controllers

import com.getcode.opencode.model.core.errors.GetIntentMetadataError
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.repositories.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class TransactionControllerPollIntentMetadataTest {

    private val repository = mockk<TransactionRepository>(relaxed = true)
    private val controller = TransactionController(
        repository = repository,
        swapRepository = mockk(relaxed = true),
        accountController = mockk(relaxed = true),
    )

    private val received = TransactionMetadata.ReceivePublicPayment(
        source = mockk(relaxed = true),
        quarks = 1,
        isIndirect = false,
        exchangeData = mockk(relaxed = true),
        mint = mockk(relaxed = true),
    )

    @Test
    fun `returns metadata that matches the requested type`() = runTest {
        coEvery { repository.getIntentMetadata(any(), any()) } returns Result.success(received)

        val result = controller.pollIntentMetadata(
            type = TransactionMetadata.PublicPayment::class,
            intentId = mockk(relaxed = true),
            owner = mockk(relaxed = true),
        )

        assertSame(received, result.getOrThrow())
    }

    @Test
    fun `fails fast with UnexpectedType when metadata is a different type`() = runTest {
        coEvery { repository.getIntentMetadata(any(), any()) } returns Result.success(received)

        val result = controller.pollIntentMetadata(
            type = TransactionMetadata.SendPublicPayment::class,
            intentId = mockk(relaxed = true),
            owner = mockk(relaxed = true),
        )

        val error = assertIs<GetIntentMetadataError.UnexpectedType>(result.exceptionOrNull())
        assertEquals("Expected SendPublicPayment metadata but received ReceivePublicPayment", error.message)
        coVerify(exactly = 1) { repository.getIntentMetadata(any(), any()) }
    }
}
