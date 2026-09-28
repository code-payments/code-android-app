package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.libs.emojis.reactions.RecentReactions
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A search's sections load off the main thread, so a slow load for text the reader has since
 * typed past can finish after the load for what they typed next. The picker must keep showing the
 * newer search's results rather than let the late one overwrite them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmojiPickerViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    private val catalog = EmojiCatalogLoader(dispatcher = dispatcher) {
        """
        {
          "categories": ["Animals"],
          "emoji": [
            {"emoji": "🐱", "name": "cat face", "category": "Animals", "version": "1.0", "skinTone": false},
            {"emoji": "🐶", "name": "dog face", "category": "Animals", "version": "1.0", "skinTone": false}
          ]
        }
        """.trimIndent()
    }

    /** Holds each section load at its recents read until the test releases it, in call order. */
    private val recents = object : RecentReactionsStore {
        val pending = mutableListOf<CompletableDeferred<Unit>>()

        override suspend fun record(emoji: String) = Unit
        override suspend fun stats(): Map<String, RecentReactions.Usage> = emptyMap()
        override suspend fun rank(undrawable: Set<String>, limit: Int): List<String> {
            CompletableDeferred<Unit>().also { pending += it }.await()
            return emptyList()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.search(model: EmojiPickerViewModel, text: String) {
        model.stateFlow.value.searchFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private fun EmojiPickerViewModel.resultNames(): List<String> =
        stateFlow.value.sections.single { it.id == EmojiPickerModel.SEARCH_RESULTS_ID }.entries.map { it.name }

    @Test
    fun `a search typed past that finishes late does not replace the newer results`() = runTest(scheduler) {
        val model = EmojiPickerViewModel(
            dispatchers = TestDispatcherProvider(dispatcher),
            emojiCatalogLoader = catalog,
            recentReactionsStore = recents,
            glyphProbe = { true },
        )
        advanceUntilIdle()
        recents.pending[0].complete(Unit) // the opening, unsearched load
        advanceUntilIdle()

        search(model, "cat")
        val cat = recents.pending[1]
        search(model, "dog")
        val dog = recents.pending[2]

        dog.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("dog face"), model.resultNames())

        cat.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("dog face"), model.resultNames())
    }
}
