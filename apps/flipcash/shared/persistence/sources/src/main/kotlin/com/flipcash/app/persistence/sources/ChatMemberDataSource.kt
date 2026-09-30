package com.flipcash.app.persistence.sources

import androidx.room.withTransaction
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.app.persistence.sources.search.MemberSearchText
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
        getSelfReadPointerOrNull(chatId, selfId) ?: 0L

    /**
     * The READ pointer [selfId] has already advanced to in [chatId]: 0 when the self row carries
     * none, `null` when there is no self row. A group's roster is paged, so a missing row means
     * nothing is known, not that nothing has been read.
     */
    suspend fun getSelfReadPointerOrNull(chatId: ChatId, selfId: ID): Long? =
        getMembersForChat(chatId)
            .firstOrNull { it.userId == selfId }
            ?.let { self -> self.pointers.firstOrNull { it.type == PointerType.READ }?.value ?: 0L }

    suspend fun upsert(chatId: ChatId, members: List<ChatMember>) {
        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        // Member rows and their normalized profiles are written in one transaction so a
        // member never observes a missing profile mid-write. Profiles go first (the
        // @Relation reads them).
        database.withTransaction {
            database.userProfileDao().upsertMembers(profileRows(members))
            database.chatMemberDao().upsert(members.map { mapper.toEntity(hex, it) })
            index(database, hex, members)
        }
    }

    /**
     * Moves [pointer]'s member forward in [chatId], whether or not that member has synced yet.
     * A pointer already ahead of [pointer] stays where it is.
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
            database.userProfileDao().upsertMembers(profileRows(members))
            database.chatMemberDao().upsert(members.map { mapper.toEntity(hex, it) })
            database.chatMemberDao().deleteMembersNotIn(
                chatIdHex = hex,
                keepUserIdHexes = members.map { mapper.userIdHex(it.userId) },
            )
            index(database, hex, members)
            database.chatMemberSearchDao().deleteTokensOfFormerMembers(hex)
        }
    }

    /** How many of [chatId]'s members the device holds. For a group, compare with its `member_count`. */
    suspend fun countMembers(chatId: ChatId): Int =
        db?.chatMemberSearchDao()?.countMembers(mapper.chatIdHex(chatId)) ?: 0

    /**
     * Drops the members of [chatId] a full roster read shows have left: held, not in [seen], and
     * joined at or before [readVersion], the roster version the read described. A member who
     * joined after it is newer than the read, not gone, and stays.
     *
     * Done a member at a time rather than as one `NOT IN` list: a large group's roster runs past
     * the 999 bound variables the minSdk SQLite build allows in a statement.
     */
    suspend fun reconcile(chatId: ChatId, seen: Set<ID>, readVersion: Long) {
        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        val seenHexes = seen.mapTo(HashSet()) { mapper.userIdHex(it) }
        database.withTransaction {
            val departed = database.chatMemberSearchDao().getMemberIdsJoinedBy(hex, readVersion)
                .filterNot { it in seenHexes }
            for (userIdHex in departed) {
                database.chatMemberDao().deleteMember(hex, userIdHex)
                database.chatMemberSearchDao().deleteTokensForMember(hex, userIdHex)
            }
            // The read lists everyone in the roster as of [readVersion], so a leave at or below it
            // no longer needs its marker.
            database.chatMemberDao().deleteMarkersUpTo(hex, readVersion)
        }
    }

    /**
     * Applies a `MemberLeft` at roster [version]: [userId] leaves search and the count, and a
     * roster page that trails the stream cannot add them back (model.proto: a trailing page
     * "cannot resurrect" a removed member). [replaceMembers] cannot do this, because a roster
     * change names who left rather than who remains.
     */
    suspend fun markLeft(chatId: ChatId, userId: ID, version: Long) {
        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        val userIdHex = mapper.userIdHex(userId)
        database.withTransaction {
            if (database.chatMemberDao().markLeft(chatIdHex = hex, userIdHex = userIdHex, version = version)) {
                database.chatMemberSearchDao().deleteTokensForMember(chatIdHex = hex, userIdHex = userIdHex)
            }
        }
    }

    suspend fun deleteForChat(chatId: ChatId) {
        val database = db ?: return
        val hex = mapper.chatIdHex(chatId)
        database.withTransaction {
            database.chatMemberDao().deleteForChat(hex)
            database.chatMemberSearchDao().deleteTokensForChat(hex)
            // The roster read is only complete for the members it wrote.
            database.chatMemberSearchDao().deleteSyncState(hex)
        }
    }

    suspend fun clear() {
        val database = db ?: return
        database.withTransaction {
            database.chatMemberDao().deleteAll()
            database.chatMemberSearchDao().deleteAllTokens()
            database.chatMemberSearchDao().deleteAllSyncState()
        }
    }

    /**
     * Rewrites the search tokens of [members] in [chatIdHex] from their stored profiles.
     *
     * Read back after the profile write rather than taken from [members]: a member can arrive with
     * no profile (see [profileRows]), and is then indexed by the one already stored, if any.
     */
    private suspend fun index(database: FlipcashDatabase, chatIdHex: String, members: List<ChatMember>) {
        val search = database.chatMemberSearchDao()
        for (member in members) {
            val userIdHex = mapper.userIdHex(member.userId)
            // A member the upsert kept out, having left at a later version, gets no words.
            if (database.chatMemberDao().getMember(chatIdHex, userIdHex)?.isMember != true) continue
            val profile = database.userProfileDao().getByUserId(userIdHex) ?: continue
            search.replaceTokens(chatIdHex, userIdHex, MemberSearchText.tokens(profile.displayName, profile.username))
        }
    }

    /**
     * The `user_profiles` rows [members] can vouch for.
     *
     * A member can arrive with no name, picture or handle — the mapper builds a profile whether or
     * not the server sent one. Writing that as a full-row replace would wipe whatever a `GetProfile`
     * had already filled in, and leave a blank row the transcript cannot tell from a nameless one. Left out instead: the member row still lands, and a sender with no profile row is
     * what makes the transcript fetch one.
     */
    private fun profileRows(members: List<ChatMember>) = members
        .filter { member ->
            val profile = member.userProfile
            profile.displayName.isNotBlank() ||
                profile.profilePicture != null ||
                !profile.username.isNullOrBlank()
        }
        .map { mapper.toProfileEntity(it) }
}
