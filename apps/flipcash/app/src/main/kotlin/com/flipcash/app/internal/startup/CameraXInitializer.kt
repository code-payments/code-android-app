package com.flipcash.app.internal.startup

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.startup.Initializer

class CameraXInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        ProcessCameraProvider.getInstance(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}