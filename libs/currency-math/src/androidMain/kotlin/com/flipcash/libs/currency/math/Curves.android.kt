package com.flipcash.libs.currency.math

import android.content.Context
import com.flipcash.libs.currency.math.internal.loader.AndroidTableLoader

suspend fun Curves.initialize(context: Context) = initialize(AndroidTableLoader(context))
