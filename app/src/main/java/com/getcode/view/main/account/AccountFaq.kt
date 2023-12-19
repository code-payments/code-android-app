package com.getcode.view.main.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.AccountModal
import com.getcode.navigation.LocalCodeNavigator
import com.getcode.theme.White
import com.getcode.theme.sheetHeight
import com.getcode.view.components.MarkdownText
import com.getcode.view.components.SheetTitle

@Preview
@Composable
fun AccountModal.Faq.AccountFaq(
    viewModel: AccountFaqViewModel = hiltViewModel(),
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.stateFlow.collectAsState()
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight),
    ) {
        Column {
            SheetTitle(
                title = name,
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is AccountModal.Faq,
                closeButton = false,
                onBackIconClicked = { navigator.pop() })
            LazyColumn {
                items(dataState.faqItems) { faqResponse ->
                    Text(
                        modifier = Modifier
                            .padding(bottom = 10.dp),
                        text = faqResponse.question,
                        color = White,
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    MarkdownText(
                        modifier = Modifier
                            .padding(bottom = 55.dp),
                        markdown = faqResponse.answer,
                        fontResource = R.font.avenir_next_demi,
                        style = MaterialTheme.typography.body1.copy(
                            lineHeight = 20.sp,
                        ),
                    )
                }
            }
        }
    }
}
