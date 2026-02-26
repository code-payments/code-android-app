package com.getcode.ui.core

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class MaskWithSpacesTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val maskedText = text.text.map { char ->
            if (char == ' ') ' ' else '•'
        }.joinToString("")

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset
        }

        return TransformedText(AnnotatedString(maskedText), offsetMapping)
    }
}