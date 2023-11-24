package com.getcode.view.main.getKin

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun ReferFriend(upPress: () -> Unit = {}, navController: NavController) {
    val context = LocalContext.current as Activity

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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
                .padding(bottom=80.dp),
                verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.title_getFriendStartedOnCode),
                style = MaterialTheme.typography.h1,
                modifier = Modifier.padding(vertical = 15.dp)
            )
            Text(
                text = stringResource(R.string.subtitle_getFriendStartedOnCode),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        CodeButton(
            modifier = Modifier
                .constrainAs(button) {
                    bottom.linkTo(parent.bottom)
                }
                .padding(bottom = 10.dp),
            onClick = {
                shareDownloadLink(context)
            },
            enabled = true,
            text = App.getInstance().getString(R.string.action_shareDownloadLink),
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
    context.startActivity(shareIntent)
}
