package com.flipcash.app.core.ui

import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.ui.components.R

internal actual fun ImageRequest.Builder.errorPlaceholderUser(): ImageRequest.Builder =
    error(R.drawable.ic_placeholder_user)
