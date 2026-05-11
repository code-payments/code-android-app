package com.flipcash.app.internal.startup

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.startup.Initializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraXInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            ProcessCameraProvider.getInstance(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}