// kmp-audit: a Hilt module that binds the Android implementations of chat's platform interfaces.
package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.AppForeground
import com.flipcash.shared.chat.ChatNotifications
import com.flipcash.shared.chat.internal.AndroidAppForeground
import com.flipcash.shared.chat.internal.AndroidChatNotifications
import com.flipcash.shared.chat.media.ChatMediaLocation
import com.flipcash.shared.chat.media.CoilSentImageCache
import com.flipcash.shared.chat.media.FilesDirChatMediaLocation
import com.flipcash.shared.chat.media.SentImageCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The bindings that need Android: the files directory, Coil's singleton cache, the process lifecycle and the notification manager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidChatModule {
    @Binds
    internal abstract fun bindChatMediaLocation(
        impl: FilesDirChatMediaLocation
    ): ChatMediaLocation

    @Binds
    internal abstract fun bindSentImageCache(
        impl: CoilSentImageCache
    ): SentImageCache

    @Binds
    internal abstract fun bindAppForeground(
        impl: AndroidAppForeground
    ): AppForeground

    @Binds
    internal abstract fun bindChatNotifications(
        impl: AndroidChatNotifications
    ): ChatNotifications
}
