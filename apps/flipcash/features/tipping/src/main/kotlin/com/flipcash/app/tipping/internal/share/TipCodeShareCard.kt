package com.flipcash.app.tipping.internal.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.min
import com.flipcash.app.bills.TipCodeGraphic
import com.flipcash.app.core.bill.Scannable
import com.getcode.theme.CodeTheme

/**
 * Output shapes for the share preview. [widthPx]/[heightPx] are the exact bitmap dimensions the
 * renderer targets; [codeFraction] is how much of the canvas's smaller side the (square) code fills.
 * The square is a genuine recomposition, not a center-crop of the hero.
 */
enum class TipCodeShareVariant(
    val widthPx: Int,
    val heightPx: Int,
    val codeFraction: Float,
) {
    // Landscape unfurl shape (OpenGraph-style); the code is limited by the shorter (height) side.
    Hero(widthPx = 1200, heightPx = 630, codeFraction = 0.9f),
    // 1:1 for the Sharesheet thumbnail slot.
    Square(widthPx = 1080, heightPx = 1080, codeFraction = 0.82f),
}

/**
 * A dedicated, fixed-size rendering of a tip code for the Android Sharesheet preview: just the
 * scannable code, large and centered on an opaque background. No card chrome, no name.
 *
 * Unlike the on-screen card, this does not size itself off its container — the caller measures it at
 * an explicit pixel size with a pinned density, so output is identical across devices.
 */
@Composable
fun TipCodeShareCard(
    card: Scannable.TipCard,
    variant: TipCodeShareVariant,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // Opaque background — same brand tone that sits behind the live card.
            .background(CodeTheme.colors.brand),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Square code, sized off the shorter side so it stays large but never clips.
            val codeSize = min(maxWidth, maxHeight) * variant.codeFraction
            TipCodeGraphic(
                payloadData = card.data,
                modifier = Modifier.size(codeSize),
            )
        }
    }
}
