package com.flipcash.services

import android.content.Context
import com.flipcash.services.db.FlipcashDatabase

object FlipcashCore {
    fun initialize(context: Context, entropy: String) {
        if (!FlipcashDatabase.isOpen()) {
            FlipcashDatabase.init(context, entropy)
        }
    }

    fun reset(context: Context) {
        FlipcashDatabase.deleteDb(context)
    }
}