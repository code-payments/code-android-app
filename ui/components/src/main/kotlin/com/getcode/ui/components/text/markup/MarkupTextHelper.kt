package com.getcode.ui.components.text.markup

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

class MarkupTextHelper {

    @Composable
    fun annotate(text: String, markups: List<Markup>): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            markups.forEach { markup ->
                val annotatedText = markup.process(text)
                annotatedText.spanStyles.forEach { spanStyle ->
                    addStyle(
                        style = spanStyle.item,
                        start = spanStyle.start,
                        end = spanStyle.end
                    )
                }
                annotatedText.getStringAnnotations(0, text.length).forEach { annotation ->
                    addStringAnnotation(
                        tag = annotation.tag,
                        annotation = annotation.item,
                        start = annotation.start,
                        end = annotation.end
                    )
                }
            }
        }
    }
}