package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.Firebase
import com.google.firebase.initialize

class FirebaseInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        Firebase.initialize(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
