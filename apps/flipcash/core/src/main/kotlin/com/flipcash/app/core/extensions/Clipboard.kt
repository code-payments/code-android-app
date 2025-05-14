package com.flipcash.app.core.extensions

import android.content.ClipData
import android.content.ClipboardManager

fun ClipboardManager.setText(text: CharSequence, label: String) {
    setPrimaryClip(ClipData.newPlainText(label, text))
}