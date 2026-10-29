package com.flipcash.app.messenger.internal.request

import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.PaymentRequests
import androidx.lifecycle.ViewModelStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `viewModelScope` runs on `Main.immediate`, so the deck collector emits inside the constructor.
 * An unconfined main dispatcher reproduces that: the ViewModel has to be fully initialized by then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRequestsViewModelTest {

    private val requests = mockk<PaymentRequests>(relaxed = true) {
        every { state } returns MutableStateFlow(emptyMap())
        every { missing } returns MutableStateFlow(emptySet())
    }
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `constructs when the deck collector emits in place`() = runTest {
        val store = ViewModelStore()
        val viewModel = ChatRequestsViewModel(requests, chatCoordinator, userManager)
        store.put("requests", viewModel)

        assertTrue(viewModel.deck.value.isEmpty())
        // Cancels the minute ticker, which would otherwise keep runTest from going idle.
        store.clear()
    }
}
