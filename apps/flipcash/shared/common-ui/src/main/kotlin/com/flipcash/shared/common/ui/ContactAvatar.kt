package com.flipcash.shared.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.placeholder
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf

@Composable
fun ContactAvatar(
    contact: DeviceContact?,
    modifier: Modifier = Modifier,
    includeBorder: Boolean = true,
) {
    if (contact == null || contact.isUnknown) {
        UnknownContactAvatar(modifier = modifier, includeBorder = includeBorder)
    } else {
        ContactAvatar(
            modifier = Modifier
                .addIf(includeBorder) {
                    Modifier.border(
                        CodeTheme.dimens.border,
                        CodeTheme.colors.divider,
                        CircleShape,
                    )
                }.then(modifier),
            photoUri = contact.photoUri,
            displayName = contact.displayName,
        )
    }
}

@Composable
fun ContactAvatar(
    photoUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)
        )
    ) {
        if (photoUri != null) {
            var isError by rememberSaveable(photoUri) { mutableStateOf(false) }
            if (!isError) {
                val context = LocalContext.current
                val request = remember(photoUri) {
                    ImageRequest.Builder(context)
                        .crossfade(true)
                        .data(photoUri.toUri())
                        .build()
                }
                AsyncImage(
                    modifier = Modifier.matchParentSize(),
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    onError = { isError = true },
                )
            }
            if (isError) {
                InitialsText(displayName)
            }
        } else {
            InitialsText(displayName)
        }
    }
}

@Composable
fun ContactAvatar(
    userProfile: UserProfile,
    modifier: Modifier = Modifier,
) {
    ProfileAvatar(
        image = userProfile.profilePicture,
        modifier = modifier,
        fallback = { UnknownContactAvatar(includeBorder = true) },
    )
}

/**
 * Renders a server-side profile picture ([MediaItem]) — the tips list, chat header, info card, etc.
 * Prefer this over the raw `photoUri` overload for anything backed by a [MediaItem]: it picks the
 * rendition that matches the avatar's measured pixel size (the server ships several thumbnail /
 * display sizes) so the image is never grainy or over-fetched, and bridges the load with the
 * item's BlurHash plus any already-cached smaller rendition. Falls back to [displayName]'s
 * initials when there's no picture.
 */
@Composable
fun ContactAvatar(
    image: MediaItem?,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    ProfileAvatar(
        image = image,
        modifier = modifier,
        fallback = { InitialsText(displayName) },
    )
}

@Composable
private fun ProfileAvatar(
    image: MediaItem?,
    modifier: Modifier,
    fallback: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)
        )
    ) {
        // Pick the rendition by the avatar's actual pixel size — the longest bounded side of the
        // measured constraints (unbounded → request the largest, so it's never under-sized).
        val targetPx = remember(constraints) {
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            maxOf(w, h).takeIf { it > 0 } ?: Int.MAX_VALUE
        }
        val photoUri = remember(image, targetPx) { image?.urlForSize(targetPx) }
        if (image != null && photoUri != null) {
            var isError by rememberSaveable(photoUri) { mutableStateOf(false) }
            if (!isError) {
                val context = LocalContext.current
                // Two progressively better placeholders bridge the load so we never flash a blank
                // gradient while the correctly-sized rendition downloads:
                //   1. the BlurHash — an instant, self-contained blurred preview, and
                //   2. the next-smaller rendition — if another surface already cached it (e.g. the
                //      list loaded the 160 this 320 avatar sits above), Coil shows it immediately
                //      (see placeholderMemoryCacheKey) and upgrades in place.
                val blurHash = remember(image) {
                    BlurHash.decode(image.blurhash(), width = 24, height = 24)?.asImage()
                }
                // Cache identity is the durable blob id, NOT the download URL: the server re-mints
                // and expires `download_url` on every fetch, so a URL-keyed cache misses on the next
                // fetch even though the bytes are immutable — every load would re-download and flash
                // the BlurHash. Falls back to the URL only if a blob id is somehow unavailable.
                val cacheKey = remember(image, targetPx, photoUri) {
                    image.cacheKeyForSize(targetPx) ?: photoUri
                }
                val previewKey = remember(image, targetPx, cacheKey) {
                    image.cacheKeyBelow(targetPx)?.takeIf { it != cacheKey }
                }
                val request = remember(photoUri, cacheKey, previewKey, blurHash) {
                    ImageRequest.Builder(context)
                        .crossfade(true)
                        .data(photoUri.toUri())
                        // Key both caches on the stable blob id (not the ephemeral URL, and not
                        // size) so every avatar load of this rendition shares one entry that
                        // survives URL rotation and app restarts — which is also what lets a
                        // smaller rendition resolve via placeholderMemoryCacheKey across surfaces.
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .apply {
                            blurHash?.let { placeholder(it) }
                            previewKey?.let { placeholderMemoryCacheKey(it) }
                        }
                        .build()
                }
                AsyncImage(
                    modifier = Modifier.matchParentSize(),
                    model = request,
                    // Crop fills the avatar while preserving aspect ratio (Fit letterboxed and let
                    // the gradient show through; FillBounds fills but distorts the photo).
                    // AsyncImage infers the Coil request scale from this, so the request is fine.
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    onError = { isError = true },
                )
            }
            if (isError) {
                fallback()
            }
        } else {
            fallback()
        }
    }
}

@Composable
private fun UnknownContactAvatar(
    includeBorder: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Brush.linearGradient(CodeTheme.colors.contactAvatar.colors))
            .addIf(includeBorder) {
                Modifier.border(
                    CodeTheme.dimens.border,
                    CodeTheme.colors.divider,
                    CircleShape,
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1.2f
                    scaleX = scale
                    scaleY = scale
                    translationY = size.height * 0.18f
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.InitialsText(displayName: String) {
    val initials = remember(displayName) {
        displayName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
    val fontSize = with(LocalDensity.current) {
        (min(maxWidth, maxHeight) * 0.38f).toSp()
    }
    Text(
        modifier = Modifier.align(Alignment.Center),
        text = initials,
        style = CodeTheme.typography.textSmall.copy(fontSize = fontSize),
        color = CodeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}