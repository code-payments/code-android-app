package com.flipcash.app.devicelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.devicelink.PhoneLinkController
import com.getcode.theme.CodeTheme

/**
 * The phone's "Link a Desktop" screen. [scanner] is the platform camera: it calls its argument with
 * each QR payload it reads. Everything else here is common.
 */
@Composable
fun PhoneLinkScreen(
    controller: PhoneLinkController,
    scanner: @Composable (onScanned: (String) -> Unit) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    when (val s = state) {
        PhoneLinkController.State.Scanning -> Box(modifier.fillMaxSize()) {
            scanner(controller::onScanned)
            Column(Modifier.align(Alignment.TopCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Title("Link a Desktop")
                Body("Scan the code shown on your computer.")
            }
        }
        else -> Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (s) {
                PhoneLinkController.State.Connecting, PhoneLinkController.State.Sending ->
                    CircularProgressIndicator(color = CodeTheme.colors.textMain)
                is PhoneLinkController.State.Confirm -> {
                    Title("Does your computer show this code?")
                    Body("Only confirm if the codes match. Confirming gives the computer full access to this account.")
                    Spacer(Modifier.height(24.dp))
                    Code(s.code)
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = controller::cancel) { Text("They don't match") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = controller::confirm) { Text("Codes match") }
                    }
                }
                PhoneLinkController.State.Linked -> {
                    Title("Desktop linked")
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDone) { Text("Done") }
                }
                is PhoneLinkController.State.Failed -> {
                    Title("Couldn't link")
                    Body(s.reason)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = controller::cancel) { Text("Scan again") }
                }
                PhoneLinkController.State.Scanning -> Unit
            }
        }
    }
}
