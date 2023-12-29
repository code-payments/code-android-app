package com.getcode.view.main.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.BrandLight
import com.getcode.theme.White
import com.getcode.util.getActivity
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun HomeRestricted(
    restrictionType: RestrictionType,
    onLogoutClick: (Activity) -> Unit = {}
) {
    lateinit var titleText: String
    lateinit var subTitleText: String
    lateinit var buttonText: String
    lateinit var buttonAction: () -> Unit

    val activity = LocalContext.current as Activity
    when(restrictionType) {
        RestrictionType.FORCE_UPGRADE -> {
            titleText = stringResource(R.string.title_updateRequired)
            subTitleText = stringResource(R.string.subtitle_updateRequiredDescription)
            buttonText = stringResource(R.string.action_update)
            buttonAction = { onUpdateButtonClick(activity) }
        }
        RestrictionType.TIMELOCK_UNLOCKED -> {
            titleText = stringResource(R.string.error_title_timelockUnlocked)
            subTitleText = stringResource(R.string.error_description_timelockUnlocked)
            buttonText = stringResource(R.string.action_logout)
            buttonAction = { onLogoutClick(activity) }
        }
        RestrictionType.ACCESS_EXPIRED -> {
            titleText = stringResource(R.string.title_accessExpired)
            subTitleText = stringResource(R.string.subtitle_accessExpiredDescription)
            buttonText = stringResource(R.string.action_logout)
            buttonAction = { onLogoutClick(activity) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand)
            .padding(horizontal = 30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp)
                    .padding(bottom = 25.dp),
                color = White,
                text = titleText,
                style = MaterialTheme.typography.subtitle1.copy(textAlign = TextAlign.Center)
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                color = BrandLight,
                text = subTitleText,
                style = MaterialTheme.typography.body1.copy(
                    textAlign = TextAlign.Center,
                    lineHeight = 25.sp
                )
            )
        }
        CodeButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 25.dp),
            text = buttonText,
            buttonState = ButtonState.Filled,
            onClick = { buttonAction() },
        )
    }
}

fun onUpdateButtonClick(activity: Activity) {
    try {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${activity.packageName}")
            )
        )
    } catch (_: ActivityNotFoundException) {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")
            )
        )
    }
}