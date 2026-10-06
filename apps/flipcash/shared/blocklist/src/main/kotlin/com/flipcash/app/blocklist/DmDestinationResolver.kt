package com.flipcash.app.blocklist

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.dmDestination
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a person to the DM with them, or to their profile until there is one.
 *
 * The same rule the profile's pinned button uses: the DM's id is derived, and the DM exists once it
 * has members. A block hides the DM but leaves its members, so blocked is checked first: opening
 * the chat of someone the viewer blocked would undo the block. Lives beside
 * [BlocklistCoordinator] because it needs both that and the chat coordinator, which this module is
 * the lowest to see.
 */
@Singleton
class DmDestinationResolver @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val blocklist: BlocklistCoordinator,
) {
    /** The destination for [userId] right now. */
    suspend fun dmDestination(userId: ID, origin: ProfileOrigin): AppRoute =
        observeDmDestination(userId, origin).first()

    /** The destination for [userId], following the DM appearing and blocks landing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDmDestination(userId: ID, origin: ProfileOrigin): Flow<AppRoute> =
        openableDm(userId)
            .map { chatId -> dmDestination(userId, chatId, origin) }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun openableDm(userId: ID): Flow<ChatId?> = flowOfDmChatId(userId)
        .flatMapLatest { chatId ->
            if (chatId == null) {
                flowOf(null)
            } else {
                combine(
                    chatCoordinator.observeMembers(chatId).map { it.isNotEmpty() },
                    blocklist.observeIsBlocked(userId),
                ) { exists, blocked -> chatId.takeIf { exists && !blocked } }
            }
        }

    private fun flowOfDmChatId(userId: ID): Flow<ChatId?> = flow {
        emit(chatCoordinator.generateChatId(userId).getOrNull())
    }
}
