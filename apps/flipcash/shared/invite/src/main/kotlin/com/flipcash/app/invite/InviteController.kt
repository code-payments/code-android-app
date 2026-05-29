package com.flipcash.app.invite

import android.graphics.drawable.Drawable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InviteChannel(
    val label: String,
    val icon: Drawable?,
    val type: InviteChannelType,
)

sealed interface InviteChannelType {
    data object Sms : InviteChannelType
    data class WhatsApp(val packageName: String) : InviteChannelType
    data object More : InviteChannelType
}

interface InviteController {
    val channels: StateFlow<List<InviteChannel>>
    fun refresh()
    fun launch(channel: InviteChannel, phoneNumber: String)
}

private object NoOpInviteController : InviteController {
    override val channels: StateFlow<List<InviteChannel>> = MutableStateFlow(emptyList())
    override fun refresh() = Unit
    override fun launch(channel: InviteChannel, phoneNumber: String) = Unit
}

val LocalInviteController: ProvidableCompositionLocal<InviteController> =
    staticCompositionLocalOf { NoOpInviteController }
