package xyz.flipchat.internal

import com.getcode.libs.emojis.EmojiQueryProvider
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

class EmojiQueryProviderImpl @Inject constructor(
    private val userManager: UserManager,
) : EmojiQueryProvider {

    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    override suspend fun getFrequentEmojis(): List<String> {
        return db.conversationMessageDao().getFrequentEmojis(userManager.userId.orEmpty())
    }
}