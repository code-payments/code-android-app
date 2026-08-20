package com.flipcash.app.menu.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.features.menu.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White50
import com.getcode.util.resources.ResourceHelper

/**
 * The "Download As" sheet (node 9278:7126): one card per export format, plus Cancel.
 *
 * Both formats are offered because they're for different things — PNG pastes anywhere, SVG stays
 * sharp when scaled. If SVG export ever stops being possible, the design's fallback is to skip this
 * sheet and share the PNG straight off the Download tile.
 */
internal fun downloadOptions(
    resources: ResourceHelper,
    onSelect: (TipCodeExportFormat) -> Unit,
): List<BottomBarAction> = listOf(
    formatAction(
        title = resources.getString(R.string.label_exportPng),
        subtitle = resources.getString(R.string.subtitle_exportPng),
        iconRes = R.drawable.ic_file_bend,
        testTag = "export_format_png",
        onClick = { onSelect(TipCodeExportFormat.Png) },
    ),
    formatAction(
        title = resources.getString(R.string.label_exportSvg),
        subtitle = resources.getString(R.string.subtitle_exportSvg),
        iconRes = R.drawable.ic_bezier_curve,
        testTag = "export_format_svg",
        onClick = { onSelect(TipCodeExportFormat.Svg) },
    ),
    BottomBarAction(
        text = resources.getString(R.string.action_cancel),
        style = BottomBarManager.BottomBarButtonStyle.Text,
    ),
)

private fun formatAction(
    title: String,
    subtitle: String,
    iconRes: Int,
    testTag: String,
    onClick: () -> Unit,
): BottomBarAction = BottomBarAction(
    // `text` is unused for rendering once `content` is supplied, but it's what the action reports
    // back through onDismiss, so keep it meaningful.
    text = AnnotatedString(title),
    inlineContentMap = emptyMap(),
    testTag = testTag,
    onClick = onClick,
    content = {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = CodeTheme.typography.textMedium,
                color = White,
            )
            Text(
                text = subtitle,
                style = CodeTheme.typography.caption,
                color = White50,
            )
        }
        Icon(
            modifier = Modifier.size(32.dp),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = White,
        )
    },
)
