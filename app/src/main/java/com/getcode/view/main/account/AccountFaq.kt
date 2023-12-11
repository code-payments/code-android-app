package com.getcode.view.main.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.theme.White
import com.getcode.theme.sheetHeight
import com.getcode.view.components.MarkdownText

@Preview
@Composable
fun AccountFaq(
    viewModel: AccountFaqViewModel = hiltViewModel(),
) {
    val dataState by viewModel.stateFlow.collectAsState()

    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Faq
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight),
    ) {
        Column {
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
