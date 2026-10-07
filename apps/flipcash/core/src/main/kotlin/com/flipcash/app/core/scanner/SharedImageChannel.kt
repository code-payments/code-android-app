package com.flipcash.app.core.scanner

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries an image shared into the app from `MainActivity`'s intent to the Scan tab.
 *
 * A `StateFlow`, not a `SharedFlow`: the image has to survive from the moment the intent lands
 * (`onCreate`/`onNewIntent`, before any UI is composed) until the Scan tab is actually composed,
 * which may be after a navigation and a recomposition or two — a one-shot emission could be
 * missed entirely. Holding the value, not just emitting it, is what makes a late subscriber
 * (the Scan tab, composed after the app has already routed there) still see it.
 */
@Singleton
class SharedImageChannel @Inject constructor() {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri) {
        _pending.value = uri
    }

    fun clear() {
        _pending.value = null
    }
}
