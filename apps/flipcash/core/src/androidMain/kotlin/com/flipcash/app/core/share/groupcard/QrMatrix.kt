package com.flipcash.app.core.share.groupcard

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
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
        /** Version 6 is 41 modules: the smallest that holds the 66-byte plain group URL at Q. */
        const val GroupCardVersion = 6
        const val GroupCardModules = 41

        /**
         * [text] as a byte-mode, version-6 symbol: at error correction Q when it fits, otherwise M.
         * An invite link (`/chat/invite#s=…`, 82 bytes) does not fit version 6 at Q, and every
         * larger version puts an alignment pattern under the logo, so it drops to M instead.
         * Throws when [text] does not fit version 6 at M either (ZXing raises a WriterException).
         */
        fun encodeGroupCard(text: String): QrMatrix {
            val code = try {
                encode(text, ErrorCorrectionLevel.Q)
            } catch (_: WriterException) {
                encode(text, ErrorCorrectionLevel.M)
            }
            check(code.version.versionNumber == GroupCardVersion) { "Expected version 6, got ${code.version.versionNumber}" }
            val m = code.matrix
            check(m.width == GroupCardModules && m.height == GroupCardModules) { "Expected 41 modules, got ${m.width}" }
            val bits = BooleanArray(m.width * m.height)
            for (y in 0 until m.height) for (x in 0 until m.width) bits[y * m.width + x] = m[x, y].toInt() == 1
            return QrMatrix(m.width, bits)
        }

        private fun encode(text: String, level: ErrorCorrectionLevel) = Encoder.encode(
            text,
            level,
            mapOf(EncodeHintType.QR_VERSION to GroupCardVersion),
        ).also { code ->
            check(code.ecLevel == level) { "Expected error correction $level, got ${code.ecLevel}" }
        }
    }
}
