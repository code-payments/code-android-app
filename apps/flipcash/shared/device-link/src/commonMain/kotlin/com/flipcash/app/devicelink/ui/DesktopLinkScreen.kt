package com.flipcash.app.devicelink.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.app.devicelink.DesktopLinkHost
import com.getcode.theme.CodeTheme
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/** What the desktop shows while linking: the QR code, then the code to compare with the phone. */
@Composable
fun DesktopLinkScreen(state: DesktopLinkHost.State?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            null, is DesktopLinkHost.State.Linked -> CircularProgressIndicator(color = CodeTheme.colors.textMain)
            is DesktopLinkHost.State.Waiting -> {
                Title("Link this computer")
                Body("On your phone, open Settings, tap Link a Desktop, and scan this code.")
                Spacer(Modifier.height(24.dp))
                Image(
                    painter = rememberQrCodePainter(state.invite),
                    contentDescription = "Link code",
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            is DesktopLinkHost.State.Confirm -> {
                Title("Check the code")
                Body("Confirm on your phone that it shows the same code.")
                Spacer(Modifier.height(24.dp))
                Code(state.code)
            }
            is DesktopLinkHost.State.Failed -> {
                Title("Couldn't link")
                Body(state.reason)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
internal fun Title(text: String) {
    Text(text, style = CodeTheme.typography.displaySmall, color = CodeTheme.colors.textMain, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun Body(text: String) {
    Text(text, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textSecondary, textAlign = TextAlign.Center)
}

@Composable
internal fun Code(code: String) {
    Text(
        "${code.take(3)} ${code.drop(3)}",
        style = CodeTheme.typography.displayLarge,
        color = CodeTheme.colors.textMain,
    )
}
