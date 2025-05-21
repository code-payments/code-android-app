package com.getcode.model

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.chat.Title
import com.getcode.model.chat.Title.Domain
import com.getcode.model.chat.Title.Localized

operator fun Title.Companion.invoke(proto: ChatService.ChatMetadata): Title? {
    return when (proto.titleCase) {
        ChatService.ChatMetadata.TitleCase.LOCALIZED -> Localized(proto.localized.keyOrText)
        ChatService.ChatMetadata.TitleCase.DOMAIN -> Domain(proto.domain.value)
        ChatService.ChatMetadata.TitleCase.TITLE_NOT_SET -> null
        else -> null
    }
}