package com.getcode.view.main.getKin

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton

@Composable
fun ReferFriend() {
    val context = LocalContext.current

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
    ) {
        val (textSection, button) = createRefs()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .constrainAs(textSection) {
                    top.linkTo(parent.top)
                    bottom.linkTo(button.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .padding(bottom= CodeTheme.dimens.grid.x16),
                verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.title_getFriendStartedOnCode),
                style = CodeTheme.typography.h1,
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3)
            )
            Text(
                text = stringResource(R.string.subtitle_getFriendStartedOnCode),
                style = CodeTheme.typography.body1,
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2)
            )
        }

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(button) {
                    bottom.linkTo(parent.bottom)
                }
                .padding(bottom = CodeTheme.dimens.grid.x2),
            onClick = {
                shareDownloadLink(context)
            },
            enabled = true,
            text = stringResource(R.string.action_shareDownloadLink),
            buttonState = ButtonState.Filled,
        )
    }
}

// TODO: download link should be somewhere central
private fun shareDownloadLink(
    context: Context,
) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "https://www.getcode.com/download")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(shareIntent)
}
