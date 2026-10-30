package com.flipcash.services.validators

interface Validator<T> {
    fun isValid(param: T): Boolean
}