package com.flipcash.app.currencycreator.internal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

@Composable
internal fun Stepper(modifier: Modifier = Modifier, cost: Fiat) {
    Column(modifier = modifier) {
        StepperItem(
            icon = painterResource(R.drawable.ic_currencycreator_name),
            title = stringResource(R.string.title_currencyCreatorStepName),
            description = stringResource(R.string.subtitle_currencyCreatorStepName),
            weight = 0.6f,
        )
        StepperItem(
            icon = painterResource(R.drawable.ic_currencycreator_icon),
            title = stringResource(R.string.title_currencyCreatorStepIcon),
            description = stringResource(R.string.subtitle_currencyCreatorStepIcon),
            weight = 0.6f,
        )
        StepperItem(
            painterResource(R.drawable.ic_currencycreator_description),
            title = stringResource(R.string.title_currencyCreatorStepDescription),
            description = stringResource(R.string.subtitle_currencyCreatorStepDescription),
            weight = 0.6f,
        )
        StepperItem(
            painterResource(R.drawable.ic_currencycreator_cashbill),
            title = stringResource(R.string.title_currencyCreatorStepDesign),
            description = stringResource(R.string.subtitle_currencyCreatorStepDesign),
            weight = 0.6f,
        )
        StepperItem(
            painterResource(R.drawable.ic_currencycreator_purchase),
            title = stringResource(
                R.string.title_currencyCreatorStepPurchase,
                cost.formatted(
                    rule = Fiat.FormattingRule.Truncated,
                    suffix = stringResource(R.string.subtitle_usdSuffix)
                )
            ),
            description = stringResource(
                R.string.subtitle_currencyCreatorStepPurchase,
                cost.formatted(rule = Fiat.FormattingRule.Truncated)
            ),
            weight = 0.6f,
            showConnector = false
        )
    }
}

/**
 * A single step in the vertical stepper. When [weight] > 0 the item expands
 * proportionally and a gradient connector line fills the space between the
 * icon box and the next item. The last item should pass [weight] = 0.
 */
@Composable
private fun ColumnScope.StepperItem(
    icon: Painter,
    title: String,
    description: String,
    weight: Float,
    showConnector: Boolean = weight > 0f
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