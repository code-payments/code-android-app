package com.flipcash.app.bills.components.bills

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.ui.res.stringResource
import com.flipcash.app.bills.components.ScannableCode
import com.flipcash.shared.bills.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.DesignSystem
import com.getcode.ui.utils.nonScaledSp
import kotlin.math.abs
import kotlin.random.Random
import android.graphics.Paint as NativePaint

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoldBar(
    modifier: Modifier = Modifier,
    payloadData: List<Byte> = emptyList(),
    amount: Fiat,
) {
    val mint = if (LocalInspectionMode.current) {
        "5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ"
    } else {
        Mint.usdf.base58()
    }
    val tilt by rememberTiltState()
    val lightSource = tilt.lightSource
    val tiltRotation = tilt.rotation
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val outerCornerPx = with(density) { 10.dp.toPx() }
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(ASPECT_RATIO, matchHeightConstraintsFirst = true)
                .fillMaxHeight()
                .fillMaxWidth(0.95f)
                .testTag("gold_bar")
                // Gold tint — dip the whole bar in metallic gold
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = GoldTint,
                        blendMode = BlendMode.Softlight,
                        cornerRadius = CornerRadius(outerCornerPx)
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val maxWidth = constraints.maxWidth
            val barShape = remember { RoundedCornerShape(10.dp) }
            val bevelPx = with(density) { 16.dp.toPx() }

            // Neumorphic shadow — subtle highlight (top-left)
            Box(
                Modifier
                    .matchParentSize()
                    .offset { IntOffset(-5, -5) }
                    .blur(10.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(BevelHighlight.copy(alpha = 0.30f), barShape)
            )

            // Neumorphic shadow — cast shadow (bottom-right)
            Box(
                Modifier
                    .matchParentSize()
                    .offset { IntOffset(7, 9) }
                    .blur(16.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(Color.Black.copy(alpha = 0.60f), barShape)
            )

            // Metal texture — AGSL on API 33+ (GPU anisotropic specular),
            // classic bitmap/gradient approach below.
            // Border + bevel overlay is shared by both paths.
            val useAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            @Suppress("NewApi")
            val textureModifier = if (useAgsl) {
                Modifier.agslBrushedMetal(
                    shape = barShape,
                    lightSource = lightSource,
                    rotation = tiltRotation,
                )
            } else {
                Modifier.brushedMetal(
                    baseColor = GoldBase,
                    shape = barShape,
                    highlightAlpha = .50f,
                    highlightCount = 3,
                    ringAlpha = .22f,
                    ringCount = 50,
                    highlightRotation = 35f + (lightSource.x - 0.5f) * 90f + tiltRotation,
                    center = Offset(
                        lightSource.x,
                        lightSource.y,
                    ),
                    grainAlpha = 50,
                )
            }
            val metalModifier = textureModifier
                // Outer lip
                .border(
                    width = 3.dp,
                    shape = barShape,
                    brush = Brush.linearGradient(
                        0.0f to GoldLight.copy(alpha = 0.9f),
                        0.3f to GoldBase.copy(alpha = 0.5f),
                        0.6f to GoldDark.copy(alpha = 0.35f),
                        1.0f to GoldDark.copy(alpha = 0.25f),
                    ),
                )
                // All bevel + surface shading in one pass
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .bevelAndSurface(
                    outerCornerRadius = outerCornerPx,
                    bevelPx = bevelPx,
                    barShape = barShape,
                    lightSource = lightSource,
                )

            Box(
                Modifier
                    .matchParentSize()
                    .then(metalModifier),
                contentAlignment = Alignment.Center,
            ) {
                // Circle recess with code cutout, USD value, and bottom branding
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .recessWithCodeCutout(
                                lightSource = lightSource,
                                tiltRotation = tiltRotation,
                            )
                            .recessedCircleBevel(lightSource = lightSource),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (payloadData.isNotEmpty()) {
                            ScannableCode(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                data = payloadData,
                                icon = null,
                            )
                        }
                    }

                    // USD value — weighted to center between code and bottom content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val suffix = stringResource(R.string.displayName_usdf)
                        Text(
                            modifier = Modifier.recessed(),
                            text = amount.formatted(showPrefix = false, suffix = suffix),
                            fontSize = 42.nonScaledSp,
                            color = EmbossColor,
                        )
                    }

                    // Bottom content — mint + branding, embossed
                    if (mint.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 4.dp)
                                .recessed(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = mint,
                                fontSize = 8.nonScaledSp,
                                color = EmbossColor,
                                maxLines = 1,
                            )
                            Image(
                                modifier = Modifier.width(64.dp),
                                contentScale = ContentScale.FillWidth,
                                painter = painterResource(R.drawable.ic_flipcash_logo_offwhite_small),
                                colorFilter = ColorFilter.tint(EmbossColor),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Brushed Metal Modifier ───────────────────────────────────────────
// Adapted from https://www.sinasamaki.com/metal-power-button/

@Composable
private fun Modifier.brushedMetal(
    baseColor: Color = Color(0xFF9A9A9A),
    shape: Shape = RectangleShape,
    ringAlpha: Float = .2f,
    ringCount: Int = 40,
    highlightAlpha: Float = .5f,
    highlightCount: Int = 3,
    highlightRotation: Float = 0f,
    center: Offset = Offset(.5f, .5f),
    grainAlpha: Int = 30,
): Modifier {
    val highlightColor = remember(baseColor, highlightAlpha) {
        lerp(baseColor, Color.White, .5f).copy(alpha = highlightAlpha)
    }
    val ringColors = remember(ringCount, ringAlpha, baseColor) {
        val ringColor = lerp(baseColor, Color.Black, .5f).copy(alpha = ringAlpha)
        buildList {
            repeat((0..ringCount).count()) {
                repeat((0..Random.nextInt(1, 12)).count()) { add(Color.Transparent) }
                repeat((0..Random.nextInt(0, 2)).count()) { add(ringColor) }
            }
        }
    }
    // Directional brushed streak texture — each row is constant brightness,
    // creating horizontal lines. Rotated via shader matrix for diagonal streaks.
    val streakBitmap = remember {
        val h = 512
        val bmp = createBitmap(1, h)
        val rng = Random(13)
        for (y in 0 until h) {
            val v = 128 + rng.nextInt(-35, 36)
            bmp[0, y] = android.graphics.Color.argb(255, v, v, v)
        }
        bmp
    }
    return this
        .drawWithCache {
            val path = Path().apply {
                addOutline(
                    shape.createOutline(size, layoutDirection, Density(density))
                )
            }
            val streakShader = BitmapShader(
                streakBitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            ).apply {
                val matrix = Matrix()
                matrix.setRotate(highlightRotation)
                setLocalMatrix(matrix)
            }
            val streakPaint = NativePaint().apply {
                shader = streakShader
                alpha = grainAlpha
                xfermode = PorterDuffXfermode(
                    PorterDuff.Mode.OVERLAY
                )
            }
            onDrawBehind {
                clipPath(path) {
                    val center = Offset(center.x * size.width, center.y * size.height)
                    drawRect(color = baseColor)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = ringColors,
                            tileMode = TileMode.Repeated,
                            center = center,
                            radius = size.width * .25f,
                        ),
                        blendMode = BlendMode.Overlay,
                    )
                    // Directional brushed streaks — rotated parallel lines
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f, 0f, size.width, size.height, streakPaint
                        )
                    }
                    rotate(
                        degrees = highlightRotation,
                        pivot = center
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = buildList {
                                    add(highlightColor)
                                    repeat(highlightCount) {
                                        add(highlightColor.copy(alpha = 0f))
                                        if (it < highlightCount - 1) add(highlightColor)
                                    }
                                    add(highlightColor)
                                },
                                center = center
                            ),
                            radius = size.width * size.height
                        )
                    }
                }
            }
        }
}

// ── Neumorphic / Skeuomorphic helpers ────────────────────────────────

/**
 * Unified bevel + surface modifier. Draws on a single continuous brushed
 * metal texture — no separate layers, so no seam at the boundary.
 *
 * Effects drawn on top of content:
 * 1. Bevel outer bright band (top-left lit)
 * 2. Center ridge specular line
 * 3. Inner highlight strip (the bright ledge at the bevel-surface boundary)
 * 4. Surface inset shadows (top/left) and vertical sheen
 */
@Composable
private fun Modifier.bevelAndSurface(
    outerCornerRadius: Float,
    bevelPx: Float,
    barShape: Shape,
    lightSource: Offset = DefaultLightSource,
): Modifier {
    val density = LocalDensity.current
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val lStart = lightStart(w, h, lightSource)
        val lEnd = lightEnd(w, h, lightSource)

        // Bevel zone positions
        val s1 = bevelPx * 0.30f
        val sMid = bevelPx * 0.50f

        fun rrAt(inset: Float) = RoundRect(
            inset, inset, w - inset, h - inset, CornerRadius(outerCornerRadius)
        )
        fun pathAt(inset: Float) = Path().apply { addRoundRect(rrAt(inset)) }

        val outerPath = pathAt(0f)
        val s1Path = pathAt(s1)
        val s2 = bevelPx * 0.70f
        val s2Path = pathAt(s2)
        val innerPath = pathAt(bevelPx)

        // Surface clip for inset shadows
        val surfaceOutline = barShape.createOutline(
            Size(w - bevelPx * 2, h - bevelPx * 2), layoutDirection, density
        )
        val surfaceClip = Path().apply { addOutline(surfaceOutline) }
        val surfaceDeep = (w - bevelPx * 2) * 0.10f

        // Bevel outer face — bright near light, dark away
        val outerFaceBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.60f),
            0.20f to Color.White.copy(alpha = 0.28f),
            0.45f to Color.Black.copy(alpha = 0.06f),
            1.0f to Color.Black.copy(alpha = 0.38f),
            start = lStart, end = lEnd,
        )

        // Center ridge — specular line, visible all around
        val ridgeRr = rrAt(sMid)
        val ridgeBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.90f),
            0.25f to GoldLight.copy(alpha = 0.55f),
            0.50f to GoldBase.copy(alpha = 0.25f),
            0.75f to GoldDark.copy(alpha = 0.20f),
            1.0f to GoldBase.copy(alpha = 0.15f),
            start = lStart, end = lEnd,
        )

        // Inner bevel face — dark away from light (slope away)
        val innerFaceBrush = Brush.linearGradient(
            0.0f to Color.Transparent,
            0.40f to Color.Black.copy(alpha = 0.06f),
            0.65f to Color.Black.copy(alpha = 0.22f),
            1.0f to Color.Black.copy(alpha = 0.42f),
            start = lStart, end = lEnd,
        )

        // Inner highlight strip — bright ledge around entire perimeter
        val innerRr = rrAt(bevelPx)
        val innerHighlightBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.85f),
            0.30f to GoldLight.copy(alpha = 0.55f),
            0.60f to GoldBase.copy(alpha = 0.35f),
            1.0f to GoldLight.copy(alpha = 0.40f),
            start = lStart, end = lEnd,
        )

        onDrawWithContent {
            drawContent()

            // 1. Bevel outer bright band (outer edge → 30%)
            clipPath(outerPath) {
                clipPath(s1Path, clipOp = ClipOp.Difference) {
                    drawRect(brush = outerFaceBrush)
                }
            }

            // 2. Bevel inner dark band (70% → inner edge)
            clipPath(s2Path) {
                clipPath(innerPath, clipOp = ClipOp.Difference) {
                    drawRect(brush = innerFaceBrush)
                }
            }

            // 3. Center ridge
            drawRoundRect(
                brush = ridgeBrush,
                topLeft = Offset(ridgeRr.left, ridgeRr.top),
                size = Size(ridgeRr.width, ridgeRr.height),
                cornerRadius = CornerRadius(ridgeRr.topLeftCornerRadius.x),
                style = Stroke(width = 3f),
            )

            // 4. Inner highlight strip — bright ledge at boundary
            drawRoundRect(
                brush = innerHighlightBrush,
                topLeft = Offset(innerRr.left, innerRr.top),
                size = Size(innerRr.width, innerRr.height),
                cornerRadius = CornerRadius(innerRr.topLeftCornerRadius.x),
                style = Stroke(width = 2.5f),
            )

            // 5. Surface shading — translated into the surface area
            val sw = w - bevelPx * 2
            val sh = h - bevelPx * 2
            translate(left = bevelPx, top = bevelPx) {
                clipPath(surfaceClip) {
                    // Diagonal sheen — sweeps with tilt
                    drawRect(
                        brush = Brush.linearGradient(
                            0.0f to Color.White.copy(alpha = 0.45f),
                            0.15f to Color.White.copy(alpha = 0.20f),
                            0.35f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.15f),
                            1.0f to Color.Black.copy(alpha = 0.35f),
                            start = lightStart(sw, sh, lightSource),
                            end = lightEnd(sw, sh, lightSource),
                        ),
                        size = Size(sw, sh),
                    )
                    // Top-to-bottom sheen
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.22f),
                            0.12f to Color.White.copy(alpha = 0.10f),
                            0.30f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.10f),
                            1.0f to Color.Black.copy(alpha = 0.22f),
                            endY = sh,
                        ),
                        size = Size(sw, sh),
                    )
                    // Tilt-tracking hotspot — bright disc follows device tilt
                    val sl = safeLight(lightSource)
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.40f),
                            0.15f to Color.White.copy(alpha = 0.25f),
                            0.4f to Color.White.copy(alpha = 0.08f),
                            1.0f to Color.Transparent,
                            center = Offset(sl.x * sw, sl.y * sh),
                            radius = (sw * 0.50f).coerceAtLeast(0.01f),
                        ),
                        size = Size(sw, sh),
                    )
                    // Top edge inset shadow
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.15f to Color.Black.copy(alpha = 0.14f),
                            0.4f to Color.Black.copy(alpha = 0.03f),
                            1.0f to Color.Transparent,
                            endY = surfaceDeep,
                        ),
                        size = Size(sw, sh),
                    )
                    // Left edge inset shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Black.copy(alpha = 0.30f),
                            0.15f to Color.Black.copy(alpha = 0.12f),
                            0.4f to Color.Black.copy(alpha = 0.02f),
                            1.0f to Color.Transparent,
                            endX = surfaceDeep,
                        ),
                        size = Size(sw, sh),
                    )
                }
            }
        }
    }
}

/**
 * Draws a full brushed-metal recessed circle, adds neumorphic
 * shadow/highlight around code shapes, then punches code-shaped
 * cutouts to reveal the bar's brighter metal underneath.
 */
@Composable
private fun Modifier.recessWithCodeCutout(
    lightSource: Offset = DefaultLightSource,
    tiltRotation: Float = 0f,
): Modifier {
    val highlightColor = remember {
        lerp(RecessBase, Color.White, .5f).copy(alpha = .25f)
    }
    val density = LocalDensity.current
    return this.drawWithContent {
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent

        val circlePath = Path().apply {
            addOutline(
                CircleShape.createOutline(size, layoutDirection, density)
            )
        }
        val androidCirclePath = circlePath.asAndroidPath()
        val center = Offset(size.width * 0.5f, size.height * 0.5f)

        // 1. Full brushed-metal recess
        clipPath(circlePath) {
            drawRect(color = RecessBase)

            // Concentric lathe grooves — pixel-snapped, no anti-alias bokeh
            val maxR = size.minDimension / 2f
            val grooveDark = lerp(RecessBase, Color.Black, .50f).copy(alpha = .30f)
            val grooveLight = lerp(RecessBase, Color.White, .25f).copy(alpha = .18f)
            val grooveCount = 50
            val strokePx = 1f // exactly 1 physical pixel — crisp, no AA blur
            val groovePaint = NativePaint().apply {
                style = NativePaint.Style.STROKE
                strokeWidth = strokePx
                isAntiAlias = false // pixel-sharp, no soft halos
            }
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                for (i in 0 until grooveCount) {
                    val t = i.toFloat() / grooveCount
                    val r = maxR * (0.05f + 0.93f * t)
                    // Dark groove
                    groovePaint.color = android.graphics.Color.argb(
                        (grooveDark.alpha * 255).toInt(),
                        (grooveDark.red * 255).toInt(),
                        (grooveDark.green * 255).toInt(),
                        (grooveDark.blue * 255).toInt(),
                    )
                    groovePaint.strokeWidth = if (i % 4 == 0) 2f else strokePx
                    nc.drawCircle(center.x, center.y, r, groovePaint)
                    // Bright ridge
                    groovePaint.color = android.graphics.Color.argb(
                        (grooveLight.alpha * 255).toInt(),
                        (grooveLight.red * 255).toInt(),
                        (grooveLight.green * 255).toInt(),
                        (grooveLight.blue * 255).toInt(),
                    )
                    groovePaint.strokeWidth = strokePx
                    nc.drawCircle(center.x, center.y, r - 1f, groovePaint)
                }
            }

            // Sweep highlight — rotates with Z-axis tilt
            val maxDim = maxOf(size.width, size.height)
            rotate(degrees = tiltRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = buildList {
                            add(highlightColor.copy(alpha = 0.40f))
                            repeat(2) {
                                add(highlightColor.copy(alpha = 0f))
                                if (it < 1) add(highlightColor.copy(alpha = 0.40f))
                            }
                            add(highlightColor.copy(alpha = 0.40f))
                        },
                        center = center,
                    ),
                    radius = maxDim * 2f,
                )
            }

            // Rim shadow — light blocked by rim on the light-source side
            val rimRadius = (size.width * 0.5f).coerceAtLeast(0.01f)
            val sl = safeLight(lightSource)
            val rimShadowCenter = Offset(
                size.width * (0.5f + (sl.x - 0.5f) * 0.8f),
                size.height * (sl.y * 0.3f + 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.Black.copy(alpha = 0.45f),
                    0.3f to Color.Black.copy(alpha = 0.20f),
                    0.6f to Color.Transparent,
                    1.0f to Color.Transparent,
                    center = rimShadowCenter,
                    radius = rimRadius,
                ),
            )
            // Directional shading — dark on light side, bright opposite
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.Black.copy(alpha = 0.25f),
                    0.20f to Color.Black.copy(alpha = 0.10f),
                    0.45f to Color.Transparent,
                    0.80f to Color.White.copy(alpha = 0.06f),
                    1.0f to Color.White.copy(alpha = 0.20f),
                    start = lightStart(size.width, size.height, lightSource),
                    end = lightEnd(size.width, size.height, lightSource),
                ),
            )
            // Diagonal accent — opposite side catches reflected light
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.Black.copy(alpha = 0.14f),
                    0.30f to Color.Black.copy(alpha = 0.04f),
                    0.50f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.12f),
                    start = lightStart(size.width, size.height, lightSource),
                    end = lightEnd(size.width, size.height, lightSource),
                ),
            )
        }

        // Density-scaled offsets for neumorphic edges
        val d = density.density

        // Helper to draw a tinted offset copy of the code, clipped to circle
        fun drawCodePass(
            dx: Float, dy: Float, argb: Int,
        ) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipPath(androidCirclePath)
                val sc = canvas.nativeCanvas.saveLayer(null, null)
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(dx, dy)
                this@drawWithContent.drawContent()
                canvas.nativeCanvas.restore()
                canvas.nativeCanvas.drawRect(
                    0f, 0f, size.width, size.height,
                    NativePaint().apply {
                        xfermode = PorterDuffXfermode(
                            PorterDuff.Mode.SRC_ATOP
                        )
                        color = argb
                    },
                )
                canvas.nativeCanvas.restoreToCount(sc)
                canvas.nativeCanvas.restore()
            }
        }

        // Shadow/highlight offsets derived from light direction
        val (sDx, sDy) = shadowOffset(d, lightSource)
        val shadowScale = 0.8f
        val highlightScale = 0.3f

        // 2. Cast shadow — away from light, strong
        drawCodePass(
            dx = sDx * shadowScale, dy = sDy * shadowScale,
            argb = android.graphics.Color.argb(120, 0, 0, 0),
        )
        // 3. Highlight edge — toward light (catching the raised rim)
        drawCodePass(
            dx = -sDx * highlightScale, dy = -sDy * highlightScale,
            argb = android.graphics.Color.argb(50, 255, 255, 255),
        )

        // 4. Punch cutout holes — reveals the bar's metal.
        drawIntoCanvas { canvas ->
            val sc = canvas.nativeCanvas.saveLayer(
                null,
                NativePaint().apply {
                    xfermode = PorterDuffXfermode(
                        PorterDuff.Mode.DST_OUT
                    )
                },
            )
            this@drawWithContent.drawContent()
            canvas.nativeCanvas.restoreToCount(sc)
        }
    }
}

/**
 * Wide beveled channel rim for the recessed circle.
 * Mimics the reference's machined groove: outer bright lip → dark channel
 * → inner shadow edge → surface. Like a miniature version of the bar's bevel.
 */
@Composable
private fun Modifier.recessedCircleBevel(
    lightSource: Offset = DefaultLightSource,
): Modifier {
    val bevelWidth = with(LocalDensity.current) { 6.dp.toPx() }
    return this.drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent

        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val lStart = lightStart(size.width, size.height, lightSource)
        val lEnd = lightEnd(size.width, size.height, lightSource)

        // Inner surface shadow — very narrow, just at the edge
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.92f to Color.Transparent,
                0.97f to Color.Black.copy(alpha = 0.12f),
                1.0f to Color.Black.copy(alpha = 0.22f),
                center = Offset(cx, cy),
                radius = r.coerceAtLeast(0.01f),
            ),
        )

        // Directional shadow — bias toward light source
        drawCircle(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.15f),
                0.18f to Color.Black.copy(alpha = 0.04f),
                0.35f to Color.Transparent,
                1.0f to Color.Transparent,
                start = lStart,
                end = lEnd,
            ),
        )

        // ── Beveled channel (3 concentric strokes) ──────────
        // 1. Inner dark face — the "wall" going down into the recess
        drawCircle(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.80f),
                0.30f to Color.Black.copy(alpha = 0.50f),
                0.55f to RecessDark.copy(alpha = 0.20f),
                1.0f to Color.Transparent,
                start = lStart,
                end = lEnd,
            ),
            radius = r - bevelWidth * 0.3f,
            style = Stroke(width = bevelWidth * 0.5f),
        )
        // 2. Channel ridge — bright specular line at the midpoint
        drawCircle(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.35f),
                0.25f to RecessDark.copy(alpha = 0.20f),
                0.45f to GoldBase.copy(alpha = 0.65f),
                0.65f to GoldLight.copy(alpha = 0.85f),
                1.0f to BevelHighlight.copy(alpha = 0.95f),
                start = lStart,
                end = lEnd,
            ),
            radius = r,
            style = Stroke(width = 4f),
        )
        // 3. Outer bright lip — the top edge catching overhead light
        drawCircle(
            brush = Brush.linearGradient(
                0.0f to Color.Black.copy(alpha = 0.40f),
                0.20f to RecessDark.copy(alpha = 0.20f),
                0.40f to GoldBase.copy(alpha = 0.60f),
                0.55f to GoldLight.copy(alpha = 0.80f),
                0.75f to BevelHighlight.copy(alpha = 0.92f),
                1.0f to Color.White.copy(alpha = 0.80f),
                start = lStart,
                end = lEnd,
            ),
            radius = r + bevelWidth * 0.35f,
            style = Stroke(width = bevelWidth * 0.7f),
        )
    }
}


// ── AGSL Metal Texture (API 33+) ────────────────────────────────────

/**
 * GPU-accelerated brushed metal texture via AGSL [RuntimeShader].
 * Drop-in replacement for [brushedMetal] — provides multi-octave
 * directional streaks, concentric ring marks, and anisotropic specular
 * highlights, all computed per-pixel on the GPU.
 *
 * Bevel, border, and surface shading are handled by the shared
 * [bevelAndSurface] Compose overlay (same as the classic path).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.agslBrushedMetal(
    shape: Shape,
    lightSource: Offset = DefaultLightSource,
    rotation: Float = 0f,
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(AGSL_BRUSHED_METAL)
    shader.setFloatUniform("iResolution", size.width, size.height)
    val sl = safeLight(lightSource)
    shader.setFloatUniform("iTilt", sl.x, sl.y)
    shader.setFloatUniform("iRotation", rotation)
    val brush = ShaderBrush(shader)
    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, Density(density)))
    }
    onDrawBehind {
        clipPath(path) {
            drawRect(brush = brush)
        }
    }
}

/**
 * AGSL shader — brushed gold metal texture.
 * Adapted from the proven FLIPCASH_SHADER approach:
 * 1. Gold ramp (3-stop vertical gradient) — the metallic body
 * 2. Pixel-space brush hash — fine additive brush lines
 * 3. Dual sheen bands — concentrated diagonal specular
 *
 * Outputs warm gray tones — the Compose SoftLight [GoldTint] pass
 * adds the gold hue, same as the classic path.
 *
 * Bevel, border, and surface shading handled by Compose overlays.
 */
private const val AGSL_BRUSHED_METAL = """
uniform float2 iResolution;
uniform float2 iTilt;      // normalized 0..1, 0.5 = neutral
uniform float  iRotation;  // degrees, specular band rotation from Z-axis yaw

float hash(float n) { return fract(sin(n) * 43758.5453); }

half3 grayRamp(float y) {
    half3 hi  = half3(0.84, 0.80, 0.72);
    half3 mid = half3(0.76, 0.72, 0.64);
    half3 lo  = half3(0.68, 0.64, 0.56);
    return y < 0.5 ? mix(hi, mid, half(y * 2.0))
                    : mix(mid, lo, half((y - 0.5) * 2.0));
}

// Rotate a 2D vector by angle in radians
float2 rot(float2 v, float a) {
    float c = cos(a); float s = sin(a);
    return float2(v.x * c - v.y * s, v.x * s + v.y * c);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;

    // ── 1. Gold ramp ────────────────────────────────────────
    half3 col = grayRamp(uv.y);

    // ── 2. Brush lines — base angle 35° + Z-rotation ───────
    float rotRad = iRotation * 0.01745329;  // deg → rad
    float angle = 0.6109 + rotRad;
    float bc = fragCoord.x * sin(angle) + fragCoord.y * cos(angle);

    float c1 = bc * 1.5;
    float i1 = floor(c1); float f1 = fract(c1);
    float b1 = mix(hash(i1), hash(i1 + 1.0), f1 * f1 * (3.0 - 2.0 * f1)) - 0.5;

    float c2 = bc * 0.25;
    float i2 = floor(c2); float f2 = fract(c2);
    float b2 = mix(hash(i2 + 100.0), hash(i2 + 101.0), f2 * f2 * (3.0 - 2.0 * f2)) - 0.5;

    float c3 = bc * 0.04;
    float i3 = floor(c3); float f3 = fract(c3);
    float b3 = mix(hash(i3 + 200.0), hash(i3 + 201.0), f3 * f3 * (3.0 - 2.0 * f3)) - 0.5;

    col += half(b1 * 0.015 + b2 * 0.012 + b3 * 0.010);

    // ── 3. Holographic specular sweep ───────────────────────
    float tiltX = iTilt.x - 0.5;
    float tiltY = iTilt.y - 0.5;

    // Rotate UV for specular axes — Z-rotation twists the bands
    float2 centered = uv - 0.5;
    float2 ruv = rot(centered, rotRad) + 0.5;

    // Primary diagonal band — sweeps across bar on X tilt, rotates on Z
    float axis1 = ruv.x * 0.6 + ruv.y * 0.4;
    float center1 = 0.5 + tiltX * 1.8;
    float dist1 = abs(axis1 - center1);
    col += half(0.25) * half(smoothstep(0.30, 0.0, dist1));
    col += half(0.20) * half(smoothstep(0.10, 0.0, dist1));

    // Secondary cross-band — perpendicular, responds to Y tilt
    float axis2 = ruv.x * 0.4 - ruv.y * 0.6 + 0.5;
    float center2 = 0.5 + tiltY * 1.4;
    float dist2 = abs(axis2 - center2);
    col += half(0.12) * half(smoothstep(0.25, 0.0, dist2));

    // ── 4. Radial hotspot — follows all 3 axes ─────────────
    // X/Y tilt moves the disc, Z rotation orbits it slightly
    float2 hotOff = rot(float2(tiltX * 1.2, tiltY * 1.2), rotRad * 0.5);
    float2 hotCenter = float2(0.5, 0.5) + hotOff;
    float hotDist = length(uv - hotCenter);
    col += half(0.18) * half(smoothstep(0.35, 0.05, hotDist));

    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0);
}
"""



// ── Emboss / Recess effects ──────────────────────────────────────────

private val EmbossColor = Color(0xFFCBB060)

// Recessed content color — slightly darker than surface, readable
private val RecessedColor = Color(0xFF8A7A50)

/**
 * Simulates recessed/engraved metal — shadow on top-left (edge going
 * down into the surface), highlight on bottom-right (light catching
 * the far rim).
 */
@Composable
private fun Modifier.recessed(): Modifier {
    val d = LocalDensity.current.density
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            val draw = { this@drawWithContent.drawContent() }
            // 1. Highlight — below-right (light catching the far rim)
            translate(left = 0.4f * d, top = 0.5f * d) {
                draw()
                drawRect(
                    color = Color.White.copy(alpha = 0.40f),
                    blendMode = BlendMode.SrcAtop,
                )
            }
            // 2. Shadow — above-left (edge dropping into the surface)
            translate(left = -0.3f * d, top = -0.5f * d) {
                draw()
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    blendMode = BlendMode.SrcAtop,
                )
            }
            // 3. Main content — dark recessed fill
            draw()
            drawRect(
                color = RecessedColor,
                blendMode = BlendMode.SrcAtop,
            )
        }
}

// ── Tilt sensor ─────────────────────────────────────────────────────

/**
 * 3-axis tilt state for holographic light response.
 * @param x horizontal light position 0..1 (from roll)
 * @param y vertical light position 0..1 (from pitch)
 * @param rotation specular band rotation in degrees (from yaw delta)
 */
private data class TiltState(
    val x: Float = DefaultLightSource.x,
    val y: Float = DefaultLightSource.y,
    val rotation: Float = 0f,
) {
    val lightSource: Offset get() = Offset(x, y)
}

private val DefaultTilt = TiltState()

/**
 * Tracks all 3 axes of device orientation:
 * - Roll  → horizontal specular position (x)
 * - Pitch → vertical specular position (y)
 * - Yaw   → specular band rotation angle (relative to initial heading)
 *
 * ±15° of pitch/roll covers full 0..1 range.
 * ±45° of yaw covers ±45° of specular rotation.
 */
@Composable
private fun rememberTiltState(): State<TiltState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(DefaultTilt) }

    DisposableEffect(Unit) {
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return@DisposableEffect onDispose { }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return@DisposableEffect onDispose { }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var refPitch: Float? = null
        var refAzimuth: Float? = null
        // Low-pass filter state — smooths out sensor noise
        var smoothX = 0.5f
        var smoothY = 0.5f
        var smoothRot = 0f
        val alpha = 0.10f // blend factor: lower = smoother, higher = snappier
        val deadZone = 0.01f // ignore changes smaller than this

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                val azimuth = orientation[0]
                val pitch = orientation[1]
                val roll = orientation[2]

                if (azimuth.isNaN() || pitch.isNaN() || roll.isNaN()) return@onSensorChanged

                if (refPitch == null) refPitch = pitch
                if (refAzimuth == null) refAzimuth = azimuth
                val maxAngle = (Math.PI / 12).toFloat() // ±15°

                val rawX = ((roll / maxAngle) * 0.5f + 0.5f).coerceIn(0f, 1f)
                val rawY = ((-(pitch - (refPitch ?: pitch)) / maxAngle) * 0.5f + 0.5f).coerceIn(0f, 1f)

                var dAzimuth = azimuth - (refAzimuth ?: azimuth)
                if (dAzimuth > Math.PI.toFloat()) dAzimuth -= (2 * Math.PI).toFloat()
                if (dAzimuth < -Math.PI.toFloat()) dAzimuth += (2 * Math.PI).toFloat()
                val maxYaw = (Math.PI / 4).toFloat()
                val rawRot = (dAzimuth / maxYaw).coerceIn(-1f, 1f) * 45f

                // Exponential moving average
                smoothX += alpha * (rawX - smoothX)
                smoothY += alpha * (rawY - smoothY)
                smoothRot += alpha * (rawRot - smoothRot)

                // Dead zone — only update state if change is perceptible
                val prev = state.value
                val dx = abs(smoothX - prev.x)
                val dy = abs(smoothY - prev.y)
                val dr = abs(smoothRot - prev.rotation)
                if (dx > deadZone || dy > deadZone || dr > 0.5f) {
                    state.value = TiltState(smoothX, smoothY, smoothRot)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return state
}

// ── Light source ─────────────────────────────────────────────────────

private val DefaultLightSource = Offset(0.5f, 0.5f)

/** Sanitize light offset — NaN falls back to default. */
private fun safeLight(light: Offset): Offset {
    val x = if (light.x.isNaN()) DefaultLightSource.x else light.x
    val y = if (light.y.isNaN()) DefaultLightSource.y else light.y
    return Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * Gradient start — bright side. Tilt shifts the gradient direction.
 * At default (0.5, 0.5) gradient runs top-left → bottom-right.
 * Tilting shifts the bright edge across the surface.
 */
private fun lightStart(w: Float, h: Float, light: Offset = DefaultLightSource): Offset {
    val s = safeLight(light)
    // Map 0..1 to gradient position: 0 → near edge, 1 → far edge
    val x = (s.x * 0.4f).coerceIn(0.001f, w - 0.001f)
    val y = (s.y * 0.4f).coerceIn(0.001f, h - 0.001f)
    return Offset(x * w, y * h)
}

/** Gradient end — dark side, opposite the light source. */
private fun lightEnd(w: Float, h: Float, light: Offset = DefaultLightSource): Offset {
    val s = safeLight(light)
    val x = (1f - s.x * 0.4f).coerceIn(0.001f, 0.999f)
    val y = (1f - s.y * 0.4f).coerceIn(0.001f, 0.999f)
    return Offset(x * w, y * h)
}

/** Shadow offset direction — away from the light, scaled by dp factor. */
private fun shadowOffset(d: Float, light: Offset = DefaultLightSource): Pair<Float, Float> {
    val dx = (0.5f - light.x) * 2f
    val dy = (0.5f - light.y) * 2f
    return (dx * d) to (dy * d)
}


// Gold tint — applied over the whole bar via BlendMode.SoftLight
private val GoldTint = Color(0xFFDEA828)

// Base colors — warm gray, the tint pass adds the gold hue
private val GoldBase = Color(0xFFB5AA96)
private val GoldDark = lerp(GoldBase, Color.Black, 0.40f)
private val GoldLight = lerp(GoldBase, Color.White, 0.40f)

private val BevelHighlight = lerp(GoldBase, Color.White, 0.60f)

// Circle recess — pre-compensated for gold SoftLight tint.
// Looks gray raw; renders as warm golden bronze (≈0xFF7A6335) after tint.
private val RecessBase = Color(0xFF4C4F5E)
// Used by neumorphicInset for shadow/rim
private val RecessDark = lerp(GoldBase, Color.Black, 0.30f)

private const val ASPECT_RATIO = 0.555f

// 39 bytes fills all 6 KikCode rings — mixed bits for realistic dot pattern
private val PREVIEW_CODE_DATA = listOf(
    0xA5, 0x3C, 0xD7, 0x8B, 0x14, 0xE9, 0x62, 0xF0,
    0x4D, 0xB6, 0x29, 0x7A, 0xC3, 0x58, 0x91, 0xDE,
    0x6F, 0x03, 0xB4, 0x87, 0x2C, 0xE5, 0x50, 0xA9,
    0x1E, 0x73, 0xC6, 0x3F, 0x98, 0x41, 0xDA, 0x65,
    0x0B, 0xF2, 0x7D, 0xAE, 0x53, 0xC0, 0x19,
).map { it.toByte() }

@Preview
@Composable
private fun Preview_GoldBar_Agsl() {
    DesignSystem {
        GoldBar(
            payloadData = PREVIEW_CODE_DATA,
            amount = Fiat(3.00),
        )
    }
}

@Preview(apiLevel = 31)
@Composable
private fun Preview_GoldBar_Fallback() {
    DesignSystem {
        GoldBar(
            payloadData = PREVIEW_CODE_DATA,
            amount = Fiat(3.00),
        )
    }
}
