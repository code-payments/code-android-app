package com.flipcash.app.core.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

inline fun <reified R> Flow<*>.filterIsNotInstance(): Flow<R> = filter { it !is R } as Flow<R>