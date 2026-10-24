package com.flipcash.app.core.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

/**
 * One entry in a [FlowStepper]. [weight] > 0 makes the row expand and draws a gradient connector to
 * the next item when [showConnector] is true.
 */
data class StepperItem(
    val icon: Painter,
    val title: String,
    val description: String,
    val weight: Float = 0.6f,
    val showConnector: Boolean = weight > 0f,
)

/** Vertical wizard stepper. Content is fully caller-supplied via [items]. */
@Composable
fun FlowStepper(
    items: List<StepperItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        items.forEach { item ->
            StepperRow(
                icon = item.icon,
                title = item.title,
                description = item.description,
                weight = item.weight,
                showConnector = item.showConnector,
            )
        }
    }
}

@Composable
private fun ColumnScope.StepperRow(
    icon: Painter,
    title: String,
    description: String,
    weight: Float,
    showConnector: Boolean,
) {
    val hasConnector = weight > 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasConnector) Modifier.weight(weight) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Column(
            modifier = Modifier.then(if (hasConnector) Modifier.fillMaxHeight() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = CodeTheme.colors.surfaceVariant,
                        shape = CodeTheme.shapes.extraSmall,
                    )
                    .border(
                        width = CodeTheme.dimens.border,
                        color = CodeTheme.colors.divider,
                        shape = CodeTheme.shapes.extraSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(CodeTheme.dimens.thickBorder)
                        .weight(1f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0.05f),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(CodeTheme.dimens.thickBorder)
                        .weight(1f)
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Text(
                text = title,
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = description,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
