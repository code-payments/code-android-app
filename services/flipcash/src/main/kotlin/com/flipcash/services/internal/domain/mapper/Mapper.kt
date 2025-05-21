package com.getcode.opencode.internal.domain.mapper

internal interface Mapper<F, T> {
    fun map(from: F): T
}