package com.getcode.view.main.invites

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.sheetHeight
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.SheetTitle
import com.getcode.view.components.getPermissionLauncher

@Preview
@Composable
fun InvitesSheet(upPress: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<InvitesSheetViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()

    val onPermissionResult = { isGranted: Boolean ->
        viewModel.onContactsPermissionChanged(isGranted)
    }
    val launcher = getPermissionLauncher(onPermissionResult)
    val permissionCheck = {
        PermissionCheck.requestPermission(
            context = context,
            permission = Manifest.permission.READ_CONTACTS,
            shouldRequest = dataState.isPermissionRequested,
            onPermissionResult = onPermissionResult,
            launcher = launcher
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
            .padding(horizontal = CodeTheme.dimens.inset)
    ) {
        Column {
            val title =
                if (dataState.isContactsPermissionGranted == true) stringResource(
                    id = if (dataState.inviteCount == 1) R.string.subtitle_youHaveInvitesSingular
                        else R.string.subtitle_youHaveInvites,
                    dataState.inviteCount
                )
                else stringResource(id = R.string.title_inviteFriend)

            SheetTitle(
                title = { title },
                onCloseIconClicked = upPress
            )

            when (dataState.isContactsPermissionGranted == true) {
                true -> InvitesContacts(
                    dataState = dataState,
                    onUpdateContactFilter = { viewModel.updateContactFilterString(it) },
                    onClick = { viewModel.inviteContact(it.phoneNumber) },
                    onCustomInputInvite = { viewModel.inviteContactCustomInput(dataState.contactFilterString)}
                )
                false -> InvitesPermission(dataState.inviteCount) {
                    viewModel.onContactsPermissionRequested()
                    permissionCheck()
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.init()
            permissionCheck()
        }
    }
}