package com.getcode.oct24.network.controllers

import com.getcode.model.ID
import com.getcode.oct24.data.mapper.RoomMapper
import com.getcode.oct24.internal.network.service.ChatService
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.Room
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatController @Inject constructor(
    private val userManager: UserManager,
    private val service: ChatService,
    private val roomMapper: RoomMapper,
) {
    suspend fun getChats(
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<Room>> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        return service.getChats(owner, userId, queryOptions)
            .map { it.map { meta -> roomMapper.map(meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun getChat(identifier: ChatIdentifier): Result<Room> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.getChat(owner, identifier)
            .map { roomMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun startChat(type: StartChatRequestType): Result<Room> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        return service.startChat(owner, userId, type)
            .map { roomMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun joinChat(identifier: ChatIdentifier): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        return service.joinChat(owner, userId, identifier)
    }

    suspend fun leaveChat(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        return service.leaveChat(owner, userId, chatId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun mute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.setMuteState(owner, chatId, true)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun unmute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.setMuteState(owner, chatId, false)
            .onFailure { ErrorUtils.handleError(it) }
    }
}