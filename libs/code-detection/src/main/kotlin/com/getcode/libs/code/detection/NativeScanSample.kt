package com.getcode.libs.code.detection

/**
 * Coarse timing for a single native code-scan of one camera frame.
 * Carried on a [CodeScanResult] so the winning frame's measurement travels with
 * the decode result — no shared mutable holder, no cross-frame race.
 *
 * @param durationMs wall-clock of the native scan call for this frame.
 * @param hit whether this frame produced a decode (always true when attached to a result).
 * @param width scanned image width in px.
 * @param height scanned image height in px.
 * @param quality scan-quality header value used for the decode.
 */
data class NativeScanSample(
    val durationMs: Long,
    val hit: Boolean,
    val width: Int,
    val height: Int,
    val quality: Int,
)
