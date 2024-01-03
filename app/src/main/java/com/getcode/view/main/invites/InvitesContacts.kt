package com.getcode.view.main.invites

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.inputColors
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.getButtonBorder
import com.getcode.view.components.getButtonColors

@Preview
@Composable
fun InvitesContacts(
    dataState: InvitesSheetUiModel = InvitesSheetUiModel(),
    onClick: (ContactModel) -> Unit = {},
    onUpdateContactFilter: (String) -> Unit = {},
    onCustomInputInvite: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (dataState.contactsLoading) {
            Text(
                modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(top = 100.dp, bottom = 30.dp),
                text = stringResource(id = R.string.subtitle_organizingContacts),
                style = CodeTheme.typography.body1.copy(textAlign = TextAlign.Center),
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(vertical = 20.dp)
            )
        } else {
            TextField(
                placeholder = { Text(stringResource(R.string.subtitle_searchForContacts)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                value = dataState.contactFilterString,
                onValueChange = {
                    onUpdateContactFilter(it)
                },
                singleLine = true,
                colors = inputColors()
            )
        }

        if (!dataState.contactsLoading && dataState.contactsFiltered.isEmpty() &&
            dataState.contactFilterString.isNotEmpty() &&
            dataState.contactFilterString.all { char -> char.isDigit() }
        ) {
            Text(
                text = stringResource(R.string.subtitle_phoneNotInContacts),
                color = BrandLight,
                style = CodeTheme.typography.body2
                    .copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(
                    vertical = 14.dp
                )
            )
            CodeButton(
                onClick = { onCustomInputInvite() },
                enabled = true,
                text = "${stringResource(R.string.action_invite)} ${dataState.contactFilterString}",
                buttonState = ButtonState.Filled,
            )
        }

        LazyColumn {
            items(dataState.contactsFiltered) { contact ->
                Box(
                    modifier = Modifier.height(90.dp)
                ) {
                    Divider(
                        color = White10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.BottomCenter)
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 15.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 20.dp)
                        ) {
                            Image(
                                painter = painterResource(
                                    R.drawable.ic_circle_outline
                                ),
                                contentDescription = ""
                            )
                            Text(
                                modifier = Modifier
                                    .align(Alignment.Center),
                                text = contact.initials,
                                style = CodeTheme.typography.body1,
                            )
                        }
                        Column(
                            modifier = Modifier.align(CenterVertically)
                        ) {
                            Text(
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .fillMaxWidth(.6f),
                                text = contact.name,
                                style = CodeTheme.typography.body1,

                                )
                            Text(
                                modifier = Modifier.padding(top = 2.dp),
                                text = contact.phoneNumberFormatted,
                                style = CodeTheme.typography.body2.copy(
                                    color = BrandLight
                                ),
                            )
                        }
                    }

                    if (contact.isRegistered) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 15.dp)
                        ) {
                            Image(
                                modifier = Modifier
                                    .padding(end = 10.dp),
                                painter = painterResource(
                                    R.drawable.ic_check
                                ),
                                contentDescription = ""
                            )
                            Text(
                                text = stringResource(R.string.subtitle_onCode),
                                style = CodeTheme.typography.body1
                            )
                        }
                    } else {
                        val buttonState =
                            if (!contact.isInvited) ButtonState.Filled else ButtonState.Bordered
                        Button(
                            onClick = { onClick(contact) },
                            modifier = Modifier
                                .padding(end = 15.dp)
                                .height(30.dp)
                                .align(Alignment.CenterEnd),
                            shape = RoundedCornerShape(25.dp),
                            colors = getButtonColors(buttonState),
                            border = getButtonBorder(buttonState),
                            elevation = ButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp
                            ),
                            contentPadding = PaddingValues()
                        ) {
                            Text(
                                text = stringResource(
                                    if (!contact.isInvited) {
                                        R.string.action_invite
                                    } else {
                                        R.string.action_remind
                                    }
                                ),
                                style = CodeTheme.typography.button,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}