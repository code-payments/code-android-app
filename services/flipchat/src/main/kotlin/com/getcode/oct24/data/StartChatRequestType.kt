package com.getcode.oct24.data

import com.getcode.model.ID

interface StartChatRequestType {
    data class TwoWay(val recipient: ID): StartChatRequestType
    data class Group(val title: String? = null, val recipients: List<ID> = emptyList()):
        StartChatRequestType
}