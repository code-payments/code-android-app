package com.getcode.services.generator

interface Generator<D,R> {
    fun generate(predicate: D): R
}