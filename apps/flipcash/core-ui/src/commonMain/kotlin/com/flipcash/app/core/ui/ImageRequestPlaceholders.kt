package com.flipcash.app.core.ui

import coil3.request.ImageRequest

/** Shows the user placeholder when the request fails. Coil's `error(Int)` overload is Android-only. */
internal expect fun ImageRequest.Builder.errorPlaceholderUser(): ImageRequest.Builder
