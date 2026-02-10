package com.flipcash.app.tokens.internal.components.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.ExpandableText
import com.getcode.util.format


@Composable
internal fun TokenDetailsSection(
    modifier: Modifier = Modifier,
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(R.drawable.ic_info_bars),
                contentDescription = null,
            )

            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.subtitle_currencyInfo),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }

        state.token?.createdAt?.let { mintDate ->
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x1),
                text = stringResource(
                    R.string.label_mintDate,
                    mintDate.format("MMMM dd, yyyy")
                ),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
        }

        ExpandableText(
            modifier = Modifier
                .padding(
                    top = if (state.token?.createdAt == null) {
                        CodeTheme.dimens.grid.x1
                    } else {
                        CodeTheme.dimens.grid.x2
                    }
                ),
            text = state.token?.description.orEmpty(),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
            isExpanded = state.descriptionExpanded,
            isExpandable = false,
            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset)
        ) {
            dispatch(TokenInfoViewModel.Event.ExpandDescription(!state.descriptionExpanded))
        }

        state.token?.socialLinks?.let { links ->
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x4),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset)
            ) {
                items(links) { link ->
                    SocialChip(link)
                }
            }
        }

        Divider(
            modifier = Modifier.padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x5
            ),
            color = CodeTheme.colors.dividerVariant,
        )
    }
}

@Composable
@Preview
private fun PreviewTokenDetails() {
    FlipcashPreview(showBackground = true) {
        ExpandableText(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            text = LoremIpsum(words = 400).values.joinToString(" "),
            contentPadding = PaddingValues(horizontal = 16.dp),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
            isExpanded = false,
            isExpandable = false,
            onToggle = { }
        )
    }
}