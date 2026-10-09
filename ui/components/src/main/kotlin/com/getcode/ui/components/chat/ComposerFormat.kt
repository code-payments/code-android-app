package com.getcode.ui.components.chat

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.channels.Channel

/** One button in [ComposerFormatStrip]. */
data class FormatAction(
    val id: String,
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** The colour the on-screen keyboard draws in, which the strip docks to and matches. */
val ComposerFormatStripColor = Color(0xFF1B1B1F)

/**
 * An icon button that can be selected. Selected reads as a filled circle; disabled dims and stops
 * taking taps.
 */
@Composable
fun SelectableIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
            .alpha(if (enabled) 1f else 0.3f)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                this.selected = selected
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The formatting strip docked to the top of the keyboard: [inline] styles, a divider, then [lines]
 * styles. All of them fit without scrolling on a phone; the row scrolls only as a fallback on a
 * narrow window.
 */
@Composable
fun ComposerFormatStrip(
    inline: List<FormatAction>,
    lines: List<FormatAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ComposerFormatStripColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("composer_format_strip"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inline.forEach { a ->
            SelectableIconButton(a.icon, a.description, a.selected, a.enabled, a.onClick, Modifier.testTag("format_${a.id}"))
        }
        Box(
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.2f)),
        )
        lines.forEach { a ->
            SelectableIconButton(a.icon, a.description, a.selected, a.enabled, a.onClick, Modifier.testTag("format_${a.id}"))
        }
    }
}

/** One item the text toolbar shows ahead of the system's own, as a short text label. */
data class ToolbarFormatItem(
    val key: String,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * Puts [items] first in the system text toolbar of everything inside [content]. Icons do not render
 * in that toolbar, so the labels are short text (B, I, S, </>, Link). The items are read each time
 * the toolbar opens, so their enabled state follows the selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposerFormatToolbarHost(
    enabled: Boolean,
    items: () -> List<ToolbarFormatItem>,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val view = LocalView.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentItems = rememberUpdatedState(items)
    val provider = remember(view) {
        FormatActionModeProvider(view, { currentItems.value() }) { coords }
    }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides provider) {
        Box(Modifier.onGloballyPositioned { coords = it }) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class FormatActionModeProvider(
    private val view: View,
    private val items: () -> List<ToolbarFormatItem>,
    private val coords: () -> LayoutCoordinates?,
) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val closed = Channel<Unit>(Channel.CONFLATED)
        val session = object : TextContextMenuSession {
            override fun close() {
                closed.trySend(Unit)
            }
        }
        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                var order = 1
                var group = 1
                items().forEach { f ->
                    val o = order++
                    menu.add(group, o, o, f.label).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        isEnabled = f.enabled
                        setOnMenuItemClickListener { f.onClick(); closed.trySend(Unit); true }
                    }
                }
                group++
                dataProvider.data().components.forEach { c ->
                    when (c) {
                        is TextContextMenuItem -> {
                            val o = order++
                            menu.add(group, o, o, c.label).apply {
                                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                                setOnMenuItemClickListener {
                                    with(c) { session.onClick() }
                                    true
                                }
                            }
                        }
                        is TextContextMenuSeparator -> group++
                        else -> Unit
                    }
                }
                return menu.size() > 0
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {
                closed.trySend(Unit)
            }

            override fun onGetContentRect(mode: ActionMode, v: View?, outRect: Rect) {
                val c = coords()?.takeIf { it.isAttached } ?: return
                val r: ComposeRect = dataProvider.contentBounds(c).translate(c.positionInRoot())
                outRect.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            }
        }
        val mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING) ?: return
        try {
            closed.receive()
        } finally {
            mode.finish()
        }
    }
}
