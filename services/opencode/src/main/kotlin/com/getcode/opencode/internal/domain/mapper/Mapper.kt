package com.getcode.opencode.internal.domain.mapper

interface Mapper<F, T> {
    fun map(from: F): T
}

interface SuspendMapper<F, T> {
    suspend fun map(from: F): T
}