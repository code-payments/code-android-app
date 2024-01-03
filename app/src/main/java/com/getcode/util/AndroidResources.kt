package com.getcode.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.getcode.BuildConfig
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class AndroidResources @Inject constructor(
    @ApplicationContext private val context: Context,
) : ResourceHelper {

    override fun getString(@StringRes resourceId: Int): String {
        return context.getString(resourceId)
    }

    override fun getString(@StringRes resourceId: Int, vararg formatArgs: Any): String {
        return context.getString(resourceId, *formatArgs)
    }

    override fun getRawResource(@RawRes resourceId: Int): String {
        return try {
            context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Timber.e(t = e, "Failed to get raw res for resource id:$resourceId")
            ""
        }
    }

    override fun getQuantityString(
        id: Int,
        quantity: Int,
        vararg formatArgs: Any,
        default: String,
    ): String {
        return try {
            context.resources.getQuantityString(id, quantity, *formatArgs)
        } catch (e: Exception) {
            Timber.e(t = e, "Failed to get quantity string for resource id:$id")
            default
        }
    }

    override fun getDimension(dimenId: Int, default: Float): Float {
        return try {
            context.resources.getDimension(dimenId)
        } catch (e: Exception) {
            Timber.e(t = e, "Failed to get dimension for resource id:$dimenId")
            default
        }
    }

    override fun getDimensionPixelSize(dimenId: Int, default: Int): Int {
        return try {
            context.resources.getDimensionPixelSize(dimenId)
        } catch (e: Exception) {
            Timber.e(t = e, message = "Failed to get dimension for resource id:$dimenId")
            default
        }
    }

    override fun getDir(name: String, mode: Int): File? {
        return context.getDir(name, mode)
    }

    @SuppressLint("DiscouragedApi")
    override fun getIdentifier(name: String, type: ResourceType): Int? {
        return when (type) {
            ResourceType.Drawable -> context.resources.getIdentifier(
                name,
                type.defType,
                BuildConfig.APPLICATION_ID
            )
        }.let { if (it == 0) null else it }
    }
}