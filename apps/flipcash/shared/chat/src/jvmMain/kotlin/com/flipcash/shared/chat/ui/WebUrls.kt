package com.flipcash.shared.chat.ui

// A copy of android.util.Patterns.WEB_URL as the platform builds it, so link detection reads the same
// candidates on desktop as on Android. LinkDetectionTest runs the shared vectors against both.
private const val UCS = "[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\uD800\uDC00-\uD83F\uDFFD\uD840\uDC00-\uD87F\uDFFD\uD880\uDC00-\uD8BF\uDFFD\uD8C0\uDC00-\uD8FF\uDFFD\uD900\uDC00-\uD93F\uDFFD\uD940\uDC00-\uD97F\uDFFD\uD980\uDC00-\uD9BF\uDFFD\uD9C0\uDC00-\uD9FF\uDFFD\uDA00\uDC00-\uDA3F\uDFFD\uDA40\uDC00-\uDA7F\uDFFD\uDA80\uDC00-\uDABF\uDFFD\uDAC0\uDC00-\uDAFF\uDFFD\uDB00\uDC00-\uDB3F\uDFFD\uDB44\uDC00-\uDB7F\uDFFD&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]"

private val WEB_URL = Regex(
    "(((?:(?i:http|https|rtsp|ftp)://(?:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:" +
    "\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a" +
    "-fA-F0-9]{2})){1,25})?\\@)?)?(?:(([a-zA-Z0-9${UCS}](?:[a-zA-Z0-9${UCS}_\\-]{0,61}[a-zA-Z0-9${UCS}]){" +
    "0,1}\\.)+(xn\\-\\-[\\w\\-]{0,58}\\w|[a-zA-Z${UCS}]{2,63})|((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][" +
    "0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-" +
    "9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))))(?:\\:\\d{1,5})?)" +
    "([/\\?](?:(?:[a-zA-Z0-9${UCS};/\\?:@&=#~\\-\\.\\+!\\*'\\(\\),_\\\$])|(?:%[a-fA-F0-9]{2}))*)?(?:\\b|" +
    "\$|^))"
)

internal actual fun findWebUrls(text: String): List<IntRange> =
    WEB_URL.findAll(text).map { it.range }.filter { !it.isEmpty() }.toList()
