package com.flipcash.services.models.chat

/** The variant carried by a [MessageContent.Widget]. */
sealed interface WidgetContent {
    /** Invites the viewer to open [username]'s profile. */
    data class ShareProfile(val username: String) : WidgetContent

    /** A `WidgetContent.type` this client does not recognise. */
    data object Unsupported : WidgetContent
}
