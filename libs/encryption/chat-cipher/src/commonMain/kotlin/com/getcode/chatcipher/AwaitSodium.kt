package com.getcode.chatcipher

import com.ionspin.kotlin.crypto.LibsodiumInitializer

/**
 * Loads libsodium and returns once it is ready. [DefaultChatCipher] and [DefaultInviteCipher]
 * load it themselves on JVM, Android and Native, where the load is synchronous. On JS it is an
 * async import(), so JS callers await this once before their first cipher call.
 */
suspend fun awaitSodium() {
    if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
}
