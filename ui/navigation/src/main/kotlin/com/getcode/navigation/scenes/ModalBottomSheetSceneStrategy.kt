package com.getcode.navigation.scenes

import android.os.Build
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledBottomSheet
import com.composeunstyled.rememberBottomSheetState
import com.getcode.animation.LocalSharedTransitionScope
import com.getcode.navigation.NavMetadataKeys
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStore
import com.getcode.navigation.results.NavigationRetVal
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.navigation.scrim.ScrimOverlay
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.utils.LocalSheetGesturesState
import kotlinx.coroutines.launch

data class BottomSheetProperties(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
)

private val Expanded = SheetDetent("expanded") { containerHeight, _ ->
    containerHeight * 0.925f
}

/** An [OverlayScene] that renders an [entry] within an [UnstyledBottomSheet]. */
internal class ModalBottomSheetScene<T : Any> constructor(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val sheetProperties: BottomSheetProperties,
    private val onBack: () -> Unit,
    override val metadata: Map<String, Any>,
    private val navResultStore: NavResultStore,
    lastNavKey: () -> NavKey?,
) : OverlayScene<T> {

    private val returnNavKey = lastNavKey()

    override val entries: List<NavEntry<T>> = listOf(entry)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModalBottomSheetScene<*>) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()

    override val content: @Composable (() -> Unit) = {
        val navigator = LocalCodeNavigator.current
        key(key) {
            val isNonDismissable =
                (metadata[NavMetadataKeys.IsNonDismissable.key] as? Boolean ?: false)
                        || navigator.sheetDismissDisabled

            val effectiveProperties = if (isNonDismissable) {
                BottomSheetProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            } else {
                sheetProperties
            }

            val handleBackResult = {
                val navResultKey =
                    metadata[NavMetadataKeys.NavResultKey.key] as? NavResultKey<NavigationRetVal<Parcelable>, Parcelable>
                if (navResultKey != null) {
                    returnNavKey?.let { navKey ->
                        navResultStore.deliverOrPersist(
                            navKey,
                            navResultKey,
                            NavResultOrCanceled.Canceled
                        )
                    }
                }
            }

            var allowDismiss by rememberSaveable { mutableStateOf(!isNonDismissable && !navigator.sheetDragDisabled) }

            val isWrapContent =
                metadata[NavMetadataKeys.IsWrapContentSheet.key] as? Boolean ?: false

            val sheetState = rememberBottomSheetState(
                initialDetent = SheetDetent.Hidden,
                detents = listOf(SheetDetent.Hidden, Expanded),
                confirmDetentChange = { detent ->
                    detent != SheetDetent.Hidden || allowDismiss
                },
            )

            // Animate the sheet in on first composition and after
            // same-route dismiss-replace (sheetGeneration increments).
            LaunchedEffect(navigator.sheetGeneration) {
                sheetState.animateTo(Expanded)
            }

            val composeScope = rememberCoroutineScope()

            val dismiss = { hide: Boolean ->
                if (hide && sheetState.currentDetent != SheetDetent.Hidden) {
                    composeScope.launch {
                        sheetState.animateTo(SheetDetent.Hidden)
                    }.invokeOnCompletion {
                        handleBackResult()
                        onBack()
                    }
                } else {
                    handleBackResult()
                    onBack()
                }
            }

            // Observe external dismiss requests (e.g. deeplink replacing current sheet).
            val pendingDismiss = navigator.pendingSheetDismiss
            if (pendingDismiss != null) {
                LaunchedEffect(pendingDismiss) {
                    try {
                        sheetState.animateTo(SheetDetent.Hidden)
                    } finally {
                        Snapshot.withMutableSnapshot {
                            handleBackResult()
                            onBack()
                            navigator.pendingSheetDismiss = null
                            pendingDismiss()
                        }
                    }
                }
            }

            val scrim = LocalScrimController.current

            CompositionLocalProvider(
                LocalSheetGesturesState provides { enabled ->
                    allowDismiss = enabled && !navigator.sheetDragDisabled
                },
                LocalScrimController provides scrim,
            ) {
                BackHandler(enabled = effectiveProperties.dismissOnBackPress) {
                    dismiss(true)
                }

                // Force light status bar icons in the sheet overlay
                val view = LocalView.current
                SideEffect {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.rootView.windowInsetsController?.setSystemBarsAppearance(
                            0, // clear → light (white) icons
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        )
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    // Scrim tracks sheet position
                    val scrimProgress = sheetState.progress(SheetDetent.Hidden, Expanded)
                    val scrimBaseColor = CodeTheme.colors.scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scrimBaseColor.copy(alpha = scrimBaseColor.alpha * scrimProgress))
                            .then(
                                if (effectiveProperties.dismissOnClickOutside) {
                                    Modifier.noRippleClickable { dismiss(true) }
                                } else Modifier
                            ),
                    )

                    UnstyledBottomSheet(
                        state = sheetState,
                        modifier = Modifier.fillMaxSize(),
                        enabled = !navigator.sheetDragDisabled && !scrim.visible,
                    ) {
                        Sheet(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .background(CodeTheme.colors.surface),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!isWrapContent) {
                                            Modifier.fillMaxHeight()
                                        } else Modifier
                                    ),
                            ) {
                                SharedTransitionLayout {
                                    CompositionLocalProvider(
                                        LocalBottomSheetDismissDispatcher provides { dismiss(true) },
                                        LocalSheetNavigator provides navigator,
                                        LocalSharedTransitionScope provides this,
                                    ) {
                                        entry.Content()
                                    }
                                    if (!isWrapContent) {
                                        ScrimOverlay(scrim)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // end key(key)
    }
}

/**
 * A [SceneStrategy] that displays entries that have added [modalBottomSheet] to their [NavEntry.metadata]
 * within a bottom sheet instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
class ModalBottomSheetSceneStrategy<T : Any>(
    private val navResultStore: NavResultStore,
    private val lastNavKey: () -> NavKey?,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>,
    ): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val isSheet = lastEntry.metadata[NavMetadataKeys.IsSheet.key] as? Boolean ?: false
        if (!isSheet) return null

        val overlaidEntries = entries.dropLast(1).let { remainingEntries ->
            if (lastEntry.metadata[NavMetadataKeys.IsSolitarySheet.key] == true) {
                remainingEntries.dropLastWhile {
                    it.metadata.getOrDefault(NavMetadataKeys.IsSheet.key, false) as Boolean
                }
            } else {
                remainingEntries
            }
        }.ifEmpty { return null }

        val sheetProperties =
            lastEntry.metadata[BOTTOM_SHEET_KEY] as? BottomSheetProperties
                ?: BottomSheetProperties()

        @Suppress("UNCHECKED_CAST")
        return ModalBottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1).ifEmpty { return null },
            overlaidEntries = overlaidEntries,
            sheetProperties = sheetProperties,
            entry = lastEntry,
            onBack = onBack,
            metadata = lastEntry.metadata,
            navResultStore = navResultStore,
            lastNavKey = lastNavKey,
        )
    }

    companion object {
        fun modalBottomSheet(
            properties: BottomSheetProperties = BottomSheetProperties(),
        ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to properties)

        internal const val BOTTOM_SHEET_KEY = "bottomsheet"
    }
}

val LocalBottomSheetDismissDispatcher = staticCompositionLocalOf { {} }

/**
 * Provides the outer (root) [CodeNavigator] to inner sheet content.
 * Used to toggle [CodeNavigator.sheetDragDisabled] from within nested navigators.
 */
val LocalSheetNavigator: ProvidableCompositionLocal<CodeNavigator?> =
    staticCompositionLocalOf { null }
