package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.SilenceState
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restrictions the host has just set or lifted, from whichever screen did it: the duration sheet,
 * the member sheet, a Members swipe or the Restricted list.
 *
 * The restricted-users index trails writes, so a screen that shows a member's restriction applies
 * these on top of its last read rather than reading again. Holds nothing itself, so there is no
 * account's data left behind at logout.
 */
@Singleton
internal class GroupRestrictionWrites @Inject constructor() {

    /** One write: [silence] is the restriction now in force, or null once it is lifted. */
    data class Write(val chatId: ChatId, val userId: ID, val silence: SilenceState?)

    private val _writes = MutableSharedFlow<Write>(extraBufferCapacity = 16)
    val writes: SharedFlow<Write> = _writes.asSharedFlow()

    fun silenced(chatId: ChatId, userId: ID, silence: SilenceState) {
        _writes.tryEmit(Write(chatId, userId, silence))
    }

    fun lifted(chatId: ChatId, userId: ID) {
        _writes.tryEmit(Write(chatId, userId, null))
    }
}
