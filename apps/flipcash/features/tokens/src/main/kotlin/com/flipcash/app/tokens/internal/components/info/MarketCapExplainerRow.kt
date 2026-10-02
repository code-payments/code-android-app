package com.flipcash.app.tokens.internal.components.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flipcash.app.tokens.internal.explainer.ExplainerRowIconBackground
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

/** Entry point to the explainer: "Row / How price works" (design node 10761:1879). */
@Composable
internal fun MarketCapExplainerRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CodeTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CodeTheme.colors.brandLight, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ExplainerRowIconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier.size(22.dp),
                painter = painterResource(R.drawable.ic_market_cap_curve),
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.title_howMarketCapWorks),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.subtitle_howMarketCapWorksRow),
                style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Medium),
                color = CodeTheme.colors.textSecondary,
            )
        }
        // The shared chevron is 9x16; 12dp tall keeps its proportions.
        Icon(
            modifier = Modifier.height(12.dp).width(6.75.dp),
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}
