package com.getcode.navigation.results

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.NavContentKey
import com.getcode.navigation.contentKey
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.navigation.utils.lifecycle.RepeatOnLifecycle
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.collections.isNotEmpty

interface NavResultStore {
    fun <K : NavigationRetVal<T>, T : Parcelable> registerCallback(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        onResult: (NavResultOrCanceled<T>) -> Unit,
    )

    fun unregisterCallback(
        callerEntryId: NavKey,
        key: NavResultKey<*, *>,
    )

    fun <K : NavigationRetVal<T>, T : Parcelable> deliverOrPersist(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        value: NavResultOrCanceled<T>,
    ): Boolean

    fun <K : NavigationRetVal<T>, T : Parcelable> takePersisted(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): NavResultOrCanceled<T>?

    /** Parent should disposeOwner() when done to avoid leaks. */
    fun disposeOwner(ownerContentKey: NavContentKey)

    /**
     * Observe changes to persisted results for a specific key
     */
    fun <K : NavigationRetVal<T>, T : Parcelable> observePersistedChanges(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): Flow<NavResultOrCanceled<T>?>

    /**
     * Register a persistent listener that survives navigation changes
     */
    fun <K : NavigationRetVal<T>, T : Parcelable> registerPersistentListener(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        onResult: (NavResultOrCanceled<T>) -> Unit,
    )

    /**
     * Unregister a persistent listener
     */
    fun unregisterPersistentListener(
        callerEntryId: NavKey,
        key: NavResultKey<*, *>,
    )

    @Composable
    fun ResultCallbackDispatcherEffect(callerEntryId: NavContentKey)
}

typealias NavResultStateRegistry = SnapshotStateMap<NavContentKey, SavedStateHandle>

@Composable
fun rememberNavResultStateRegistry(): NavResultStateRegistry {
    return remember { mutableStateMapOf() }
}

@Composable
fun rememberNavResultStore(resultStateRegistry: NavResultStateRegistry): NavResultStore = remember {
    NavResultStoreImpl(
        controller = SavedStateHandleNavResultController(
            savedStateHandleProvider = { navKey: NavKey ->
                resultStateRegistry[navKey.contentKey()]
            }
        )
    )
}

internal class NavResultStoreImpl(
    // An optional method of persisting a result
    private val controller: NavResultController,
) : NavResultStore {

    // This is a registry of all callbacks registered for a given NavKey owner
    private val idsByOwner =
        mutableMapOf<NavContentKey, MutableSet<Pair<NavKey, NavResultKey<*, *>>>>()

    /**
     * This is registry of callbacks registered for results
     * @param Pair<NavKey, NavResultKey<*, *>> - the caller's NavKey and the specific result key
     * @param (Any) -> Unit - the callback to invoke with the result (as NavResultOrCanceled<T>)
     */
    private val callbacks =
        mutableMapOf<Pair<NavKey, NavResultKey<*, *>>, (Any) -> Unit>()

    // This is a registry of pending handler queues for a given NavKey owner
    private val handlerQueues: SnapshotStateMap<NavContentKey, SnapshotStateList<() -> Unit>> =
        SnapshotStateMap()

    private fun makeKey(
        callerEntryId: NavKey,
        key: NavResultKey<*, *>,
    ): Pair<NavKey, NavResultKey<*, *>> = callerEntryId to key


    @Suppress("UNCHECKED_CAST")
    override fun <K : NavigationRetVal<T>, T : Parcelable> registerCallback(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        onResult: (NavResultOrCanceled<T>) -> Unit,
    ) {
        trace("registerCallback: callerEntryId=$callerEntryId, key=$key", type = TraceType.Silent)
        val callbackKey = makeKey(callerEntryId, key)
        idsByOwner.getOrPut(
            callerEntryId.contentKey(),
            defaultValue = { mutableSetOf() },
        ).add(callbackKey)
        callbacks.put(callbackKey) { any -> onResult(any as NavResultOrCanceled<T>) }
    }

    override fun unregisterCallback(
        callerEntryId: NavKey,
        key: NavResultKey<*, *>,
    ) {
        trace("unregisterCallback: callerEntryId=$callerEntryId, key=$key", type = TraceType.Silent)
        val callbackKey = makeKey(callerEntryId, key)
        idsByOwner[callerEntryId.contentKey()]?.remove(callbackKey)
        callbacks.remove(callbackKey)
    }

    /**
     * Deliver a result intended for the caller entry.
     * Returns true if delivered to a callback; false if persisted.
     */
    override fun <K : NavigationRetVal<T>, T : Parcelable> deliverOrPersist(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        value: NavResultOrCanceled<T>,
    ): Boolean {
        trace("deliverOrPersist: callerEntryId=$callerEntryId, key=$key, value=$value")
        val callbackKey = makeKey(callerEntryId, key)
        val cb = callbacks.remove(callbackKey)
        return if (cb != null) {
            // Avoid invoking the callback immediately/directly here because we don't know if the
            // owner is running. The callback behavior will be lost if it is not currently
            // rendering.
            // The queue should be added when the listener is started
            Snapshot.withMutableSnapshot {
                handlerQueues.getOrPut(callerEntryId.contentKey(), { SnapshotStateList() })
                    .add {
                        trace("deliverOrPersist: callerEntryId=$callerEntryId, key=$key, INVOKING CALLBACK XXXXXX", type = TraceType.Silent)
                        cb(value)
                    }
            }
            trace("deliverOrPersist: callerEntryId=$callerEntryId, key=$key, queued callback, queueSize=${handlerQueues[callerEntryId.contentKey()]?.size}", type = TraceType.Silent)
            true
        } else {
            controller.put(callerEntryId, key, value)
            false
        }
    }

    /**
     * For process-death recovery: the caller can poll once on resume.
     * If a persisted value exists, returns and clears it.
     */
    override fun <K : NavigationRetVal<T>, T : Parcelable> takePersisted(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): NavResultOrCanceled<T>? {
        return controller.take(callerEntryId, key)
    }

    override fun disposeOwner(ownerContentKey: NavContentKey) {
        trace("disposeOwner: owner=$ownerContentKey", type = TraceType.Silent)
        Snapshot.withMutableSnapshot {
            idsByOwner.remove(ownerContentKey)
                ?.forEach { (callerEntryId, key) -> unregisterCallback(callerEntryId, key) }
            handlerQueues.remove(ownerContentKey)
        }
    }

    /**
     * Observe changes to persisted results for a specific key
     */
    override fun <K : NavigationRetVal<T>, T : Parcelable> observePersistedChanges(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): Flow<NavResultOrCanceled<T>?> {
        trace("observePersistedChanges: callerEntryId=$callerEntryId, key=$key", type = TraceType.Silent)
        return controller.observeChanges(callerEntryId, key)
    }

    /**
     * Register a persistent listener that survives navigation changes
     */
    override fun <K : NavigationRetVal<T>, T : Parcelable> registerPersistentListener(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        onResult: (NavResultOrCanceled<T>) -> Unit,
    ) {
        val callbackKey = makeKey(callerEntryId, key)
        idsByOwner.getOrPut(
            callerEntryId.contentKey(),
            defaultValue = { mutableSetOf() },
        ).add(callbackKey)
        callbacks.put(callbackKey) { any ->
            @Suppress("UNCHECKED_CAST")
            onResult(any as NavResultOrCanceled<T>)
        }
    }

    /**
     * Unregister a persistent listener
     */
    override fun unregisterPersistentListener(
        callerEntryId: NavKey,
        key: NavResultKey<*, *>,
    ) {
        val callbackKey = makeKey(callerEntryId, key)
        idsByOwner[callerEntryId.contentKey()]?.remove(callbackKey)
        callbacks.remove(callbackKey)
    }

    @Composable
    override fun ResultCallbackDispatcherEffect(callerEntryId: NavContentKey) {
        RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
            while (isActive) {
                val queue: SnapshotStateList<() -> Unit>? = handlerQueues[callerEntryId]
                if (queue != null && queue.isNotEmpty()) {
                    trace("ResultCallbackDispatcherEffect: callerEntryId=$callerEntryId, invoking callback, queueSize=${queue.size}", type = TraceType.Silent)
                    val cb = queue.removeAt(0)
                    try {
                        cb()
                    } catch (e: Exception) {
                        trace("Error invoking nav result callback for $callerEntryId", error = e, type = TraceType.Error)
                    }
                } else {
                    delay(500)
                }
            }
        }
    }
}

val LocalNavKey = staticCompositionLocalOf<NavKey> { error("No NavKey") }

/**
 * If a CoroutineScope is provided, launch the work in it; otherwise run it directly.
 * This is useful for cases where you might or might not have a lifecycle-aware scope
 * available to launch from.
 */
fun CoroutineScope?.launchOrRun(work: () -> Unit) {
    this?.launch { work() } ?: work()
}

/**
 * Caller registers for a one-shot result before navigating forward.
 * @param coroutineScope Required if onResult will change parent state in a way that might cancel
 *   its coroutine context.
 */
inline fun <reified T : Parcelable> CodeNavigator.navigateForResult(
    route: NavigationRetVal<T>,
    options: NavOptions = NavOptions(),
    coroutineScope: CoroutineScope? = null,
    noinline onResult: (NavResultOrCanceled<T>) -> Unit,
) {
    if (options.debugRouting) {
        trace("navigateForResult: route=$route, currentRouteKey=$currentRouteKey", type = TraceType.Navigation)
    }
    coroutineScope.launchOrRun {
        val curKey = currentRouteKey ?: error("No current route to register result callback with")
        this.resultStore.registerCallback(curKey, route.asKey(), onResult)
        navigate(route = route, options = options.copy(navigatingForResult = true))
    }
}

// LocalNavKey is volatile for a given composition. When navigating away the component will see
// the key change.  It is assumed this is during the animation phase.
@Composable
inline fun <reified T : Parcelable> resultBackNavigator(
    @Suppress("UNCHECKED_CAST") route: NavigationRetVal<T>? = LocalNavKey.current as? NavigationRetVal<T>,
    navigator: CodeNavigator = LocalCodeNavigator.current,
    navResultStore: NavResultStore = LocalCodeNavigator.current.resultStore,
): IResultBackNavigator<T> = route?.let {
    ResultBackNavigator(
        navResultKey = route.asKey(),
        navigator = navigator,
        navResultStore = navResultStore,
    )
} ?: run {
    trace("ResultBackNavigator: Returning default object because route was not a RetVal")
    object : IResultBackNavigator<T> {
        override fun returnValue(value: T) {
            trace("ResultBackNavigator: No route available to return result $value for ${T::class.simpleName}", type = TraceType.Silent)
        }

        override fun returnCanceled() {
            trace("ResultBackNavigator: No route available to return canceled for ${T::class.simpleName}", type = TraceType.Silent)
        }
    }
}

interface IResultBackNavigator<T : Parcelable> {
    fun returnValue(value: T)
    fun returnCanceled()
    fun returnValueOrNone(value: T?) {
        if (value != null) {
            returnValue(value)
        } else {
            returnNoValue()
        }
    }

    fun returnNoValue() = returnCanceled()
}

class ResultBackNavigator<T : Parcelable>(
    private val navResultKey: NavResultKey<NavigationRetVal<T>, T>,
    private val navigator: CodeNavigator,
    private val navResultStore: NavResultStore,
) : IResultBackNavigator<T> {
    private fun returnResult(value: NavResultOrCanceled<T>) {
        val callerId = navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
        trace("returnResult: callerId=$callerId, key=$navResultKey, value=$value", type = TraceType.Silent)
        if (callerId != null) {
            navResultStore.deliverOrPersist(
                callerEntryId = callerId,
                key = navResultKey,
                value = value
            )
        } else {
            trace("No caller to deliver result to; result will be dropped", type = TraceType.Silent)
        }
        navigator.navigateBack(navigatingForResult = true)
    }

    override fun returnValue(value: T) {
        returnResult(NavResultOrCanceled.ReturnValue(value))
    }

    @Suppress("UNCHECKED_CAST")
    override fun returnCanceled() {
        returnResult(NavResultOrCanceled.Canceled)
    }
}

/**
 * Composable helper to observe persisted NavResult changes and automatically handle them
 */
@Composable
fun <K : NavigationRetVal<T>, T : Parcelable> ObserveNavResultChanges(
    key: NavResultKey<K, T>,
    callerEntryId: NavKey = LocalNavKey.current,
    navResultStore: NavResultStore = LocalCodeNavigator.current.resultStore,
    onResult: (NavResultOrCanceled<T>) -> Unit,
) {
    LaunchedEffect(callerEntryId, key) {
        navResultStore.observePersistedChanges(callerEntryId, key)
            .filterNotNull()
            .collect { result ->
                trace("ObserveNavResultChanges: callerEntryId=$callerEntryId, key=$key, result=$result", type = TraceType.Silent)
                runCatching {
                    onResult(result)
                }.onFailure {
                    trace(
                        message = "Error handling nav result for callerEntryId=$callerEntryId, key=$key",
                        error = it,
                        type = TraceType.Error
                    )
                }
                // After handling, remove the persisted result to prevent duplicate handling
                runCatching {
                    navResultStore.takePersisted(callerEntryId, key)
                }.onFailure {
                    trace(
                        message = "Error clearing persisted nav result for callerEntryId=$callerEntryId, key=$key",
                        error = it,
                        type = TraceType.Error
                    )
                }
            }
    }
}
