package com.getcode.view.main.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.navigation.screens.DeleteConfirmationScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.TextSection

@Composable
fun DeleteCodeAccount() {
    val navigator = LocalCodeNavigator.current
    Column(Modifier.padding(20.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.ic_delete_bubble),
                    contentDescription = "Delete"
                )
            }
            item {
                TextSection(
                    title = stringResource(id = R.string.deleteAccount_title_willDo),
                    description = stringResource(id = R.string.deleteAccount_description_willDo)
                )
            }
            item {
                TextSection(
                    title = stringResource(id = R.string.deleteAccount_title_wontDo),
                    description = stringResource(id = R.string.deleteAccount_description_wontDo)
                )
            }
            item {
                TextSection(
                    title = stringResource(id = R.string.deleteAccount_title_willHappen),
                    description = stringResource(id = R.string.deleteAccount_description_willHappen)
                )
            }
        }
        CodeButton(
            onClick = { navigator.push(DeleteConfirmationScreen) },
            text = stringResource(R.string.action_continue),
            buttonState = ButtonState.Filled,
        )
    }
}