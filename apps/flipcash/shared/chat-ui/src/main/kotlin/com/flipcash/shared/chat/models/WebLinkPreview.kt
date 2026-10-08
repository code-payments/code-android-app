package com.flipcash.shared.chat.models

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader

/** Whether a web card asks for its page on its own or waits to be told. */
enum class WebLinkPreviewMode { Automatic, TapToLoad }

/**
 * TapToLoad for a viewer outside the group, who has not agreed to this device contacting a site a
 * stranger linked; Automatic otherwise. Never stored, so nothing kept says who looked.
 */
val LocalWebLinkPreviewMode = staticCompositionLocalOf { WebLinkPreviewMode.Automatic }

/**
 * The loader for a preview's picture: no cookies, public-only DNS, the page rules on every hop.
 * Null draws no picture, because the app's own loader keeps cookies and follows any redirect.
 */
val LocalWebPreviewImageLoader = staticCompositionLocalOf<ImageLoader?> { null }
