package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.Firebase
import com.google.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            Firebase.initialize(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
