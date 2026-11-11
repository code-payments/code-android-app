package com.getcode.opencode.model.core

import com.getcode.utils.hexEncodedString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

typealias ID = List<Byte>

val NoId: ID = emptyList()

@OptIn(ExperimentalUuidApi::class)
val RandomId: ID get() = Uuid.random().toByteArray().toList()

val ID.description: String
    @OptIn(ExperimentalUuidApi::class)
    get() = if (size == 16) Uuid.fromByteArray(toByteArray()).toString() else hexEncodedString()


