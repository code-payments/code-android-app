package com.flipcash.shared.chat.ui.media

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.app.Dialog
import android.net.Uri
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Immutable
import com.getcode.ui.components.chat.ChatInputDefaults
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme

/** The most photos a message can carry; media rows go away once this many are staged. */
const val MAX_STAGED_PHOTOS = 10

/** A row in the attach menu. */
enum class AttachRow { Cash, Camera, Photos }

/**
 * The rows the attach menu lists, in order: Cash when the chat offers it, then Camera and Photos.
 * Camera and Photos need a chat that [acceptsMedia] and room for more than [stagedCount] photos.
 */
fun attachRows(cashOffered: Boolean, acceptsMedia: Boolean, stagedCount: Int): List<AttachRow> =
    buildList {
        if (cashOffered) add(AttachRow.Cash)
        if (acceptsMedia && stagedCount < MAX_STAGED_PHOTOS) {
            add(AttachRow.Camera)
            add(AttachRow.Photos)
        }
    }

internal fun attachRowTag(row: AttachRow): String = "attach_row_${row.name.lowercase()}"

/**
 * The small card listing [rows]. Separate from [AttachPopover] so it can be placed by something
 * other than a popup window, and tested without one.
 */
@Composable
fun AttachMenu(
    rows: List<AttachRow>,
    onRowClick: (AttachRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = attachMetrics()
    val shape = RoundedCornerShape(metrics.menuCorner)
    AttachMenuRows(
        rows = rows,
        onRowClick = onRowClick,
        modifier = modifier
            .shadow(CodeTheme.dimens.staticGrid.x2, shape)
            .clip(shape)
            .background(CodeTheme.colors.surface)
            .width(metrics.menuWidth),
    )
}

/** The rows alone, on whatever surface the host draws. */
@Composable
internal fun AttachMenuRows(
    rows: List<AttachRow>,
    onRowClick: (AttachRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = attachMetrics()
    val grid = CodeTheme.dimens.staticGrid
    Column(modifier = modifier.padding(vertical = metrics.menuVerticalPadding)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(attachRowTag(row))
                    .clickable(role = Role.Button) { onRowClick(row) }
                    .height(metrics.menuRowHeight)
                    .padding(horizontal = grid.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(grid.x8)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = CodeTheme.colors.textMain,
                        modifier = Modifier.size(grid.x4),
                    )
                }
                Spacer(Modifier.width(grid.x3))
                Text(
                    text = stringResource(row.label),
                    style = CodeTheme.typography.textMedium.copy(fontSize = 18.sp),
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    }
}

private val AttachRow.icon: ImageVector
    get() = when (this) {
        AttachRow.Cash -> Icons.Outlined.Payments
        AttachRow.Camera -> Icons.Outlined.PhotoCamera
        AttachRow.Photos -> Icons.Outlined.PhotoLibrary
    }

private val AttachRow.label: Int
    get() = when (this) {
        AttachRow.Cash -> R.string.action_chatAttachCash
        AttachRow.Camera -> R.string.action_chatAttachCamera
        AttachRow.Photos -> R.string.action_chatAttachPhotos
    }

/**
 * The attach surface's measurements, from the theme's grid so they follow the screen's size class.
 * The menu's width and row metrics follow iOS's `AttachSurfaceLayout`, each at the nearest grid step.
 * [fieldPadding], [barContentHeight] and the chip's size and corner are the composer's own, read
 * from the same tokens so the menu stays aligned with the field and a capture lands on its chip.
 */
@Immutable
internal data class AttachMetrics(
    val menuWidth: Dp,
    val menuRowHeight: Dp,
    val menuVerticalPadding: Dp,
    val menuCorner: Dp,
    /** The composer field's padding: the menu's leading edge sits this far out from "+"'s. */
    val fieldPadding: Dp,
    /** The menu reaches out past the field to this inset from the screen's edge, over the "$" button. */
    val menuLeadingInset: Dp,
    /** Height of a one-line composer field plus its padding: iOS `BarMetrics.contentHeight`. */
    val barContentHeight: Dp,
    /** How far the open menu reaches above the composer field's top edge. */
    val fieldTopOverhang: Dp,
    /** How far past "+"'s size the surface has fully turned from "+"'s fill into the glass. */
    val plusBlendDistance: Dp,
    val cardFallbackHeight: Dp,
    /** The margin between the card and the screen's sides and bottom: iOS `AttachOverlayLayout.cardInset`. */
    val cardInset: Dp,
    val cardCorner: Dp,
    /** A staged chip's radius, which a landing ends on. */
    val chipCorner: Dp,
    val chipSize: Dp,
)

@Composable
internal fun attachMetrics(): AttachMetrics {
    val grid = CodeTheme.dimens.staticGrid
    return AttachMetrics(
        menuWidth = grid.x20 * 2 + grid.x4,
        menuRowHeight = grid.x12,
        menuVerticalPadding = grid.x2,
        menuCorner = grid.x3,
        fieldPadding = ChatInputDefaults.FieldPadding,
        menuLeadingInset = grid.x3,
        barContentHeight = ChatInputDefaults.OutsideSize,
        fieldTopOverhang = grid.x2,
        plusBlendDistance = grid.x8,
        cardFallbackHeight = grid.x20 * 5,
        cardInset = grid.x2,
        cardCorner = grid.x5,
        chipCorner = PhotoChipDefaults.Corner,
        chipSize = PhotoChipDefaults.Size,
    )
}
// How quickly the shot's aim catches up with where the chip is: about two frames behind it.
private const val ShotAimTauNanos = 30_000_000f
private const val ShotProgressThreshold = 0.001f
private const val BlurRadiusPx = 40
private val Tint = Color(0xFF48484A)
// Over a blur the tint stays mostly opaque: the blur isn't drawn on every device that reports it
// enabled (emulators, some GPUs), and at 0.4 the composer showed through sharp behind the rows.
private const val GlassTintAlpha = 0.85f
private const val SolidTintAlpha = 0.97f

internal fun menuHeight(rowCount: Int, rowHeight: Dp, verticalPadding: Dp): Dp = rowHeight * rowCount + verticalPadding * 2

/**
 * The menu's top-left in screen pixels for a "+" at [plus]: its leading edge on the composer
 * field's, centred on "+"'s bottom edge, lifted to clear the field's top by `overhangPx`.
 * Port of iOS `AttachOverlayLayout.panelFrame`.
 */
internal fun menuOrigin(plus: Rect, heightPx: Float, fieldPaddingPx: Float, contentHeightPx: Float, overhangPx: Float, leadingPx: Float): Offset {
    val fieldTop = plus.bottom + fieldPaddingPx - contentHeightPx
    val y = minOf(plus.bottom - heightPx / 2f, fieldTop - overhangPx)
    // iOS reaches the leading edge out to `leadingPx` from the screen edge (menuLeadingReach).
    return Offset(leadingPx, y)
}

/**
 * [menu] lifted, if need be, so its bottom stands no lower than [limitBottom]. With the keyboard
 * down "+" is close to the screen's bottom and the menu, which reaches below it, would run off.
 */
internal fun menuClampedAbove(menu: Rect, limitBottom: Float): Rect =
    if (menu.bottom <= limitBottom) menu else menu.translate(0f, limitBottom - menu.bottom)

/**
 * Where the menu's bottom may reach: over the keyboard the screen's bottom, as iOS lets it hang
 * over the keys; with the keyboard down the composer's bottom edge, so it sits level with the field.
 */
internal fun menuLimitBottom(screenBottom: Float, composerBottom: Float, keyboardUp: Boolean): Float =
    if (keyboardUp) screenBottom else minOf(screenBottom, composerBottom)

/**
 * How much of the frosted glass shows at [rect] against "+"'s flat fill: none at "+"'s size, all of
 * it once the surface is [blendPx] bigger, so the two meet without a colour jump.
 */
internal fun attachGlassAmount(rect: Rect, plus: Rect, blendPx: Float): Float {
    val growth = maxOf(rect.width - plus.width, rect.height - plus.height)
    return (growth / blendPx).coerceIn(0f, 1f)
}

/** What the attach surface is showing. */
enum class AttachPhase {
    /** Shrunk into "+" and gone: on its way in or out. */
    Collapsed,

    /** The menu's rows. */
    Menu,

    /** The camera card. */
    Camera,

    /** Shrinking onto the chip a capture was staged as, then gone. */
    Landing,
}

/** The camera card's share of the screen's height, and its height before a screen is measured. */
internal const val CardHeightFraction = 0.58f

/**
 * The card's frame in a screen [width] by [height] pixels: [heightPx] tall (see [cardHeight]),
 * standing [bottomInsetPx] above the screen's bottom and [insetPx] in from its sides.
 * Port of iOS `AttachOverlayLayout.cardFrame`.
 */
internal fun cardRect(width: Float, height: Float, insetPx: Float, bottomInsetPx: Float, heightPx: Float): Rect {
    val w = (width - insetPx * 2).coerceAtLeast(0f)
    val h = heightPx.coerceAtMost((height - bottomInsetPx).coerceAtLeast(0f))
    return Rect(insetPx, height - bottomInsetPx - h, insetPx + w, height - bottomInsetPx)
}

/** The card's height on a screen [screenHeightPx] tall: 58% of it, rounded; iOS `AttachCard.height`. */
internal fun cardHeight(screenHeightPx: Float, fallbackPx: Float): Float =
    if (screenHeightPx > 0f) (screenHeightPx * CardHeightFraction).roundToInt().toFloat() else fallbackPx

/** The frames the surface moves between, in screen pixels. Read in layout, so a plain holder. */
private class SurfaceGeometry {
    var plus = Rect.Zero
    var menu = Rect.Zero
    var card = Rect.Zero
    var windowOrigin = Offset.Zero
}

/**
 * The attach surface: one frosted shape that grows out of "+" into the menu, morphs into the
 * camera card and back, and shrinks onto the chip a capture was staged as. Only its frame and
 * corner radius move; the rows and the camera sit at their resting frames inside the clip and
 * cross-fade (the leaving side early, the arriving side late). Port of iOS `AttachSurface`.
 *
 * It lives over the keyboard. A normal popup sits below the IME and a focusable one takes focus
 * from the field, so the surface is in non-focusable windows, which are layered above the IME and
 * leave the field focused: a full-screen window that draws the content and swallows outside taps,
 * and, where cross-window blur is on, a backdrop window under it that tracks the shape and carries
 * the blur. Because they are non-focusable they get no key events; Back is taken through the
 * activity's dispatcher at overlay priority, which runs before the IME's own Back handling, so the
 * keyboard stays. The content window is added once, hidden, when the surface is first composed:
 * adding it on the tap cost the morph its first frames.
 *
 * The camera is mounted, hidden, once the menu has finished opening when access is already
 * granted, so it is streaming by the time the menu opens into it.
 *
 * [onShutter] is the host's cue to stage the shot (its chip is what the card lands on) before
 * [onCaptured] says the file is written.
 *
 * [plus] and [landing] are queried every frame, since "+" slides with the keyboard through layer
 * transforms that `onGloballyPositioned` does not report. [onBack] is Back: the host steps the
 * [phase] back (camera to menu, menu to closed). [onGone] fires once the surface has finished
 * leaving, whichever way it went.
 */
@Composable
fun AttachSurface(
    phase: AttachPhase,
    rows: List<AttachRow>,
    plus: () -> LayoutCoordinates?,
    landing: () -> LayoutCoordinates?,
    onRowClick: (AttachRow) -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit,
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit,
    onCaptured: (Uri) -> Unit,
    onCameraError: (Throwable) -> Unit,
    onGone: () -> Unit,
    onCoverChange: (Boolean) -> Unit = {},
) {
    var windowShown by remember { mutableStateOf(false) }
    LaunchedEffect(phase) { if (phase != AttachPhase.Collapsed) windowShown = true }

    val takesBack = phase == AttachPhase.Menu || phase == AttachPhase.Camera
    val currentBack by rememberUpdatedState(onBack)
    BackHandler(enabled = takesBack) { currentBack() }
    val hostView = LocalView.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DisposableEffect(takesBack, hostView) {
            val dispatcher = hostView.findActivity()?.onBackInvokedDispatcher
            val callback = android.window.OnBackInvokedCallback { currentBack() }
            if (takesBack && dispatcher != null) {
                dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            }
            onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
        }
    }

    val parentContext = rememberCompositionContext()
    val host = remember(hostView) { AttachWindow(hostView) }
    // What the window shows: the open surface, or nothing between opens.
    var session by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    DisposableEffect(host) {
        host.show(parentContext) { session?.invoke() }
        onDispose { host.dismiss() }
    }

    if (!windowShown) return
    val currentGone by rememberUpdatedState(onGone)
    val currentCover by rememberUpdatedState(onCoverChange)
    AttachSurfaceWindow(
        host = host,
        setSession = { session = it },
        phase = phase,
        rows = rows,
        plus = plus,
        landing = landing,
        onRowClick = onRowClick,
        onBack = onBack,
        onDismissRequest = onDismissRequest,
        onShutter = onShutter,
        onCaptured = onCaptured,
        onCameraError = onCameraError,
        onCovering = { currentCover(true) },
        onUncovering = { currentCover(false) },
        onGone = {
            currentCover(false)
            windowShown = false
            currentGone()
        },
    )
}

@Composable
private fun AttachSurfaceWindow(
    host: AttachWindow,
    setSession: ((@Composable () -> Unit)?) -> Unit,
    phase: AttachPhase,
    rows: List<AttachRow>,
    plus: () -> LayoutCoordinates?,
    landing: () -> LayoutCoordinates?,
    onRowClick: (AttachRow) -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit,
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit,
    onCaptured: (Uri) -> Unit,
    onCameraError: (Throwable) -> Unit,
    onCovering: () -> Unit,
    onUncovering: () -> Unit,
    onGone: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val blurEnabled = rememberCrossWindowBlurEnabled(view)
    val currentPhase by rememberUpdatedState(phase)
    val currentPlus by rememberUpdatedState(plus)
    val currentLanding by rememberUpdatedState(landing)
    val currentRows by rememberUpdatedState(rows)
    val currentOnRowClick by rememberUpdatedState(onRowClick)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnShutter by rememberUpdatedState(onShutter)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val currentOnGone by rememberUpdatedState(onGone)
    val currentOnUncovering by rememberUpdatedState(onUncovering)
    val currentOnCovering by rememberUpdatedState(onCovering)

    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter, Rect.VisibilityThreshold) }
    val radius = remember { Animatable(0f) }
    // The whole surface's opacity: it fades out at the end of a landing.
    val fade = remember { Animatable(1f) }
    val geometry = remember { SurfaceGeometry() }
    var ready by remember { mutableStateOf(false) }
    // The camera mounts once the menu has finished opening, so its start-up stays off the morph's
    // frames; it still has the menu's dwell time to open before the card shows it.
    var cameraWarm by remember { mutableStateOf(false) }
    // The last preview frame at the shutter: the card shrinks this onto the chip, not the live feed.
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    // Closing onto "+": the bar's own "+" is back underneath, so the surface stops drawing one.
    var handedBack by remember { mutableStateOf(false) }

    val metrics = attachMetrics()
    val plusBlendPx = with(density) { metrics.plusBlendDistance.toPx() }
    val cornerPx = with(density) { metrics.menuCorner.toPx() }

    DisposableEffect(host) {
        setSession {
            SurfaceContent(
                phase = currentPhase,
                rows = currentRows,
                rect = rect,
                radius = radius,
                fade = fade,
                geometry = geometry,
                ready = ready,
                blur = blurEnabled,
                cameraWarm = cameraWarm,
                handedBack = handedBack,
                frozen = frozen,
                plusBlendPx = plusBlendPx,
                onRowClick = { currentOnRowClick(it) },
                onBack = { currentOnBack() },
                onDismiss = { currentOnDismiss() },
                onShutter = { preview, uri ->
                    frozen = preview
                    currentOnShutter(preview, uri)
                },
                onCaptured = { currentOnCaptured(it) },
                onCameraError = {
                    frozen = null
                    currentOnCameraError(it)
                },
            )
        }
        host.reveal()
        onDispose {
            setSession(null)
            host.conceal()
        }
    }

    LaunchedEffect(host, blurEnabled, metrics) {
        val screen = IntArray(2)
        val menuWidthPx = with(density) { metrics.menuWidth.roundToPx() }
        val fieldPaddingPx = with(density) { metrics.fieldPadding.toPx() }
        val contentHeightPx = with(density) { metrics.barContentHeight.toPx() }
        val overhangPx = with(density) { metrics.fieldTopOverhang.toPx() }
        val leadingPx = with(density) { metrics.menuLeadingInset.toPx() }
        val cardInsetPx = with(density) { metrics.cardInset.toPx() }
        val cardCornerPx = with(density) { metrics.cardCorner.toPx() }
        val chipCornerPx = with(density) { metrics.chipCorner.toPx() }
        val chipSizePx = with(density) { metrics.chipSize.toPx() }
        val cardFallbackPx = with(density) { metrics.cardFallbackHeight.toPx() }
        var initialized = false
        var lastPhase: AttachPhase? = null
        var lastTarget: Rect? = null
        var job: Job? = null
        var fading = false
        var closing = false
        var landingSince = 0L
        var framesShown = 0
        // The shot's shrink runs once, from the shutter: its progress is sprung, and the frame is
        // placed between where it started and an aim that eases after the chip's spot. Restarting
        // a spring each time that spot moves (it rides the keyboard up) would stall the card.
        val shotProgress = Animatable(0f)
        var shotFrom: Rect? = null
        var shotFromRadius = 0f
        var aim = Rect.Zero
        var lastFrameNanos = 0L

        while (true) {
            val frameNanos = withFrameNanos { it }
            val dtNanos = if (lastFrameNanos == 0L) 0L else (frameNanos - lastFrameNanos).coerceIn(0L, 50_000_000L)
            lastFrameNanos = frameNanos
            val plusCoordinates = currentPlus()?.takeIf { it.isAttached } ?: continue
            view.getLocationOnScreen(screen)
            val shift = Offset(screen[0].toFloat(), screen[1].toFloat())
            val plusRect = plusCoordinates.boundsInRoot().translate(shift)
            val menuHeightPx = with(density) { menuHeight(currentRows.size.coerceAtLeast(1), metrics.menuRowHeight, metrics.menuVerticalPadding).roundToPx() }
            val menuRect0 = Rect(
                menuOrigin(plusRect, menuHeightPx.toFloat(), fieldPaddingPx, contentHeightPx, overhangPx, leadingPx),
                Size(menuWidthPx.toFloat(), menuHeightPx.toFloat()),
            )
            val origin = host.contentOrigin()
            val size = host.contentSize()
            val screenH = size.height
            val menuRect = menuClampedAbove(
                menuRect0,
                limitBottom = menuLimitBottom(
                    screenBottom = origin.y + screenH - cardInsetPx - host.bottomSystemInsetPx(),
                    composerBottom = plusRect.bottom + fieldPaddingPx,
                    keyboardUp = host.keyboardUp(),
                ),
            )
            val card = cardRect(
                width = size.width,
                height = screenH,
                insetPx = cardInsetPx,
                bottomInsetPx = cardInsetPx + host.bottomSystemInsetPx(),
                heightPx = cardHeight(screenH, cardFallbackPx),
            ).translate(origin)
            geometry.plus = plusRect
            geometry.menu = menuRect
            geometry.card = card
            geometry.windowOrigin = origin

            val now = currentPhase
            val plusRadius = plusRect.height / 2f
            val chip = currentLanding()?.takeIf { it.isAttached }?.boundsInRoot()?.translate(shift)
            // From the shutter on, the card shrinks the taken frame toward the chip. The photo is
            // still being saved, so until its chip lays out it heads for where the chip will be,
            // beside "+" and above it, and retargets onto the chip once there is one.
            val shot = frozen != null && (now == AttachPhase.Camera || now == AttachPhase.Landing)
            val towardChip = chip ?: Rect(
                left = plusRect.center.x,
                top = plusRect.top - chipSizePx,
                right = plusRect.center.x + chipSizePx,
                bottom = plusRect.top,
            )
            val targetRect: Rect
            val targetRadius: Float
            when {
                shot -> { targetRect = towardChip; targetRadius = chipCornerPx }
                now == AttachPhase.Collapsed -> { targetRect = plusRect; targetRadius = plusRadius }
                now == AttachPhase.Menu -> { targetRect = menuRect; targetRadius = cornerPx }
                // Camera, or a landing without a shot frame: hold the card until the chip lays out.
                chip != null && now == AttachPhase.Landing -> { targetRect = chip; targetRadius = chipCornerPx }
                else -> { targetRect = card; targetRadius = cardCornerPx }
            }

            if (!initialized) {
                // Starts as "+" itself, so the surface grows out of it.
                rect.snapTo(plusRect)
                radius.snapTo(plusRadius)
                initialized = true
                ready = true
            }

            // To or from the card the spring is iOS's `attachCard` (0.30s, no bounce): an overshoot
            // on a card half the screen tall would carry past the screen. The menu's is bouncier.
            val cardMotion = now == AttachPhase.Camera || now == AttachPhase.Landing ||
                lastPhase == AttachPhase.Camera
            val damping = if (cardMotion) 1f else 0.69f
            val stiffness = if (cardMotion) 439f else 541f
            val spec = spring(damping, stiffness, Rect.VisibilityThreshold)
            val radiusSpec = spring<Float>(damping, stiffness)
            fun launchMorph() {
                job?.cancel()
                job = launch {
                    launch { radius.animateTo(targetRadius, radiusSpec) }
                    rect.animateTo(targetRect, spec)
                }
            }
            if (shot) {
                if (shotFrom == null) {
                    job?.cancel()
                    shotFrom = rect.value
                    shotFromRadius = radius.value
                    aim = towardChip
                    shotProgress.snapTo(0f)
                    job = launch { shotProgress.animateTo(1f, spring(damping, stiffness, ShotProgressThreshold)) }
                }
                aim = lerp(aim, towardChip, 1f - exp(-dtNanos / ShotAimTauNanos))
                val p = shotProgress.value
                rect.snapTo(lerp(shotFrom!!, aim, p))
                radius.snapTo(lerp(shotFromRadius, chipCornerPx, p))
                lastPhase = now
                lastTarget = targetRect
            } else if (now != lastPhase) {
                shotFrom = null
                lastPhase = now
                lastTarget = targetRect
                launchMorph()
            } else if (targetRect != lastTarget) {
                lastTarget = targetRect
                if (job?.isActive == true || now == AttachPhase.Landing || shot) {
                    launchMorph()
                } else {
                    // Settled and "+" or the field moved with the keyboard: follow it exactly.
                    rect.snapTo(targetRect)
                    radius.snapTo(targetRadius)
                }
            }

            val aimed = shotFrom == null || (
                abs(aim.left - towardChip.left) < 0.5f && abs(aim.top - towardChip.top) < 0.5f &&
                    abs(aim.width - towardChip.width) < 0.5f && abs(aim.height - towardChip.height) < 0.5f
                )
            val settled = job?.isActive != true && aimed
            if (!cameraWarm && ((now == AttachPhase.Menu && settled) || now == AttachPhase.Camera)) {
                cameraWarm = true
            }
            if (now == AttachPhase.Menu && frozen != null) frozen = null
            if (closing && now != AttachPhase.Collapsed) {
                // Reopened while closing: take "+" back.
                closing = false
                handedBack = false
                currentOnCovering()
            }
            if (now == AttachPhase.Collapsed && !closing) {
                // Closing onto "+": give it back to the bar now and stop drawing ours, so the two
                // never stack. The glass thins to nothing as the surface reaches "+", so by the time
                // the window goes it draws nothing there and its removal cannot flicker.
                closing = true
                handedBack = true
                currentOnUncovering()
            }
            if (now == AttachPhase.Collapsed && settled) {
                currentOnGone()
                return@LaunchedEffect
            }
            if (now == AttachPhase.Landing && landingSince == 0L) landingSince = System.nanoTime()
            // A chip that never shows (scrolled away, say) must not hold the surface up.
            val stale = landingSince != 0L && System.nanoTime() - landingSince > 800_000_000L
            if (now == AttachPhase.Landing && settled && (chip != null || stale) && !fading) {
                // On the chip: fade out, so the chip underneath takes over without a pop.
                fading = true
                job = launch {
                    fade.animateTo(0f, tween(120))
                    currentOnGone()
                }
            }

            host.update(
                rect = rect.value,
                cornerPx = radius.value,
                glass = attachGlassAmount(rect.value, plusRect, plusBlendPx),
                blur = blurEnabled,
                touchable = now == AttachPhase.Menu || (now == AttachPhase.Camera && !shot),
                alpha = fade.value,
            )
            // One frame after the surface first drew over "+", the bar's own "+" can hide.
            if (framesShown < 2 && ++framesShown == 2) currentOnCovering()
            if (fading && fade.value == 0f) return@LaunchedEffect
        }
    }
}

@Composable
private fun SurfaceContent(
    phase: AttachPhase,
    rows: List<AttachRow>,
    rect: Animatable<Rect, *>,
    radius: Animatable<Float, *>,
    fade: Animatable<Float, *>,
    geometry: SurfaceGeometry,
    ready: Boolean,
    blur: Boolean,
    cameraWarm: Boolean,
    frozen: ImageBitmap?,
    handedBack: Boolean,
    plusBlendPx: Float,
    onRowClick: (AttachRow) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit,
    onCaptured: (Uri) -> Unit,
    onCameraError: (Throwable) -> Unit,
) {
    if (!ready) return
    val density = LocalDensity.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val menuShown = phase == AttachPhase.Menu
    val cardShown = phase == AttachPhase.Camera || phase == AttachPhase.Landing
    val plusAlpha by layerAlpha(!appeared || phase == AttachPhase.Collapsed)
    val rowsAlpha by layerAlpha(menuShown)
    val cardAlpha by layerAlpha(cardShown)
    val rowsScale by animateFloatAsState(
        if (menuShown) 1f else 0.85f,
        tween(270, easing = LinearOutSlowInEasing),
        label = "rowsScale",
    )
    // Mounted only when the chat offers it, and then from the first frame, so the camera is
    // already open when the menu morphs into it.
    val hasCamera = AttachRow.Camera in rows

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Outside the surface: close it, and keep the tap off the transcript behind.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val r = rect.value
                    val placeable = measurable.measure(
                        Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (r.left - geometry.windowOrigin.x).roundToInt(),
                            (r.top - geometry.windowOrigin.y).roundToInt(),
                        )
                    }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(CornerSize(radius.value))
                    clip = true
                    alpha = fade.value
                }
                .drawBehind {
                    val glass = attachGlassAmount(rect.value, geometry.plus, plusBlendPx)
                    // Without cross-window blur there is no backdrop window: the tint is drawn here.
                    if (!blur) drawRect(Tint.copy(alpha = SolidTintAlpha * glass))
                    // "+"'s flat fill, which the glass takes over from as the surface grows.
                    if (!handedBack) drawRect(Color.White.copy(alpha = 0.10f * (1f - glass)))
                }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // Each layer is laid out at its resting frame and moved by how far the clip has moved.
            // A layer that is not shown stays composed but takes no touches.
            @Composable
            fun Layer(
                resting: () -> Rect,
                alpha: () -> Float,
                interactive: Boolean = true,
                scale: () -> Float = { 1f },
                content: @Composable () -> Unit,
            ) {
                val r0 = resting()
                Box(
                    modifier = Modifier
                        .requiredSize(with(density) { r0.width.toDp() }, with(density) { r0.height.toDp() })
                        .offset {
                            val r = rect.value
                            val rr = resting()
                            IntOffset((rr.left - r.left).roundToInt(), (rr.top - r.top).roundToInt())
                        }
                        .graphicsLayer {
                            this.alpha = alpha()
                            val s = scale()
                            scaleX = s
                            scaleY = s
                            val rr = resting()
                            val p = geometry.plus
                            transformOrigin = TransformOrigin(
                                ((p.center.x - rr.left) / rr.width).coerceIn(0f, 1f),
                                ((p.center.y - rr.top) / rr.height).coerceIn(0f, 1f),
                            )
                        }
                        .then(if (interactive) Modifier else Modifier.blockTouches()),
                ) { content() }
            }

            if (hasCamera && cameraWarm) {
                // Under the shot, the live preview is skipped rather than drawn and covered: its
                // frames would keep the clip and the GPU busy for as long as the card shrinks.
                val shot = frozen != null
                Layer(resting = { geometry.card }, alpha = { if (shot) 0f else cardAlpha }, interactive = cardShown) {
                    ChatCameraCard(
                        active = cardShown,
                        onBack = onBack,
                        onShutter = onShutter,
                        onCaptured = onCaptured,
                        onError = onCameraError,
                    )
                }
            }
            if (frozen != null && cardShown) {
                // Fills the moving frame rather than a resting one, so the photo itself shrinks.
                Image(
                    bitmap = frozen,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (rowsAlpha > 0.01f || menuShown) {
                Layer(resting = { geometry.menu }, alpha = { rowsAlpha }, interactive = menuShown, scale = { rowsScale }) {
                    AttachMenuRows(rows = rows, onRowClick = onRowClick)
                }
            }
            Layer(resting = { geometry.plus }, alpha = { if (handedBack) 0f else plusAlpha }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = CodeTheme.colors.textMain,
                    )
                }
            }
        }
    }
}

/** Takes every touch aimed at this subtree, before its children see it. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun layerAlpha(shown: Boolean): androidx.compose.runtime.State<Float> = animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    // The arriving side fades in late, the leaving side out early and fast.
    animationSpec = if (shown) tween(165, delayMillis = 135, easing = LinearOutSlowInEasing)
    else tween(105, easing = LinearOutSlowInEasing),
    label = "attachLayer",
)

@Composable
private fun rememberCrossWindowBlurEnabled(view: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val wm = remember(view) { view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    var enabled by remember { mutableStateOf(wm.isCrossWindowBlurEnabled) }
    DisposableEffect(wm) {
        val listener = java.util.function.Consumer<Boolean> { enabled = it }
        wm.addCrossWindowBlurEnabledListener(listener)
        onDispose { wm.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/** The non-focusable windows the surface lives in: the content, and with blur a backdrop. */
private class AttachWindow(private val anchor: View) {
    private val background = GradientDrawable()

    // Under the content: follows the surface's frame, and carries the window blur and the tint.
    private val backdrop = Dialog(anchor.context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setBackgroundDrawable(background)
            setGravity(Gravity.TOP or Gravity.START)
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Not focusable: layered above the IME, and the field keeps focus.
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            setWindowAnimations(0)
        }
    }

    // Full screen, transparent: draws the content and swallows outside taps, so they close the
    // surface without also reaching the transcript (whose tap hides the keyboard).
    private val content = Dialog(anchor.context, android.R.style.Theme_Translucent_NoTitleBar).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.TOP or Gravity.START)
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setTitle("AttachSurface")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            setWindowAnimations(0)
        }
    }
    private var touchable = true
    private var backdropShown = false

    fun show(parent: CompositionContext, body: @Composable () -> Unit) {
        val decor = content.window!!.decorView
        decor.setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())
        decor.setViewTreeViewModelStoreOwner(anchor.findViewTreeViewModelStoreOwner())
        decor.setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())
        content.setContentView(
            ComposeView(anchor.context).apply {
                setParentCompositionContext(parent)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent(body)
            },
        )
        // The backdrop is added first and kept, so it stays under the content: a window added
        // later is stacked on top, and over the rows it dimmed them and let the composer through.
        backdrop.show()
        backdrop.window?.decorView?.visibility = View.INVISIBLE
        content.show()
        conceal()
    }

    /** Shows the content window; the backdrop follows on the first [update] that wants it. */
    fun reveal() {
        content.window?.decorView?.visibility = View.VISIBLE
    }

    /** Hides both windows between opens, keeping the content window added. */
    fun conceal() {
        content.window?.decorView?.visibility = View.INVISIBLE
        setTouchable(false)
        hideBackdrop()
    }

    private fun hideBackdrop() {
        if (!backdropShown) return
        backdrop.window?.decorView?.visibility = View.INVISIBLE
        backdropShown = false
    }

    private fun setTouchable(touchable: Boolean) {
        if (this.touchable == touchable) return
        this.touchable = touchable
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (touchable) content.window?.clearFlags(flag) else content.window?.addFlags(flag)
    }

    /** The content window's top-left on the screen. */
    fun contentOrigin(): Offset {
        val loc = IntArray(2)
        content.window?.decorView?.getLocationOnScreen(loc)
        return Offset(loc[0].toFloat(), loc[1].toFloat())
    }

    /** The content window's size in pixels: the whole screen. */
    fun contentSize(): Size {
        val decor = content.window?.decorView ?: return Size.Zero
        return Size(decor.width.toFloat(), decor.height.toFloat())
    }

    fun keyboardUp(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(anchor) ?: return false
        return insets.isVisible(WindowInsetsCompat.Type.ime())
    }

    /** How far the system bars reach up from the screen's bottom. */
    fun bottomSystemInsetPx(): Float {
        val insets = ViewCompat.getRootWindowInsets(anchor) ?: return 0f
        return insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom.toFloat()
    }

    /**
     * Only with cross-window blur does the backdrop window exist: it follows the surface's frame,
     * which costs a window relayout per frame. Without blur the content window draws the tint, and
     * nothing is relaid out while the surface moves.
     */
    fun update(rect: Rect, cornerPx: Float, glass: Float, blur: Boolean, touchable: Boolean, alpha: Float) {
        setTouchable(touchable)
        val window = backdrop.window ?: return
        if (!blur) {
            hideBackdrop()
            return
        }
        window.decorView.alpha = alpha
        background.cornerRadius = cornerPx
        // "+"'s flat fill is drawn by the content; the backdrop is the glass, fading in with it.
        background.setColor(Tint.copy(alpha = GlassTintAlpha).toArgb())
        background.alpha = (glass.coerceIn(0f, 1f) * 255).roundToInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius((BlurRadiusPx * glass.coerceIn(0f, 1f)).roundToInt())
        }
        val lp = window.attributes
        val x = rect.left.roundToInt()
        val y = rect.top.roundToInt()
        val w = rect.width.roundToInt().coerceAtLeast(1)
        val h = rect.height.roundToInt().coerceAtLeast(1)
        if (lp.x != x || lp.y != y || lp.width != w || lp.height != h) {
            lp.x = x
            lp.y = y
            lp.width = w
            lp.height = h
            window.attributes = lp
        }
        if (!backdropShown) {
            // Placed before it shows, so it never flashes at the wrong spot.
            window.decorView.visibility = View.VISIBLE
            backdropShown = true
        }
    }

    fun dismiss() {
        runCatching { content.dismiss() }
        runCatching { backdrop.dismiss() }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_AttachMenu() {
    AttachMenu(
        rows = attachRows(cashOffered = true, acceptsMedia = true, stagedCount = 0),
        onRowClick = {},
        modifier = Modifier.padding(CodeTheme.dimens.staticGrid.x3),
    )
}

private fun View.findActivity(): android.app.Activity? {
    var c: Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
