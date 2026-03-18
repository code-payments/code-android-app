package com.getcode.navigation.scenes

import android.os.Parcelable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.getcode.navigation.NavMetadataKeys
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStore
import com.getcode.navigation.results.NavigationRetVal
import com.getcode.theme.CodeTheme
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

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (() -> Unit) = {
        val isNonDismissable =
            (metadata[NavMetadataKeys.IsNonDismissable.key] as? Boolean) ?: false

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

        var sheetState: SheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                // prevent dismissing via gesture if non-dismissable
                !(value == SheetValue.Hidden && isNonDismissable)
            },
        )

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

        // Remove inset padding. Default adds nav bar padding.
        // Remove grab bar for bleed to top edge of sheet
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { dismiss(false) },
            scrimColor = CodeTheme.colors.scrim,
            properties = modalBottomSheetProperties,
            dragHandle = null,
            contentWindowInsets = { WindowInsets() },
            containerColor = CodeTheme.colors.surface,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
            ) {
                CompositionLocalProvider(LocalBottomSheetDismissDispatcher provides { dismiss(true) }) {
                    entry.Content()
                }
            }
        }
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

        val bottomSheetProperties = lastEntry.metadata[BOTTOM_SHEET_KEY] as? ModalBottomSheetProperties
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
