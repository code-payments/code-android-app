package com.flipcash.shared.chat.media

import android.net.Uri
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.Deferred

/** [ChatMediaUploads.stage] for a picked or shared [Uri]. */
fun ChatMediaUploads.stage(chatId: ChatId, uri: Uri, ready: Deferred<Boolean>? = null): String =
    stage(chatId, uri.toString(), ready)
