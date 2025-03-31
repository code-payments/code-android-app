package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.ChatType

operator fun ChatType.Companion.invoke(proto: ChatService.ChatType): ChatType {
    return runCatching { types[proto.ordinal] }.getOrNull() ?: ChatType.Unknown
}