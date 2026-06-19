package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.MessagePointer
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
            entities.groupBy { it.chatIdHex }
                .mapValues { (_, members) -> members.map { mapper.toMember(it) } }
        } ?: emptyFlow()

    suspend fun getMembersForChat(chatId: ChatId): List<ChatMember> =
        getMembersForChat(mapper.chatIdHex(chatId))

    suspend fun getMembersForChat(chatIdHex: String): List<ChatMember> =
        db?.chatMemberDao()?.getMembersForChat(chatIdHex)?.map { mapper.toMember(it) } ?: emptyList()

    suspend fun upsert(chatId: ChatId, members: List<ChatMember>) {
        val hex = mapper.chatIdHex(chatId)
        db?.chatMemberDao()?.upsert(members.map { mapper.toEntity(hex, it) })
    }

    suspend fun updatePointers(chatId: ChatId, pointer: MessagePointer) {
        val dao = db?.chatMemberDao() ?: return
        val chatIdHex = mapper.chatIdHex(chatId)
        val userIdHex = mapper.userIdHex(pointer.userId)

        val existing = dao.getMember(chatIdHex, userIdHex)
        val existingPointers = existing?.pointersJson ?: emptyList()

        val merged = existingPointers
            .filter { it.type != pointer.type.name }
            .plus(mapper.pointerSerialized(pointer))

        dao.updatePointers(chatIdHex, userIdHex, mapper.pointersToJson(merged))
    }

    suspend fun deleteForChat(chatId: ChatId) {
        db?.chatMemberDao()?.deleteForChat(mapper.chatIdHex(chatId))
    }

    suspend fun clear() {
        db?.chatMemberDao()?.deleteAll()
    }
}
