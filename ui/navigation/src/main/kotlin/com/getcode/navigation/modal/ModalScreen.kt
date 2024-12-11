package com.getcode.navigation.modal

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.screens.ModalContent

interface ModalScreen : Screen, ModalContent {

    @Composable
    fun ModalContent()

    @Composable
    override fun Content() {
        ModalContent()
    }
}