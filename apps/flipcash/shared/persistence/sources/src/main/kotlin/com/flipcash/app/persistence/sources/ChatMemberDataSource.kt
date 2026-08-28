package com.flipcash.app.persistence.sources

import androidx.room.withTransaction
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMemberDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeMembers(chatId: ChatId): Flow<List<ChatMember>> =
        db?.chatMemberDao()?.observeMembersForChat(mapper.chatIdHex(chatId))?.map { entities ->
            entities.map { mapper.toMember(it) }
        } ?: emptyFlow()

    fun observeAll(): Flow<Map<String, List<ChatMember>>> =
        db?.chatMemberDao()?.observeAll()?.map { entities ->
            entities.groupBy { it.member.chatIdHex }
                .mapValues { (_, members) -> members.map { mapper.toMember(it) } }
        } ?: emptyFlow()

    /** Resolves the [chatType] DM chat id that [userId] is a member of, or null if none is cached. */
    suspend fun getChatIdForUser(userId: ID, chatType: ChatType): ChatId? {
        val hex = db?.chatMemberDao()
            ?.getChatIdForMember(mapper.userIdHex(userId), chatType.name)
            ?: return null
        return mapper.chatIdFromHex(hex)
    }

    suspend fun getMembersForChat(chatId: ChatId): List<ChatMember> =
        getMembersForChat(mapper.chatIdHex(chatId))

    suspend fun getMembersForChat(chatIdHex: String): List<ChatMember> =
        db?.chatMemberDao()?.getMembersForChat(chatIdHex)?.map { mapper.toMember(it) } ?: emptyList()

    /** The READ pointer [selfId] has already advanced to in [chatId], or 0 if none is cached. */
    suspend fun getSelfReadPointer(chatId: ChatId, selfId: ID): Long =
        getMembersForChat(chatId)
            .firstOrNull { it.userId == selfId }
            ?.pointers
            ?.firstOrNull { it.type == PointerType.READ }
            ?.value
            ?: 0L

    suspend fun upsert(chatId: ChatId, members: List<ChatMember>) {
        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        // Member rows and their normalized profiles are written in one transaction so a
        // member never observes a missing profile mid-write. Profiles go first (the
        // @Relation reads them), and use the authoritative full-profile upsert.
        database.withTransaction {
            database.userProfileDao().upsertFull(members.map { mapper.toProfileEntity(it) })
            database.chatMemberDao().upsert(members.map { mapper.toEntity(hex, it) })
        }
    }

    /**
     * Records [pointer] for its member in [chatId], whether or not that member has synced yet.
     *
     * The read-merge-write runs inside the DAO so a feed sync landing between the two halves
     * cannot be lost.
     */
    suspend fun updatePointers(chatId: ChatId, pointer: MessagePointer) {
        val dao = db?.chatMemberDao() ?: return
        dao.advancePointer(
            chatIdHex = mapper.chatIdHex(chatId),
            userIdHex = mapper.userIdHex(pointer.userId),
            pointer = mapper.pointerSerialized(pointer),
        )
    }

    /**
     * Replaces a chat's membership with [members], keeping the rows that survive instead of
     * clearing the table first. A wipe takes each member's pointers with it, and the refresh
     * that follows can only restore what the server knew at fetch time — so a read the client
     * has advanced locally but not yet reported would be lost.
     */
    suspend fun replaceMembers(chatId: ChatId, members: List<ChatMember>) {
        if (members.isEmpty()) {
            deleteForChat(chatId)
            return
        }

        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        database.withTransaction {
            database.userProfileDao().upsertFull(members.map { mapper.toProfileEntity(it) })
            database.chatMemberDao().upsert(members.map { mapper.toEntity(hex, it) })
            database.chatMemberDao().deleteMembersNotIn(
                chatIdHex = hex,
                keepUserIdHexes = members.map { mapper.userIdHex(it.userId) },
            )
        }
    }

    suspend fun deleteForChat(chatId: ChatId) {
        db?.chatMemberDao()?.deleteForChat(mapper.chatIdHex(chatId))
    }

    suspend fun clear() {
        db?.chatMemberDao()?.deleteAll()
    }
}
