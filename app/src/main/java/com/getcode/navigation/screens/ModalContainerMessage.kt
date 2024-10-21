package com.getcode.navigation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.services.manager.ModalManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.addIf

fun buildMessageContent(
    message: com.getcode.services.manager.ModalManager.Message,
    onClose: (com.getcode.services.manager.ModalManager.ActionType?) -> Unit
): Screen {
    return ModalContainerMessage(message, onClose)
}

private data class ModalContainerMessage(
    val message: com.getcode.services.manager.ModalManager.Message,
    val onClose: (com.getcode.services.manager.ModalManager.ActionType?) -> Unit,
) : Screen, NamedScreen, ModalRoot {

    @Composable
    override fun Content() {
        ModalContainer(
            modalHeightMetric = ModalHeightMetric.WrapContent,
            closeButtonEnabled = { it is ModalContainerMessage },
            onCloseClicked = {
                onClose(null)
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                message.icon?.let { imageResId ->
                    Box(
                        modifier = Modifier
                            .background(CodeTheme.colors.brandLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.padding(CodeTheme.dimens.grid.x3),
                            painter = painterResource(imageResId),
                            contentDescription = null,
                        )
                    }
                }
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .addIf(message.icon != null) {
                            Modifier.padding(top = CodeTheme.dimens.grid.x2)
                        },
                    text = message.title,
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.onBackground,
                )

                if (message.subtitle.isNotEmpty()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = message.subtitle,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.onBackground,
                    )
                }


                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x2),
                    buttonState = ButtonState.Filled,
                    text = message.positiveText,
                    onClick = {
                        message.onPositive()
                        onClose(com.getcode.services.manager.ModalManager.ActionType.Positive)
                    }
                )

                message.negativeText?.let { negativeText ->
                    if (negativeText.isNotEmpty()) {
                        CodeButton(
                            modifier = Modifier.fillMaxWidth(),
                            buttonState = ButtonState.Filled10,
                            text = negativeText,
                            onClick = {
                                message.onNegative()
                                onClose(com.getcode.services.manager.ModalManager.ActionType.Negative)
                            }
                        )
                    }
                }

                message.tertiaryText?.let { tertiaryText ->
                    if (tertiaryText.isNotEmpty()) {
                        CodeButton(
                            modifier = Modifier.fillMaxWidth(),
                            buttonState = ButtonState.Bordered,
                            text = tertiaryText,
                            onClick = {
                                message.onTertiary()
                                onClose(com.getcode.services.manager.ModalManager.ActionType.Tertiary)
                            }
                        )
                    }
                }
            }
        }

        BackHandler(message.isDismissibleByBackButton) {
            onClose(null)
        }
    }
}