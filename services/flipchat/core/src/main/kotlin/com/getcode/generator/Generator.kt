package com.getcode.generator

interface Generator<D,R> {
    fun generate(predicate: D): R
}