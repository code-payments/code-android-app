package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flipcash.core.R as CoreR
import com.flipcash.libs.textformat.ComposerEdit
import com.flipcash.libs.textformat.DraftRanges
import com.flipcash.libs.textformat.InlineFormat
import com.flipcash.libs.textformat.LineFormat
import com.flipcash.libs.textformat.consumedRanges
import com.flipcash.libs.textformat.insertLink
import com.flipcash.libs.textformat.isInlineActive
import com.flipcash.libs.textformat.isLineActive
import com.flipcash.libs.textformat.toggleInline
import com.flipcash.libs.textformat.toggleLine
import com.flipcash.shared.chat.ui.maskableUrlOrNull
import com.flipcash.shared.chat.ui.parseChatText
import com.flipcash.shared.chat.ui.protectedRangesOf
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.FormatAction
import com.getcode.ui.components.chat.ToolbarFormatItem

private val Ranges = DraftRanges { protectedRangesOf(it) }

/**
 * The composer's formatting state: whether the strip is open, whether the link sheet is, and the
 * edits the buttons make to [field]. Every edit goes through the shared helpers in
 * `:libs:text-format`, so the parser is the one judge of what a button may do.
 */
@Stable
internal class ComposerFormatting(private val field: TextFieldState) {
    var stripOpen by mutableStateOf(false)
    var linkSheetOpen by mutableStateOf(false)

    private val text get() = field.text.toString()
    private val from get() = field.selection.min
    private val to get() = field.selection.max

    fun toggle(format: InlineFormat) {
        toggleInline(text, from, to, format, Ranges)?.let(::apply)
    }

    fun toggle(format: LineFormat) = apply(toggleLine(text, from, to, format))

    fun isActive(format: InlineFormat) = isInlineActive(text, from, to, format, Ranges)

    fun isEnabled(format: InlineFormat) =
        field.selection.collapsed || isActive(format) || toggleInline(text, from, to, format, Ranges) != null

    fun isActive(format: LineFormat) = isLineActive(text, from, to, format)

    fun addLink(url: String) = apply(insertLink(text, from, to, url))

    private fun apply(edit: ComposerEdit) {
        field.edit {
            replace(0, length, edit.text)
            selection = TextRange(edit.selectionStart, edit.selectionEnd)
        }
    }

    /** The items the system text toolbar leads with: none until there is a selection to wrap. */
    fun toolbarItems(): List<ToolbarFormatItem> {
        if (field.selection.collapsed) return emptyList()
        fun inline(key: String, label: String, format: InlineFormat) =
            ToolbarFormatItem(key, label, isEnabled(format)) { toggle(format) }
        return listOf(
            inline("fmt_bold", "B", InlineFormat.Bold),
            inline("fmt_italic", "I", InlineFormat.Italic),
            inline("fmt_strike", "S", InlineFormat.Strike),
            inline("fmt_code", "</>", InlineFormat.Code),
            ToolbarFormatItem("fmt_link", "Link", true) { linkSheetOpen = true },
        )
    }
}

@Composable
internal fun rememberComposerFormatting(field: TextFieldState): ComposerFormatting {
    val formatting = remember(field) { ComposerFormatting(field) }
    // A cleared draft is back to one line, where the toggle is gone, so the strip goes with it.
    LaunchedEffect(field.text.isEmpty()) {
        if (field.text.isEmpty()) formatting.stripOpen = false
    }
    return formatting
}

/** Dims the characters the parser consumed, so the markers show but recede. */
@Composable
internal fun rememberMarkerDimming(enabled: Boolean): OutputTransformation? {
    val dim = CodeTheme.colors.textSecondary
    return remember(enabled, dim) {
        if (!enabled) null else OutputTransformation {
            val raw = asCharSequence().toString()
            consumedRanges(raw, parseChatText(raw)).forEach {
                addStyle(SpanStyle(color = dim), it.first, it.last + 1)
            }
        }
    }
}

/** The `Aa` button that opens and closes the strip. */
@Composable
internal fun FormatToggleButton(formatting: ComposerFormatting) {
    val open = formatting.stripOpen
    Box(
        modifier = Modifier
            .testTag("chat_format_toggle")
            .size(com.getcode.ui.components.chat.ChatInputDefaults.AccessorySize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (open) 0.25f else 0.1f), CircleShape)
            .clickable { formatting.stripOpen = !formatting.stripOpen },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain,
        )
    }
}

/** The nine buttons, docked under the composer in the keyboard's colour. */
@Composable
internal fun FormatStrip(formatting: ComposerFormatting, field: TextFieldState) {
    // Reads the field so the buttons follow the selection and the text.
    val context = androidx.compose.ui.platform.LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = field.text to field.selection
    fun inline(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, res: Int, f: InlineFormat) =
        FormatAction(id, icon, context.getString(res), formatting.isActive(f), formatting.isEnabled(f)) { formatting.toggle(f) }
    fun line(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, res: Int, f: LineFormat) =
        FormatAction(id, icon, context.getString(res), formatting.isActive(f), true) { formatting.toggle(f) }
    com.getcode.ui.components.chat.ComposerFormatStrip(
        inline = listOf(
            inline("bold", Icons.Rounded.FormatBold, CoreR.string.description_chatFormatBold, InlineFormat.Bold),
            inline("italic", Icons.Rounded.FormatItalic, CoreR.string.description_chatFormatItalic, InlineFormat.Italic),
            inline("strike", Icons.Rounded.FormatStrikethrough, CoreR.string.description_chatFormatStrike, InlineFormat.Strike),
            inline("code", Icons.Rounded.Code, CoreR.string.description_chatFormatCode, InlineFormat.Code),
            FormatAction(
                id = "link",
                icon = Icons.Rounded.Link,
                description = stringResource(CoreR.string.description_chatFormatLink),
                selected = false,
                enabled = true,
                onClick = { formatting.linkSheetOpen = true },
            ),
        ),
        lines = listOf(
            line("bulleted", Icons.Rounded.FormatListBulleted, CoreR.string.description_chatFormatBulleted, LineFormat.Bullet),
            line("numbered", Icons.Rounded.FormatListNumbered, CoreR.string.description_chatFormatNumbered, LineFormat.Numbered),
            line("quote", Icons.Rounded.FormatQuote, CoreR.string.description_chatFormatQuote, LineFormat.Quote),
            line("codeblock", Icons.Rounded.DataObject, CoreR.string.description_chatFormatCodeBlock, LineFormat.CodeBlock),
        ),
    )
}

/** Asks for the URL, and accepts only exactly one detected link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(CoreR.string.title_chatFormatLink),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(CodeTheme.shapes.small)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(14.dp),
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(CoreR.string.hint_chatFormatLink),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it; rejected = false },
                    modifier = Modifier.fillMaxWidth().testTag("chat_format_link_input"),
                    singleLine = true,
                    textStyle = CodeTheme.typography.textMedium.copy(color = CodeTheme.colors.textMain),
                    cursorBrush = SolidColor(CodeTheme.colors.textMain),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            if (rejected) {
                Text(
                    text = stringResource(CoreR.string.error_chatFormatLink),
                    style = CodeTheme.typography.textSmall,
                    color = com.getcode.theme.TextError,
                    modifier = Modifier.testTag("chat_format_link_error"),
                )
            }
            Box(
                modifier = Modifier
                    .testTag("chat_format_link_add")
                    .fillMaxWidth()
                    .clip(CodeTheme.shapes.small)
                    .background(Color.White)
                    .clickable {
                        val url = maskableUrlOrNull(input)
                        if (url == null) rejected = true else onConfirm(url)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CoreR.string.action_chatFormatLinkAdd),
                    style = CodeTheme.typography.textMedium,
                    color = Color.Black,
                )
            }
        }
    }
}
