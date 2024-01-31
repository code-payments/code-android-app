package com.getcode.view.main.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import com.getcode.util.getActivity
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConfirmDeleteAccount(
    viewModel: DeleteAccountViewModel
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        Modifier
            .padding(CodeTheme.dimens.grid.x4)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        Text(
            text = stringResource(id = R.string.subtitle_deleteAccountDescription),
            style = CodeTheme.typography.subtitle2
        )
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            placeholder = {
                Text(
                    stringResource(id = R.string.subtitle_typeDelete).format(viewModel.requiredPhrase),
                    style = CodeTheme.typography.caption
                )
            },
            value = viewModel.typedText.collectAsState().value,
            onValueChange = {
                viewModel.onTextUpdated(it)
            },
            textStyle = CodeTheme.typography.caption,
            singleLine = true,
            colors = inputColors(),
            shape = CodeTheme.shapes.extraSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                keyboardController?.hide()
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = context.getString(R.string.prompt_title_deleteAccount),
                        positiveText = context.getString(R.string.action_deleteAccount),
                        negativeText = context.getString(R.string.action_cancel),
                        onPositive = { context.getActivity()?.let { viewModel.onConfirmDelete(it) } },
                        onNegative = { }
                    ))
            },
            text = stringResource(R.string.action_deleteAccount),
            buttonState = ButtonState.Filled,
            enabled = viewModel.isDeletionAllowed()
        )
    }
}