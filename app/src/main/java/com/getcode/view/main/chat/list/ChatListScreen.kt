package com.getcode.view.main.chat.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeScaffold

@Composable
fun ChatListScreen(
    dispatch: (ChatListViewModel.Event) -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    text = "Start a New Chat"
                ) {

                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
//            items(conversations.itemCount) { index ->
//                conversations[index]?.let { chat ->
//                    ChatNode(chat = chat) { }
//                }
//            }
        }
    }
}