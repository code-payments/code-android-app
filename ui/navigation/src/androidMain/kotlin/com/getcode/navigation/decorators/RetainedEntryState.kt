package com.getcode.navigation.decorators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/**
 * Book-keeping for [RetainedEntryState]: which content keys keep their state after their entry is
 * gone, and when that stops being true.
 *
 * Split out from the decorators because the ordering here is the whole difficulty, and none of it
 * needs Compose to be exercised. A pop is reported only once the entry's content has left
 * composition, so it lands a transition later than the back stack change that caused it — which
 * means [release] routinely runs *before* the pops it is meant to discard. Marking the ledger
 * released rather than only emptying it is what makes those late arrivals clear instead of being
 * held onto for the next account.
 *
 * @param retains whether a content key is one whose state should outlive its entry.
 */
internal class RetentionLedger(private val retains: (contentKey: Any) -> Boolean) {
    private val held = linkedSetOf<Any>()
    private var released = false

    /** A retainable entry is on screen again, so retention resumes. */
    fun onRendered(contentKey: Any) {
        if (retains(contentKey)) released = false
    }

    /** True when [contentKey]'s state should survive the pop that just happened. */
    fun onPopped(contentKey: Any): Boolean {
        val keep = !released && retains(contentKey)
        if (keep) held += contentKey
        return keep
    }

    /**
     * Everything currently held, dropped. Pops still in flight are dropped too, until the next
     * retainable entry renders.
     */
    fun release(): List<Any> {
        released = true
        val dropped = held.toList()
        held.clear()
        return dropped
    }
}

/**
 * Per-entry ViewModel and `rememberSaveable` state for a [androidx.navigation3.ui.NavDisplay], with
 * an opt-in for the entries whose state should survive them.
 *
 * Nav3 scopes both to the entry and destroys both with it, which is right for a screen that was
 * pushed and popped. It is wrong for the tab homes: a tab press replaces the whole back stack, so
 * every tab came back cold — a fresh ViewModel with its default state, and a list scrolled back to
 * the top. Retaining the entries on the back stack instead would fix that, but it also puts them in
 * front of back: [androidx.navigation3.ui.NavDisplay] enables its back handler on
 * `scene.previousEntries.isNotEmpty()`, so anything left underneath for state reasons is also
 * something back walks through. Holding the state here keeps the back stack — and back — as they
 * were.
 *
 * The mechanism is the one AndroidX documents on
 * [androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner]: a store outlives the
 * composition that used it and is destroyed only by an explicit
 * [ViewModelStoreProvider.clearKey]. These decorators are the stock pair with that call, and
 * [SaveableStateHolder.removeState], skipped for the retained keys.
 *
 * State is held against the entry's `contentKey`, so a tab comes back to what it had only if it
 * comes back under the same key.
 */
@Stable
class RetainedEntryState internal constructor(
    private val saveableStateHolder: SaveableStateHolder,
    private val viewModelStoreProvider: ViewModelStoreProvider,
    private val ledger: RetentionLedger,
) {
    /**
     * Outermost first. Saveable state wraps the ViewModel store because a store's
     * [androidx.lifecycle.SavedStateHandle] is restored through the saved state registry the
     * saveable decorator provides.
     */
    val decorators: List<NavEntryDecorator<NavKey>> = listOf(
        NavEntryDecorator(
            onPop = { contentKey ->
                if (!ledger.onPopped(contentKey)) saveableStateHolder.removeState(contentKey)
            },
            decorate = { entry ->
                ledger.onRendered(entry.contentKey)
                saveableStateHolder.SaveableStateProvider(entry.contentKey) { entry.Content() }
            },
        ),
        NavEntryDecorator(
            onPop = { contentKey ->
                if (!ledger.onPopped(contentKey)) viewModelStoreProvider.clearKey(contentKey)
            },
            decorate = { entry ->
                val owner = rememberViewModelStoreOwner(
                    key = entry.contentKey,
                    provider = viewModelStoreProvider,
                    savedStateRegistryOwner = LocalSavedStateRegistryOwner.current,
                )
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
            },
        ),
    )

    /**
     * Drop everything being held, and stop holding until a retainable entry renders again.
     *
     * The caller decides when retention has stopped meaning anything — for the tab homes, when the
     * user is no longer on the tabs at all. Without this the ViewModels of a signed-out account
     * would be waiting for whoever signs in next.
     */
    fun releaseAll() {
        ledger.release().forEach { contentKey ->
            saveableStateHolder.removeState(contentKey)
            viewModelStoreProvider.clearKey(contentKey)
        }
    }
}

/**
 * A [RetainedEntryState] remembered across recompositions.
 *
 * @param retains whether an entry's `contentKey` names state that should outlive the entry.
 *   Retains nothing by default, which is Nav3's own behaviour.
 */
@Composable
fun rememberRetainedEntryState(
    retains: (contentKey: Any) -> Boolean = { false },
): RetainedEntryState {
    val saveableStateHolder = rememberSaveableStateHolder()
    val viewModelStoreProvider = rememberViewModelStoreProvider()
    return remember(saveableStateHolder, viewModelStoreProvider) {
        RetainedEntryState(
            saveableStateHolder = saveableStateHolder,
            viewModelStoreProvider = viewModelStoreProvider,
            ledger = RetentionLedger(retains),
        )
    }
}
