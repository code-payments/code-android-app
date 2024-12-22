package com.getcode.ui.components.text.markup

import android.net.Uri
import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.net.toUri
import com.getcode.ui.components.R
import kotlin.reflect.KClass

sealed interface Markup {
    @Composable
    fun process(text: String): AnnotatedString

    sealed interface Interactive

    companion object {
        fun create(type: KClass<out Markup>): Markup {
            return when (type) {
                RoomNumber::class -> RoomNumber(0)
                Url::class -> Url("")
                Phone::class -> Phone("")
                else -> throw IllegalArgumentException("Unknown Markup type")
            }
        }
    }

    data class RoomNumber(val number: Long): Markup, Interactive {
        companion object {
            const val TAG: String = "ROOM_NUMBER"
        }

        @Composable
        override fun process(text: String): AnnotatedString {
            val regex = Regex("#\\d+")
            return buildAnnotatedString {
                var lastIndex = 0
                regex.findAll(text).forEach { matchResult ->
                    val start = matchResult.range.first
                    val end = matchResult.range.last + 1

                    append(text.substring(lastIndex, start))

                    val number = matchResult.value.removePrefix("#")
                    pushStringAnnotation(tag = TAG, annotation = number)
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(matchResult.value)
                    }
                    pop()

                    lastIndex = end
                }
                append(text.substring(lastIndex))
            }
        }
    }

    data class Url(val link: String): Markup, Interactive {
        companion object {
            const val TAG: String = "URL"
        }

        @Composable
        override fun process(text: String): AnnotatedString {
            val matcher = Patterns.WEB_URL.matcher(text)
            return buildAnnotatedString {
                var lastIndex = 0
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()

                    append(text.substring(lastIndex, start))

                    val rawUrl = matcher.group()
                    val uri = rawUrl.toUri()
                    val resolvedUrl = if (uri.scheme == null) {
                        Uri.Builder()
                            .scheme("https")
                            .authority(uri.path)
                            .build().toString()
                    } else {
                        rawUrl
                    }

                    pushStringAnnotation(tag = TAG, annotation = resolvedUrl)
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(rawUrl)
                    }
                    pop()

                    lastIndex = end
                }
                append(text.substring(lastIndex))
            }
        }
    }
    data class Phone(val phoneNumber: String): Markup, Interactive {
        companion object {
            const val TAG: String = "PHONE_NUMBER"
        }

        @Composable
        override fun process(text: String): AnnotatedString {
            val regex = Regex("^\\+?[0-9]{1,3}[ \\-.]?\\(?[0-9]{1,4}\\)?[ \\-.]?[0-9]{3,}([ \\-.]?[0-9]{2,})*$")
            return buildAnnotatedString {
                var lastIndex = 0
                regex.findAll(text).forEach { matchResult ->
                    val start = matchResult.range.first
                    val end = matchResult.range.last + 1

                    append(text.substring(lastIndex, start))

                    val phone = matchResult.value
                    pushStringAnnotation(tag = TAG, annotation = phone)
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(phone)
                    }
                    pop()

                    lastIndex = end
                }
                append(text.substring(lastIndex))
            }
        }
    }
}
