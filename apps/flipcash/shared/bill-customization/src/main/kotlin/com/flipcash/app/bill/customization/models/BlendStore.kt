package com.flipcash.app.bill.customization.models

import com.flipcash.app.bill.customization.internal.features.BlendMode

data class BlendStore(
    val blendMode: BlendMode,
    val committedStrength: Float,
    val temporaryStrength: Float? = null,
) {
    val strength: Float
        get() = temporaryStrength ?: committedStrength
}
