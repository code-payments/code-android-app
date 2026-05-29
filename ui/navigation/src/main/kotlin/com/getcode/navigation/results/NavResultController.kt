package com.getcode.navigation.results

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface NavResultController {
    fun <K : NavigationRetVal<T>, T : Parcelable> put(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        value: NavResultOrCanceled<T>,
    )

    fun <K : NavigationRetVal<T>, T : Parcelable> take(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): NavResultOrCanceled<T>?

    fun <K : NavigationRetVal<T>, T : Parcelable> clear(callerEntryId: NavKey, key: NavResultKey<K, T>)

    /**
     * Observe changes to persisted results for a specific key
     */
    fun <K : NavigationRetVal<T>, T : Parcelable> observeChanges(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): Flow<NavResultOrCanceled<T>?>
}

data class PersistenceChangeEvent(
    val callerEntryId: NavKey,
    val key: NavResultKey<*, *>,
    val value: NavResultOrCanceled<*>?,
)

class SavedStateHandleNavResultController(
    private val savedStateHandleProvider: (callerNavKey: NavKey) -> androidx.lifecycle.SavedStateHandle?,
) : NavResultController {
    private val changeEvents = MutableSharedFlow<PersistenceChangeEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )

    override fun <K : NavigationRetVal<T>, T : Parcelable> put(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
        value: NavResultOrCanceled<T>,
    ) {
        // Writing a value twice is an error. Ensure any existing value is removed to allow overwrite.
        // Not clear if this was really necessary or if the error about a repeated write to savestate was caused by something else.
        if (savedStateHandleProvider(callerEntryId)?.contains(key.name) == true) {
            savedStateHandleProvider(callerEntryId)?.remove<NavResultOrCanceled<T>>(key.toString())
        }
        savedStateHandleProvider(callerEntryId)?.set(key.toString(), value)
        // Emit change event
        changeEvents.tryEmit(PersistenceChangeEvent(callerEntryId, key, value))
    }

    override fun <K : NavigationRetVal<T>, T : Parcelable> take(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): NavResultOrCanceled<T>? {
        val handle = savedStateHandleProvider(callerEntryId) ?: return null
        val value = handle.get<NavResultOrCanceled<T>>(key.name)
        // one-shot consumption
        if (value != null) {
            handle.remove<NavResultOrCanceled<T>>(key.name)
            // Emit removal event
            changeEvents.tryEmit(PersistenceChangeEvent(callerEntryId, key, null))
        }
        return value
    }

    override fun <K : NavigationRetVal<T>, T : Parcelable> clear(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ) {
        savedStateHandleProvider(callerEntryId)?.remove<Any>(key.name)
        // Emit removal event
        changeEvents.tryEmit(PersistenceChangeEvent(callerEntryId, key, null))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <K : NavigationRetVal<T>, T : Parcelable> observeChanges(
        callerEntryId: NavKey,
        key: NavResultKey<K, T>,
    ): Flow<NavResultOrCanceled<T>?> {
        return changeEvents
            .filter { it.callerEntryId == callerEntryId && it.key == key }
            .map { it.value as? NavResultOrCanceled<T> }
            .onStart {
                // Emit current value if it exists
                val currentValue =
                    savedStateHandleProvider(callerEntryId)?.get<NavResultOrCanceled<T>>(
                        key.name
                    )
                if (currentValue != null) {
                    emit(currentValue)
                }
            }
    }
}
