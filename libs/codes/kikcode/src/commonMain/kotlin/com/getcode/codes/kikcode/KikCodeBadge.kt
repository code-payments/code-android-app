package com.getcode.codes.kikcode

/**
 * The Flipcash badge that sits in a code's middle well, as a single SVG-compatible path.
 *
 * Shared so an exported code is self-contained: [KikCodeSvg] embeds it directly rather than
 * reaching for a platform asset. Transcribed from the Android vector drawable
 * `ic_logo_round_white.xml`, which is the self-contained form -- a filled disc with the glyph
 * knocked out via the even-odd rule. (iOS composes the same figure at runtime by masking a circle
 * with a luminance-to-alpha glyph; both produce a solid disc with a transparent glyph.)
 */
object KikCodeBadge {

    /** Side of the square viewport [PATH_DATA] is authored in. */
    const val VIEWPORT: Double = 62.0

    /** Must be filled with the even-odd rule, or the glyph fills in solid. */
    const val PATH_DATA: String =
        "M61.665,30.832C61.665,47.861 47.861,61.665 30.832,61.665C13.804,61.665 0,47.861 0,30.832C0,13.804 13.804,-0 30.832,-0C47.861,-0 61.665,13.804 61.665,30.832ZM24.843,15L24.811,15C22.154,15 20,17.154 20,19.811C20,22.469 22.154,24.623 24.811,24.623L24.811,24.623L34.434,24.623L34.434,24.623L37.642,24.623L39.245,24.623L39.246,24.623C41.903,24.623 44.057,22.469 44.057,19.812C44.057,17.154 41.903,15 39.246,15L39.245,15L37.642,15L34.434,15L34.434,15L24.843,15ZM34.434,27.188L36.038,27.188L36.038,27.188C38.695,27.188 40.849,29.342 40.849,32C40.849,34.657 38.695,36.811 36.038,36.811L36.038,36.811L34.434,36.811L24.858,36.811L24.811,36.811C22.154,36.811 20,34.657 20,32C20,29.343 22.154,27.188 24.811,27.188L24.811,27.188L24.811,27.188L34.434,27.188ZM29.623,44.189C29.623,41.531 27.469,39.377 24.811,39.377C22.154,39.377 20,41.531 20,44.189C20,46.846 22.154,49 24.811,49C27.469,49 29.623,46.846 29.623,44.189Z"
}
