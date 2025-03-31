package xyz.flipchat.services.internal.network.chat

import com.codeinc.flipchat.gen.messaging.v1.Model

sealed interface MessageStreamUpdate {
    data class Messages(val data: List<Model.Message>): MessageStreamUpdate
    data class Pointers(val data: List<Model.PointerUpdate>): MessageStreamUpdate
    data class Typing(val data: List<Model.IsTyping>): MessageStreamUpdate
}