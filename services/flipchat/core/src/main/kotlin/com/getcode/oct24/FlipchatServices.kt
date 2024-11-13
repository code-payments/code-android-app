package com.getcode.oct24

import android.content.Context
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.services.db.Database
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