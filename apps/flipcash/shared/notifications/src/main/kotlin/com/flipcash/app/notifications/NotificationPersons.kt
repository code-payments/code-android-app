package com.flipcash.app.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.flipcash.app.contacts.ContactResolver
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.notifications.R
import androidx.core.graphics.createBitmap

internal fun Bitmap.toCircularBitmap(): Bitmap {
    val size = minOf(width, height)
    val xOffset = (width - size) / 2
    val yOffset = (height - size) / 2

    val output = createBitmap(size, size)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val half = size / 2f
    canvas.drawCircle(half, half, half, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, -xOffset.toFloat(), -yOffset.toFloat(), paint)

    return output
}

internal fun buildSelfPerson(
    context: Context,
    profile: UserProfile?,
    contactResolver: ContactResolver,
): Person {
    val selfPhoto = contactResolver.resolveOwnPhotoBytes(profile?.verifiedPhoneNumber)
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    return Person.Builder()
        .setName(
            profile?.displayName?.takeIf { it.isNotEmpty() }
                ?: context.getString(R.string.notification_self_person_name)
        )
        .setKey("self")
        .apply {
            if (selfPhoto != null) setIcon(IconCompat.createWithBitmap(selfPhoto.toCircularBitmap()))
        }
        .build()
}
