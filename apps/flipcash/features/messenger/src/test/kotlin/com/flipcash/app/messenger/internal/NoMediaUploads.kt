package com.flipcash.app.messenger.internal

import com.flipcash.shared.chat.media.ChatMediaUploads
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow

/** A [ChatMediaUploads] with nothing staged, for view model tests that are not about photos. */
internal fun noMediaUploads(): ChatMediaUploads = mockk(relaxed = true) {
    every { allStates } returns emptyFlow()
}
