package com.flipcash.shared.chat

/**
 * What [MessagingOperations.hydrateChat] found for a chat on the way onto the screen.
 *
 * The cases stay apart because the transcript fetch that follows depends on which one it was: a
 * chat the server has never heard of has no messages to ask for, and asking anyway comes back
 * DENIED, while a fetch that merely failed says nothing about the chat and the fetch should still
 * be tried.
 */
sealed interface ChatHydration {
    /** The device already holds the row; [ChatCoordinator.observeMetadata] is answering for it. */
    data object Stored : ChatHydration

    /** Fetched from the server and deliberately not persisted; see [ChatMembership.isMember]. */
    data class Fetched(val membership: ChatMembership) : ChatHydration

    /**
     * The server has no such chat. A DM opened on its derived id before either side has written
     * in it, from a scanned tip card or a profile, is the usual way here.
     */
    data object Absent : ChatHydration

    /** The fetch failed for some other reason, so whether the chat exists is still unknown. */
    data object Unavailable : ChatHydration
}
