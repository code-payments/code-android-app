package com.getcode.navigation.scenes

import android.os.Build
import android.os.Parcelable
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.getcode.animation.LocalSharedTransitionScope
import com.getcode.navigation.NavMetadataKeys
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStore
import com.getcode.navigation.results.NavigationRetVal
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy.Companion.modalBottomSheet
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.navigation.scrim.ScrimOverlay
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.LocalSheetGesturesState
import kotlinx.coroutines.launch

// Adapted from code courtesy of https://github.com/android/nav3-recipes/pull/67

/** An [OverlayScene] that renders an [entry] within a [ModalBottomSheet]. */
internal class ModalBottomSheetScene<T : Any> @OptIn(ExperimentalMaterial3Api::class) constructor(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
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

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (() -> Unit) = {
        // Scope composition by the scene key so that rememberModalBottomSheetState
        // (which uses rememberSaveable) creates a fresh SheetState when the route changes.
        //
        // NOTE: sheetGeneration is intentionally NOT part of this key. Putting it here
        // causes the key() block to re-key while the old scene is still composing
        // (during the pendingDismiss finally block), which creates a second
        // ModalBottomSheet popup for the same SaveableStateProvider key → crash.
        // Instead, sheetGeneration is used as the LaunchedEffect key for show() so
        // same-route dismiss-replace re-shows the sheet without recreating the scope.
        val navigator = LocalCodeNavigator.current
        key(key) {
            val isNonDismissable =
                (metadata[NavMetadataKeys.IsNonDismissable.key] as? Boolean ?: false)
                        || navigator.sheetDismissDisabled

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

            var allowDismiss by rememberSaveable { mutableStateOf(!navigator.sheetDragDisabled) }
            var confirmHiddenCalled by remember { mutableStateOf(false) }
            var sheetState: SheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { value ->
                    val allowed = value != SheetValue.Hidden || allowDismiss
                    if (value == SheetValue.Hidden && allowed) {
                        confirmHiddenCalled = true
                    }
                    allowed
                }
            )

            // Show the sheet on initial composition AND after same-route
            // dismiss-replace (where sheetGeneration increments but the scene
            // key stays the same, so rememberSaveable restores the Hidden state).
            LaunchedEffect(navigator.sheetGeneration) {
                sheetState.show()
            }

            val composeScope = rememberCoroutineScope()

            val dismiss = { hide: Boolean ->
                if (hide && sheetState.isVisible) {
                    composeScope.launch {
                        sheetState.hide()
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
            // Reading pendingSheetDismiss registers a snapshot read — when navigateTo sets it,
            // Compose guarantees recomposition (no coroutine dispatch ordering ambiguity).
            val pendingDismiss = navigator.pendingSheetDismiss
            if (pendingDismiss != null) {
                LaunchedEffect(pendingDismiss) {
                    try {
                        sheetState.hide()
                    } finally {
                        // Apply all changes atomically so Compose never sees an
                        // intermediate state where the old sheet is removed but
                        // sheetGeneration hasn't incremented yet.  Without this,
                        // the key(key, sheetGeneration) block can recompose and
                        // create a second ModalBottomSheet popup for the same key
                        // before the old one is destroyed → SaveableStateProvider
                        // duplicate-key crash.
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
                // Remove inset padding. Default adds nav bar padding.
                // Remove grab bar for bleed to top edge of sheet
                ModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = {
                        if (!isNonDismissable) {
                            if (navigator.pendingSheetDismiss != null) {
                                // External dismiss in progress (e.g. sheet replacement via
                                // openAsSheet). The pendingDismiss handler's finally block
                                // owns the onBack() call — don't let onDismissRequest also
                                // call it, which would remove the sheet prematurely while
                                // the handler is still running.
                                confirmHiddenCalled = false
                            } else if (confirmHiddenCalled) {
                                confirmHiddenCalled = false
                                dismiss(false)
                            } else {
                                // Spurious Hidden snap from layout anchor recalculation
                                // (trySnapTo bypasses confirmValueChange) — re-show the sheet
                                composeScope.launch { sheetState.show() }
                            }
                        }
                    },
                    sheetGesturesEnabled = !navigator.sheetDragDisabled && !scrim.visible,
                    scrimColor = CodeTheme.colors.scrim,
                    properties = if (isNonDismissable) {
                        ModalBottomSheetProperties(
                            shouldDismissOnBackPress = false,
                            shouldDismissOnClickOutside = false,
                        )
                    } else modalBottomSheetProperties,
                    dragHandle = null,
                    contentWindowInsets = { WindowInsets() },
                    containerColor = CodeTheme.colors.surface,
                ) {
                    // The sheet's popup window defaults to dark (black) status bar icons.
                    // Force light icons so they're visible against the dark scrim.
                    val view = LocalView.current
                    SideEffect {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            view.rootView.windowInsetsController?.setSystemBarsAppearance(
                                0, // clear light status bars flag → light (white) icons
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
                    ) {
                        SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalBottomSheetDismissDispatcher provides { dismiss(true) },
                                LocalSheetNavigator provides navigator,
                                LocalSharedTransitionScope provides this,
                            ) {
                                entry.Content()
                            }
                            ScrimOverlay(scrim)
                        }
                    }
                }
            }
        } // end key(key)
    }
}

/**
 * A [SceneStrategy] that displays entries that have added [modalBottomSheet] to their [NavEntry.metadata]
 * within a [ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
class ModalBottomSheetSceneStrategy<T : Any>(
    private val navResultStore: NavResultStore,
    private val lastNavKey: () -> NavKey?,
) : SceneStrategy<T> {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>,
    ): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val isSheet = lastEntry.metadata[NavMetadataKeys.IsSheet.key] as? Boolean ?: false
        if (!isSheet) return null

        // Keep all entries unless solitary; for inner sheets, retain other inner sheet entries
        val overlaidEntries = entries.dropLast(1).let { remainingEntries ->
            if (lastEntry.metadata[NavMetadataKeys.IsSolitarySheet.key] == true) {
                // Drop all sheet entries for solitary sheets
                remainingEntries.dropLastWhile {
                    it.metadata.getOrDefault(NavMetadataKeys.IsSheet.key, false) as Boolean
                }
            } else {
                // Keep all entries to allow intra-sheet navigation
                remainingEntries
            }
        }.ifEmpty { return null }

        val bottomSheetProperties =
            lastEntry.metadata[BOTTOM_SHEET_KEY] as? ModalBottomSheetProperties
                ?: ModalBottomSheetProperties()

        @Suppress("UNCHECKED_CAST")
        return ModalBottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1).ifEmpty { return null },
            overlaidEntries = overlaidEntries,
            modalBottomSheetProperties = bottomSheetProperties,
            entry = lastEntry,
            onBack = onBack,
            metadata = lastEntry.metadata,
            navResultStore = navResultStore,
            lastNavKey = lastNavKey,
        )
    }

    companion object {
        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [ModalBottomSheet].
         *
         * @param modalBottomSheetProperties properties that should be passed to the containing
         * [ModalBottomSheet].
         */
        @OptIn(ExperimentalMaterial3Api::class)
        fun modalBottomSheet(
            modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
        ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to modalBottomSheetProperties)

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
