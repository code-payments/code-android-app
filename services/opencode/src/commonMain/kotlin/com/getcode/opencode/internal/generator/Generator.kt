package com.getcode.opencode.internal.generator

internal interface Generator<D,R> {
    fun generate(predicate: D): R
}