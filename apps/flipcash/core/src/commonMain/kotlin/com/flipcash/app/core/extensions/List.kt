package com.flipcash.app.core.extensions

inline fun <reified R, T> List<T>.filterIsNotInstance(): List<T> = filter { it !is R }