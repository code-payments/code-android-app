package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.Platform
import com.getcode.model.chat.Platform.Unknown
import com.getcode.model.chat.Platform.entries

operator fun Platform.Companion.invoke(proto: ChatService.Platform): Platform {
    return runCatching { entries[proto.ordinal] }.getOrNull() ?: Unknown
}