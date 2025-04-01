package com.getcode.util

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity

fun Context.getActivity(): FragmentActivity? = when (this) {
    is AppCompatActivity -> this
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}