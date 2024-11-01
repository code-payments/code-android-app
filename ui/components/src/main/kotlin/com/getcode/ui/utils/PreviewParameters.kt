package com.getcode.ui.utils

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.getcode.model.ID
import java.security.SecureRandom
import java.util.UUID

class UUIDPreviewParameterProvider(count: Int = 40) : PreviewParameterProvider<UUID> {
    override val values: Sequence<UUID> = generateSequence { UUID.randomUUID() }.take(count)
}

class IDPreviewParameterProvider(count: Int = 40): PreviewParameterProvider<ID> {
    override val values: Sequence<ID> = generateSequence {
        val random = SecureRandom()
        ByteArray(32).also { random.nextBytes(it) }.toList()
    }.take(count)
}