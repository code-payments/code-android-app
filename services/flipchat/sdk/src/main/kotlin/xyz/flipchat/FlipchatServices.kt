package xyz.flipchat

import android.content.Context
import com.getcode.services.db.Database
import xyz.flipchat.internal.db.FcAppDatabase
import javax.inject.Inject

class FlipchatServices @Inject constructor() {

    companion object {
        fun openDatabase(context: Context, entropy: String) {
            if (!FcAppDatabase.isOpen()) {
                FcAppDatabase.init(context, entropy)
                Database.register(FcAppDatabase.requireInstance())
            }
        }
    }
}