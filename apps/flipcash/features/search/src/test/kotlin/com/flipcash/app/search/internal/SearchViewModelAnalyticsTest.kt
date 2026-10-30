package com.flipcash.app.search.internal

import com.flipcash.analytics.SearchResultType
import com.flipcash.analytics.events.SearchEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.libs.coroutines.TestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/** `Search: Opened` is sent once when the screen's view model starts, and each tap sends its own result type. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelAnalyticsTest {

    private val analytics = RecordingAnalytics()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): SearchViewModel {
        val recentStore = mockk<RecentSearchStore>(relaxed = true) {
            every { recents } returns MutableStateFlow(emptyList())
        }
        return SearchViewModel(
            exchange = mockk(relaxed = true),
            chatCoordinator = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
            tokenCoordinator = mockk(relaxed = true),
            resources = mockk(relaxed = true),
            dispatchers = TestDispatcherProvider(dispatcher),
            messageDataSource = mockk(relaxed = true),
            currencySearch = mockk(relaxed = true),
            recentStore = recentStore,
            analytics = analytics,
        )
    }

    @Test
    fun `opening Search sends Search Opened once and nothing else`() {
        viewModel()

        assertEquals(listOf(SearchEvents.opened()), analytics.events)
    }

    @Test
    fun `each result type sends Result Tapped after Opened`() {
        val model = viewModel()

        SearchResultType.entries.forEach(model::resultTapped)

        assertEquals(
            listOf(SearchEvents.opened()) + SearchResultType.entries.map { SearchEvents.resultTapped(it) },
            analytics.events,
        )
    }
}
