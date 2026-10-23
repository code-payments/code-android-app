package com.flipcash.services.internal.domain.mapper

internal interface Mapper<F, T> {
    fun map(from: F): T
}