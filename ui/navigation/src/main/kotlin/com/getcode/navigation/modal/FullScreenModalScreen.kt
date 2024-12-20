package com.getcode.navigation.modal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.screens.ModalContent
import com.getcode.theme.CodeTheme

sealed interface ModalHeightMetric {
    data class Weight(val weight: Float) : ModalHeightMetric
    data object WrapContent : ModalHeightMetric
}

interface FullScreenModalScreen : Screen, ModalContent {

    @Composable
    fun ModalContent()

    @Composable
    override fun Content() {
        ModalContent()
    }
}

interface ModalScreen : Screen, ModalContent {

    val modalHeight: ModalHeightMetric
        @Composable get() = ModalHeightMetric.Weight(CodeTheme.dimens.modalHeightRatio)

    @Composable
    fun ModalContent()

    @Composable
    override fun Content() {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModalContent()
        }
    }
}