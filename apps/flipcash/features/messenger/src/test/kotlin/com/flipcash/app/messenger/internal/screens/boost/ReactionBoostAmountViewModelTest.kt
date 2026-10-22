package com.flipcash.app.messenger.internal.screens.boost

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.models.chat.ChatId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReactionBoostAmountViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatId = ChatId(ByteArray(32) { 1 })

    @Test
    fun `the keypad exposes the resolved split for its message`() = runTest {
        val resolver = mockk<ReactionBoostSplitResolver>()
        val viewModel = ReactionBoostAmountViewModel(mockk(relaxed = true), mockk(relaxed = true), resolver)
        assertEquals(null, viewModel.split.value)

        for (copy in ReactionBoostViewModel.SplitCopy.entries) {
            coEvery { resolver.resolve(chatId, 7) } returns copy
            viewModel.load(chatId, 7)
            assertEquals(copy, viewModel.split.value)
        }
    }
}
