package com.flipcash.app.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.chat.ComposerFormatToolbarHost
import com.getcode.ui.components.chat.composerFormatItems

/**
 * SPIKE, debug builds only. A bare BasicTextField wired the same way ChatInput is, for devices
 * where the app's onboarding cannot be passed (a fresh AVD has no account).
 */
class ComposerSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state: TextFieldState = rememberTextFieldState("hello world")
            Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().padding(16.dp)) {
                ComposerFormatToolbarHost {
                    BasicTextField(
                        state = state,
                        modifier = Modifier.fillMaxWidth().composerFormatItems(state),
                    )
                }
            }
        }
    }
}
