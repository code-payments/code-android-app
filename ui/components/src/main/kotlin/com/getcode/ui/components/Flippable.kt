package com.getcode.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

enum class CardFace(
    val angle: Float,
    var noAnim: Boolean = false
) {
    Front(0f) {
        override val next: CardFace
            get() = Back
    },

    Back(180f) {
        override val next: CardFace
            get() = Front
    },

    Neither(90f) {
        override val next: CardFace
            get() = Front
    };

    abstract val next: CardFace
}


@Composable
fun FlippableCard(
    modifier: Modifier = Modifier,
    cardFace: CardFace,
    flipMs: Int = 400,
    animateFlip: Boolean = true,
    back: @Composable BoxScope.() -> Unit = {},
    front: @Composable BoxScope.() -> Unit = {},
) {
    val rotation = if (animateFlip) {
        animateFloatAsState(
            targetValue = cardFace.angle,
            animationSpec = tween(
                durationMillis = if (cardFace.noAnim) 0 else flipMs,
                easing = FastOutSlowInEasing,
            )
        )
    } else {
        object : State<Float> {
            override val value: Float
                get() = cardFace.angle

        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 20f * density
            }
    ) {
        if (cardFace != CardFace.Neither) {
            if (rotation.value <= 90f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    front()
                }
            } else {
                Box(
                    Modifier
                        .graphicsLayer {
                            rotationY = 180f
                        }.fillMaxSize(),
                ) {
                    back()
                }
            }
        }
    }
}