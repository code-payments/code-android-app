package com.flipcash.app.core.share.groupcard

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** A square QR module matrix; [get] is true for a dark module. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    init {
        require(bits.size == size * size) { "A ${size}x$size matrix needs ${size * size} bits, got ${bits.size}" }
    }

    /** False outside the matrix, so neighbour checks need no bounds handling. */
    operator fun get(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && bits[y * size + x]

    companion object {
        /** Version 6 is 41 modules: the smallest that holds the 66-byte invite URL at Q. */
        const val GroupCardVersion = 6
        const val GroupCardModules = 41

        /**
         * [text] as a byte-mode, error-correction-Q, version-6 symbol. Throws when it does not fit
         * version 6 (ZXing raises a WriterException), rather than silently moving to a version that
         * puts an alignment pattern under the logo.
         */
        fun encodeGroupCard(text: String): QrMatrix {
            val code = Encoder.encode(
                text,
                ErrorCorrectionLevel.Q,
                mapOf(EncodeHintType.QR_VERSION to GroupCardVersion),
            )
            check(code.ecLevel == ErrorCorrectionLevel.Q) { "Expected error correction Q, got ${code.ecLevel}" }
            check(code.version.versionNumber == GroupCardVersion) { "Expected version 6, got ${code.version.versionNumber}" }
            val m = code.matrix
            check(m.width == GroupCardModules && m.height == GroupCardModules) { "Expected 41 modules, got ${m.width}" }
            val bits = BooleanArray(m.width * m.height)
            for (y in 0 until m.height) for (x in 0 until m.width) bits[y * m.width + x] = m[x, y].toInt() == 1
            return QrMatrix(m.width, bits)
        }
    }
}
