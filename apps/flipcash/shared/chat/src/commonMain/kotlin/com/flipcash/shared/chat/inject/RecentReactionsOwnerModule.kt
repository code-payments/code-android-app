package com.flipcash.shared.chat.inject

import com.flipcash.services.user.UserManager
import com.getcode.libs.emojis.reactions.RecentReactionsOwner
import com.getcode.utils.hexEncodedString
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RecentReactionsOwnerModule {

    @Provides
    fun providesRecentReactionsOwner(userManager: UserManager): RecentReactionsOwner =
        RecentReactionsOwner { userManager.accountId?.hexEncodedString() }
}
