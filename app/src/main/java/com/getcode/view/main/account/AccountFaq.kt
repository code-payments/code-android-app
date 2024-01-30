package com.getcode.view.main.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.view.components.MarkdownText

@Preview
@Composable
fun AccountFaq(
    viewModel: AccountFaqViewModel = viewModel(),
) {
    val dataState by viewModel.stateFlow.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x11)
    ) {
        items(dataState.faqItems) { faqResponse ->
            Column(
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                Text(
                    text = faqResponse.question,
                    color = White,
                    style = CodeTheme.typography.h6.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                MarkdownText(
                    markdown = faqResponse.answer,
                    fontResource = R.font.avenir_next_demi,
                    style = CodeTheme.typography.body1.copy(
                        lineHeight = 20.sp,
                    ),
                )
            }

        }
    }
}
