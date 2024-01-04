package com.getcode.view.main.invites

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.R
import com.getcode.theme.CodeTheme

@Preview
@Composable
fun InvitesPermission(inviteCount: Int = 0, onButtonClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painterResource(
                    R.drawable.ic_code_invite
                ),
                contentDescription = "",
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .padding(vertical = 50.dp)
            )
            Text(
                text = stringResource(id = R.string.subtitle_inviteCount, inviteCount),
                style = CodeTheme.typography.h1,
                modifier = Modifier.padding(bottom = 15.dp)
            )

            Text(
                text = stringResource(id = R.string.subtitle_youHaveInvitesLeft, inviteCount),
                style = CodeTheme.typography.body1,
                modifier = Modifier.padding(bottom = 15.dp)
            )
        }

        CodeButton(
            onClick = onButtonClick,
            text = stringResource(id = R.string.action_allowContacts),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .padding(bottom = 15.dp)
        )
    }
}