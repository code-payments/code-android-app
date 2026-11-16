package com.flipcash.client

import com.codeinc.flipcash.wire.messaging.v1.GetReactorsResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.GrpcTransport

/** Read calls against `flipcash.messaging.v1.Messaging`. */
class MessagingClient(private val transport: GrpcTransport) {

    /**
     * One page of the users who reacted with [emoji] to [messageId] in [chatId].
     *
     * @throws GetReactorsException when the server answers with a result other than OK.
     */
    suspend fun getReactors(
        owner: Ed25519.KeyPair,
        chatId: Id,
        messageId: Long,
        emoji: String,
        options: QueryOptions = QueryOptions(),
    ): ReactorsPage {
        val request = Requests.getReactors(owner, chatId, messageId, emoji, options)
        return decodeGetReactors(transport.unary(Requests.GET_REACTORS, request.encodeFlipcash()))
    }
}

/** A `GetReactorsResponse` as Android's `ChatMessagingService.getReactors` reads it. */
internal fun decodeGetReactors(bytes: ByteArray): ReactorsPage {
    val response = GetReactorsResponse.ADAPTER.decode(bytes)
    // An unknown result number decodes to OK in Wire; Android sees UNRECOGNIZED.
    rawEnum(response.unknownFields, tag = 1)?.let { throw GetReactorsException.Unrecognized(it) }
    return when (response.result) {
        GetReactorsResponse.Result.OK -> ReactorsPage(
            reactors = response.reactors.map { it.toDomain() },
            hasMore = response.has_more,
            pagingToken = response.paging_token?.value_?.let { Id(it.toByteArray()) },
        )
        GetReactorsResponse.Result.DENIED -> throw GetReactorsException.Denied()
        GetReactorsResponse.Result.MESSAGE_NOT_FOUND -> throw GetReactorsException.MessageNotFound()
    }
}
