package com.getcode.ui.utils

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.util.UUID

class UUIDPreviewParameterProvider(count: Int = 40) : PreviewParameterProvider<UUID> {
    override val values: Sequence<UUID> = generateSequence { UUID.randomUUID() }.take(count)
}