package com.getcode.model.chat

sealed interface Title {
    val value: String

    data class Localized(override val value: String) : Title
    data class Domain(override val value: String) : Title

    companion object
}