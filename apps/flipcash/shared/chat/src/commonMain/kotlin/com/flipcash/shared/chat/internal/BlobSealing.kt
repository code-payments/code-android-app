package com.flipcash.shared.chat.internal

import com.flipcash.app.blob.ChatMediaSealing

fun BlobSealer.sealing(plaintext: ByteArray) = ChatMediaSealing(chatId) { blobId -> sealWith(blobId, plaintext) }
