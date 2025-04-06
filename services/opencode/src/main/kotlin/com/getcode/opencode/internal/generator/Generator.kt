package com.getcode.opencode.internal.generator

interface Generator<D,R> {
    fun generate(predicate: D): R
}