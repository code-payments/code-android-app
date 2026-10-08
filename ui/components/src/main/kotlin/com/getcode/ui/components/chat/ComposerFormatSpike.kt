package com.getcode.ui.components.chat

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextRange
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel

/**
 * SPIKE, debug builds only. Not for merge.
 *
 * Which way the composer adds format items to its text toolbar, chosen by the `mode` string in the
 * `composer_format_spike` SharedPreferences (set with `run-as`), so no rebuild per attempt:
 * - absent / "off": nothing changes.
 * - "api": `Modifier.appendTextContextMenuComponents`, items carry a label and a leadingIcon.
 * - "action_label": own `TextContextMenuProvider` over a platform ActionMode, icon plus label.
 * - "action_icon": same, empty title plus icon and contentDescription.
 */
enum class SpikeMode { Off, Api, ActionLabel, ActionIcon }

private class Fmt(val key: String, val label: String, val icon: Int, val open: String, val close: String)

private val formats = listOf(
    Fmt("fmt_bold", "Bold", com.getcode.ui.components.R.drawable.ic_spike_format_bold, "*", "*"),
    Fmt("fmt_italic", "Italic", com.getcode.ui.components.R.drawable.ic_spike_format_italic, "_", "_"),
    Fmt("fmt_strike", "Strikethrough", com.getcode.ui.components.R.drawable.ic_spike_format_strike, "~", "~"),
    Fmt("fmt_code", "Code", com.getcode.ui.components.R.drawable.ic_spike_format_code, "`", "`"),
    Fmt("fmt_link", "Link", com.getcode.ui.components.R.drawable.ic_spike_format_link, "[", "]()"),
)

private fun Context.spikeMode(): SpikeMode {
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable) return SpikeMode.Off
    val raw = getSharedPreferences("composer_format_spike", Context.MODE_PRIVATE).getString("mode", "off")
    return when (raw) {
        "api" -> SpikeMode.Api
        "action_label" -> SpikeMode.ActionLabel
        "action_icon" -> SpikeMode.ActionIcon
        else -> SpikeMode.Off
    }
}

/** Wraps the selection in one `edit { }`. Link leaves the caret between the parentheses. */
private fun TextFieldState.wrapSelection(f: Fmt) {
    edit {
        val sel = selection
        if (sel.collapsed) return@edit
        val s = sel.min
        val e = sel.max
        replace(e, e, f.close)
        replace(s, s, f.open)
        selection = if (f.key == "fmt_link") {
            TextRange(e + f.open.length + 2)
        } else {
            TextRange(s + f.open.length, e + f.open.length)
        }
    }
}

@Composable
fun Modifier.composerFormatItems(state: TextFieldState): Modifier {
    val mode = LocalContext.current.spikeMode()
    if (mode == SpikeMode.Off) return this
    return this.appendTextContextMenuComponents {
        formats.forEach { f ->
            item(
                key = f.key,
                label = if (mode == SpikeMode.ActionIcon) f.label else f.label,
                leadingIcon = f.icon,
            ) {
                state.wrapSelection(f)
                close()
            }
        }
    }
}

@Composable
fun ComposerFormatToolbarHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = remember { context.spikeMode() }
    if (mode != SpikeMode.ActionLabel && mode != SpikeMode.ActionIcon) {
        content()
        return
    }
    val view = LocalView.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val provider = remember(view, mode) {
        IconActionModeProvider(view, iconOnly = mode == SpikeMode.ActionIcon) { coords }
    }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides provider) {
        Box(Modifier.onGloballyPositioned { coords = it }) { content() }
    }
}

private class IconActionModeProvider(
    private val view: View,
    private val iconOnly: Boolean,
    private val coords: () -> LayoutCoordinates?,
) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val closed = Channel<Unit>(Channel.CONFLATED)
        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                var group = 1
                var order = 1
                dataProvider.data().components.forEach { c ->
                    when (c) {
                        is TextContextMenuItem -> {
                            val fmt = formats.firstOrNull { it.key == c.key }
                            val o = order++
                            val item = menu.add(group, o, o, if (fmt != null && iconOnly) "" else c.label)
                            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                            if (fmt != null) {
                                item.icon = ContextCompat.getDrawable(view.context, fmt.icon)
                                if (Build.VERSION.SDK_INT >= 26) item.contentDescription = fmt.label
                            }
                            item.setOnMenuItemClickListener { with(c) { object : androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession { override fun close() { closed.trySend(Unit) } }.onClick() }; true }
                        }
                        is TextContextMenuSeparator -> group++
                        else -> Unit
                    }
                }
                return menu.size() > 0
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) { closed.trySend(Unit) }
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
