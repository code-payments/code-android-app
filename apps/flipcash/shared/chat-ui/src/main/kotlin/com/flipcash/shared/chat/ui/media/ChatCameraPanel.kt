package com.flipcash.shared.chat.ui.media

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.TextureView
import android.graphics.Canvas
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Range
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.camera.view.PreviewView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.flipcash.shared.chat.ui.ChatAnimations
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val CAMERA_SHUTTER_TAG = "chat_camera_shutter"
internal const val CAMERA_MORE_TAG = "chat_camera_more"

/** The camera's measurements, from the theme's grid. */
private object CameraDefaults {
    val ShutterSize: Dp @Composable get() = CodeTheme.dimens.staticGrid.x14

    /** The shutter's gap above the card's bottom edge. */
    val ShutterBottom: Dp @Composable get() = CodeTheme.dimens.staticGrid.x5

    /** The back chevron and the "..." button's diameter. */
    val ControlSize: Dp @Composable get() = CodeTheme.dimens.staticGrid.x9

    /** The flash and flip circles, a touch smaller than the chevron and "..." ones. */
    val SecondaryControlSize: Dp @Composable get() = CodeTheme.dimens.staticGrid.x8

    /** The back chevron and the "..." button share the shutter's row: centred on it, this far from the sides. */
    val ControlInset: Dp @Composable get() = CodeTheme.dimens.staticGrid.x3
    val ControlBottom: Dp @Composable get() = ShutterBottom + (ShutterSize - ControlSize) / 2

    val ControlSpacing: Dp @Composable get() = CodeTheme.dimens.staticGrid.x2
}

/** Which way the lens faces. The panel opens on [Back]. */
enum class ChatCameraLens { Back, Front }

/**
 * A camera: a live preview with a white shutter, and a "…" button that unfolds the
 * flash (on/off only, offered only when the lens has a flash) and flip controls. Opens on the back
 * lens with the flash off.
 *
 * A capture is written as a JPEG under `cacheDir/chat_capture/` and handed to [onCaptured] as a
 * file `Uri`; there is no review step, the host stages it. A failed capture goes to [onError].
 * [onShutter] gets the preview's last frame at the tap and the `Uri` the shot will be written to,
 * for a host that animates the shot away and stages it before the file exists; [onCaptured] then
 * gets that same `Uri` once it is written.
 *
 * What the panel shows depends on the camera permission: not yet asked, it asks (once [active]) and
 * shows a spinner; denied, it explains and offers Settings; no camera on the device, "Camera
 * unavailable". With access already granted it starts the camera as soon as it is composed, whether
 * or not it is [active] (on screen), so a host can mount it hidden under a menu and have a live
 * preview by the time the menu is opened into it.
 */
@Composable
fun ChatCameraPanel(
    onCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onError: (Throwable) -> Unit = {},
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val permission = rememberCameraPermission()

    LaunchedEffect(permission.status, active) {
        if (active && hasCamera && permission.status == PermissionResult.NotRequested) permission.launch()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when {
            !hasCamera -> CameraMessage(stringResource(R.string.subtitle_chatCameraUnavailable))

            permission.isGranted -> CameraPreview(active = active, onShutter = onShutter, onCaptured = onCaptured, onError = onError)

            permission.status == PermissionResult.NotRequested -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )

            else -> CameraMessage(stringResource(R.string.subtitle_chatCameraPermissionDenied)) {
                Text(
                    text = stringResource(R.string.action_chatOpenSettings),
                    style = CodeTheme.typography.textMedium,
                    color = Color.Black,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(role = Role.Button) {
                            context.startActivity(IntentUtils.appSettings(context))
                        }
                        .padding(horizontal = CodeTheme.dimens.staticGrid.x4, vertical = CodeTheme.dimens.staticGrid.x2),
                )
            }
        }
    }
}

/**
 * The camera with a back chevron to the attach menu, filling its container. [active] is whether it
 * is on screen: only then does it ask for the camera permission. Photos need no card: the menu
 * launches [rememberChatPhotoPicker] directly.
 */
@Composable
fun ChatCameraCard(
    active: Boolean,
    onBack: () -> Unit,
    onCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ChatCameraPanel(active = active, onCaptured = onCaptured, onError = onError, onShutter = onShutter)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = CameraDefaults.ControlInset, bottom = CameraDefaults.ControlBottom)
                .size(CameraDefaults.ControlSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.description_chatAttachBack),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CameraMessage(
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(CodeTheme.dimens.staticGrid.x5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3),
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.textMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
private fun CameraPreview(
    active: Boolean,
    onShutter: (preview: ImageBitmap?, uri: Uri) -> Unit,
    onCaptured: (Uri) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // COMPATIBLE (a TextureView) rather than PERFORMANCE (a SurfaceView): the preview lives inside a
    // surface that fades, scales and clips to a rounded corner as it morphs, none of which a
    // SurfaceView's separate window honours.
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    // A fixed 30fps floor: left to its default [5, 30] range the Seeker's HAL drops to 10fps in a
    // dim room, which makes the preview stutter and slows auto-exposure, so it takes seconds to
    // brighten after opening.
    val preview = remember { Preview.Builder().setTargetFrameRate(PreviewFrameRate).build() }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    var lens by remember { mutableStateOf(ChatCameraLens.Back) }
    var flashOn by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var canFlip by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val shutterSource = remember { MutableInteractionSource() }
    val shutterPressed by shutterSource.collectIsPressedAsState()

    // Mounted hidden, the panel opens collapsed to "..." every time it comes on screen, as on iOS.
    LaunchedEffect(active) { if (!active) controlsOpen = false }

    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(lens, lifecycleOwner) {
        runCatching {
            val provider = context.awaitCameraProvider()
            canFlip = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            preview.surfaceProvider = previewView.surfaceProvider
            // Only this panel's use cases: unbindAll would also tear down anyone else's.
            provider.unbind(preview, imageCapture)
            camera = provider.bindToLifecycle(lifecycleOwner, lens.selector(), preview, imageCapture)
        }.onFailure(onError)
    }
    // Mounted hidden under a menu, the camera must not outlive the panel: lifecycle binding alone
    // would keep it open for as long as the host stays resumed. A shot still being written keeps it
    // until the write ends: the host staged it at the shutter and may close the panel before then,
    // and unbinding would abort the write.
    val release = remember { CameraRelease { ProcessCameraProvider.getInstance(context).get().unbind(preview, imageCapture) } }
    DisposableEffect(Unit) {
        onDispose { release.panelGone() }
    }
    LaunchedEffect(flashOn, hasFlash) {
        imageCapture.flashMode =
            if (flashOn && hasFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CameraDefaults.ShutterBottom)
                .size(CameraDefaults.ShutterSize)
                .testTag(CAMERA_SHUTTER_TAG)
                .alpha(if (shutterPressed) 0.6f else 1f)
                .clip(CircleShape)
                .border(BorderStroke(CodeTheme.dimens.thickBorder, Color.White), CircleShape)
                .padding(CodeTheme.dimens.staticGrid.x1)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = shutterSource,
                    indication = null,
                    enabled = !capturing,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.action_chatTakePhoto),
                ) {
                    capturing = true
                    release.writing = true
                    val file = captureFile(context)
                    onShutter(previewView.thumbnail()?.asImageBitmap(), Uri.fromFile(file))
                    imageCapture.capture(
                        context = context,
                        file = file,
                        onSaved = { capturing = false; release.written(); onCaptured(it) },
                        onFailed = { capturing = false; release.written(); onError(it) },
                    )
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = CameraDefaults.ControlInset, bottom = CameraDefaults.ControlBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Flash and flip unfold above the ··· as one column, growing from its bottom edge.
            AnimatedVisibility(visible = controlsOpen, enter = StackEnter, exit = StackExit) {
                Column(
                    modifier = Modifier.padding(bottom = CameraDefaults.ControlSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CameraDefaults.ControlSpacing),
                ) {
                    if (hasFlash) {
                        CameraControl(
                            icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            description = stringResource(
                                if (flashOn) R.string.action_chatFlashOff else R.string.action_chatFlashOn,
                            ),
                            onClick = { flashOn = !flashOn },
                            size = CameraDefaults.SecondaryControlSize,
                        )
                    }
                    if (canFlip) {
                        CameraControl(
                            icon = Icons.Filled.FlipCameraAndroid,
                            description = stringResource(
                                if (lens == ChatCameraLens.Back) {
                                    R.string.action_chatUseFrontCamera
                                } else {
                                    R.string.action_chatUseBackCamera
                                },
                            ),
                            size = CameraDefaults.SecondaryControlSize,
                            onClick = {
                                lens = if (lens == ChatCameraLens.Back) ChatCameraLens.Front else ChatCameraLens.Back
                                flashOn = false
                            },
                        )
                    }
                }
            }
            Box {
                CameraControl(
                    icon = if (controlsOpen) Icons.Filled.Close else Icons.Filled.MoreHoriz,
                    description = stringResource(
                        if (controlsOpen) R.string.action_chatHideCameraControls else R.string.action_chatMoreCameraControls,
                    ),
                    onClick = { controlsOpen = !controlsOpen },
                    modifier = Modifier.testTag(CAMERA_MORE_TAG),
                )
                // The ··· carries a dot while folded, the hint that more controls are behind it.
                val dot by animateFloatAsState(if (controlsOpen) 0f else 1f, ChatAnimations.swap, label = "more dot")
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -CodeTheme.dimens.staticGrid.x1 / 2, y = CodeTheme.dimens.staticGrid.x1 / 2)
                        .size(CodeTheme.dimens.staticGrid.x2)
                        .graphicsLayer { alpha = dot.coerceIn(0f, 1f) }
                        .background(ChatMediaBlue, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CameraControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CameraDefaults.ControlSize,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A changed glyph (··· and ✕, flash on and off) swaps in place rather than cutting.
        AnimatedContent(
            targetState = icon,
            transitionSpec = { fadeIn(ChatAnimations.swap) togetherWith fadeOut(ChatAnimations.swap) },
            label = "camera control icon",
        ) { glyph ->
            Icon(imageVector = glyph, contentDescription = description, tint = Color.White)
        }
    }
}

private val StackOrigin = TransformOrigin(0.5f, 1f)
private val StackEnter = scaleIn(
    ChatAnimations.swap,
    initialScale = ChatAnimations.reactionEnterScale,
    transformOrigin = StackOrigin,
) + fadeIn(ChatAnimations.swap)
private val StackExit = scaleOut(
    ChatAnimations.swap,
    targetScale = ChatAnimations.reactionEnterScale,
    transformOrigin = StackOrigin,
) + fadeOut(ChatAnimations.swap)

private fun ChatCameraLens.selector(): CameraSelector = when (this) {
    ChatCameraLens.Back -> CameraSelector.DEFAULT_BACK_CAMERA
    ChatCameraLens.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
}

/**
 * The preview's current frame at a quarter of the view's size, as the user sees it. Only a
 * stand-in that shrinks away, and [PreviewView.getBitmap] reads back the full frame and copies it
 * again, which held the shutter tap's frame for ~80ms on the Seeker.
 */
private fun PreviewView.thumbnail(): Bitmap? {
    val texture = getChildAt(0) as? TextureView ?: return bitmap
    if (width == 0 || height == 0) return null
    val w = (width / ThumbnailScale).coerceAtLeast(1)
    val h = (height / ThumbnailScale).coerceAtLeast(1)
    // Read at the texture's own aspect, a quarter size, so the stream isn't stretched first.
    val frame = texture.getBitmap(
        (texture.width / ThumbnailScale).coerceAtLeast(1),
        (texture.height / ThumbnailScale).coerceAtLeast(1),
    ) ?: return null
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
        Canvas(out).apply {
            // CameraX rotates, scales and offsets the texture view with view properties (its
            // matrix), not its texture transform; apply both at the reduced size, as drawn.
            scale(1f / ThumbnailScale, 1f / ThumbnailScale)
            translate(texture.left.toFloat(), texture.top.toFloat())
            concat(texture.matrix)
            concat(texture.getTransform(null))
            drawBitmap(frame, null, android.graphics.Rect(0, 0, texture.width, texture.height), null)
        }
        frame.recycle()
    }
}

private const val ThumbnailScale = 4

/** Unbinds the camera once the panel is gone and no shot is being written, whichever comes last. */
private class CameraRelease(private val unbind: () -> Unit) {
    var writing = false
    private var gone = false

    fun panelGone() {
        gone = true
        if (!writing) runCatching(unbind)
    }

    fun written() {
        writing = false
        if (gone) runCatching(unbind)
    }
}

private fun captureDir(context: Context) = File(context.cacheDir, "chat_capture")

internal fun captureFile(context: Context): File {
    val dir = captureDir(context).apply { mkdirs() }
    return File(dir, "photo_${UUID.randomUUID()}.jpg")
}

/**
 * Deletes camera files a previous process left behind. A shot's file goes once its photo is
 * encoded or dropped, but only while the process that took it lives: an install or a kill in
 * between strands it. Files this process wrote are left alone, since a chat may still be encoding
 * them, so this only takes ones older than the process.
 */
suspend fun deleteLeftoverCaptures(context: Context) = withContext(Dispatchers.IO) {
    val processStart = System.currentTimeMillis() -
        (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
    captureDir(context).listFiles()
        ?.filter { it.lastModified() < processStart }
        ?.forEach { it.delete() }
}

private fun ImageCapture.capture(
    context: Context,
    file: File,
    onSaved: (Uri) -> Unit,
    onFailed: (Throwable) -> Unit,
) {
    takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // The Uri given at the shutter, so the host can match the two.
                onSaved(Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) = onFailed(exception)
        },
    )
}

private val PreviewFrameRate = Range(30, 30)

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
