package com.flipcash.app.userflags

import androidx.compose.ui.text.input.KeyboardType

sealed interface FieldEditor<T> {
    data class TextInput<T>(val keyboard: KeyboardType, val parse: (String) -> T?) : FieldEditor<T>
    data class SingleSelect<T>(val options: List<Pair<String, T>>) : FieldEditor<T>
    data class MultiSelect<Item>(val options: List<Pair<String, Item>>) : FieldEditor<List<Item>>
}