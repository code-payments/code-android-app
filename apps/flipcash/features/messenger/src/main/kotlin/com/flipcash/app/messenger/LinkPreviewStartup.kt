package com.flipcash.app.messenger

import com.flipcash.app.messenger.internal.link.LinkCardMemory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts reading the saved link previews at launch.
 *
 * The store reads its database when it is built, and Hilt builds it on first injection, which
 * without this is when a chat first opens. Injecting this from the application builds it at
 * launch, so the rows are in memory before any chat is.
 */
@Singleton
class LinkPreviewStartup @Inject internal constructor(
    private val memory: LinkCardMemory,
) {
    /** Whether the saved previews have been read. */
    val isLoaded: Boolean get() = memory.isLoaded
}
