package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.Identity
import com.getcode.model.chat.Platform

operator fun Identity.Companion.invoke(proto: ChatService.MemberIdentity): Identity? {
    val platform = Platform(proto.platform).takeIf { it != Platform.Unknown } ?: return null
    return Identity(
        platform = platform,
        username = proto.username,
        imageUrl = null,
    )
}