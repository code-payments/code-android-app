package com.kik.kikx.kincodes

import android.graphics.Canvas

interface KikCodeContentRenderer {

    /**
     * Renders the content of a Kik code, but not the disk containing it, nor the logo at its center.
     */
    fun render(encodedKikCode: ByteArray, size: Int, canvas: Canvas)
}
