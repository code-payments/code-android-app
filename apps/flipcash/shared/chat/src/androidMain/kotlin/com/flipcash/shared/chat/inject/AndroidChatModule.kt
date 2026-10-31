package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.AppForeground
import com.flipcash.shared.chat.ChatNotifications
import com.flipcash.shared.chat.internal.AndroidAppForeground
import com.flipcash.shared.chat.internal.AndroidChatNotifications
import com.flipcash.shared.chat.internal.delegates.MediaSendDelegate
import com.flipcash.shared.chat.link.LinkPreviewImages
import com.flipcash.shared.chat.link.UploadedLinkPreviewImages
import com.flipcash.shared.chat.media.ChatMediaSending
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The bindings that need Android: photo upload, the process lifecycle and the notification manager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidChatModule {
    @Binds
    @Singleton
    abstract fun bindChatMediaSending(
        impl: MediaSendDelegate
    ): ChatMediaSending

    @Binds
    internal abstract fun bindLinkPreviewImages(
        impl: UploadedLinkPreviewImages
    ): LinkPreviewImages

    @Binds
    internal abstract fun bindAppForeground(
        impl: AndroidAppForeground
    ): AppForeground

    @Binds
    internal abstract fun bindChatNotifications(
        impl: AndroidChatNotifications
    ): ChatNotifications
}
