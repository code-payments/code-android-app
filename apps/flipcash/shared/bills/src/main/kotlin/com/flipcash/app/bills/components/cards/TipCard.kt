package com.flipcash.app.bills.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.bills.components.ScannableCode
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.bills.R
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TipCard(
    payloadData: List<Byte>,
    user: UserProfile,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
) {
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = CodeTheme.dimens.inset),
        contentAlignment = contentAlignment
    ) {
        val mW = this.maxWidth
        val codeSize = remember { mW * 0.65f }

        Column(
            modifier = Modifier
                .background(
                    CodeTheme.colors.tipCardColor,
                    shape = CodeTheme.shapes.xxl,
                )
                .padding(vertical = CodeTheme.dimens.grid.x8, horizontal = CodeTheme.dimens.grid.x7)
                .heightIn(0.dp, 800.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4)
        ) {
            if (payloadData.isNotEmpty()) {
                ScannableCode(
                    modifier = Modifier.size(codeSize),
                    data = payloadData,
                    icon = null,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_tip),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )

                ContactAvatar(
                    modifier = Modifier
                        .padding(start = CodeTheme.dimens.grid.x2, end = CodeTheme.dimens.grid.x1)
                        .size(CodeTheme.dimens.staticGrid.x5)
                        .clip(CircleShape),
                    userProfile = user
                )

                Text(
                    text = user.displayName.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    }
}