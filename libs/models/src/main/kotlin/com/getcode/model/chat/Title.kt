package com.getcode.model.chat

import com.codeinc.gen.chat.v1.ChatService

sealed interface Title {
    val value: String

    data class Localized(override val value: String) : Title
    data class Domain(override val value: String) : Title

    companion object {
        operator fun invoke(proto: ChatService.ChatMetadata): Title? {
            return when (proto.titleCase) {
                ChatService.ChatMetadata.TitleCase.LOCALIZED -> Localized(proto.localized.keyOrText)
                ChatService.ChatMetadata.TitleCase.DOMAIN -> Domain(proto.domain.value)
                ChatService.ChatMetadata.TitleCase.TITLE_NOT_SET -> null
                else -> null
            }
        }
    }
}