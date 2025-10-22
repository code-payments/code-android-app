package com.kik.scan

class Scanner {
    data class ScanResult(
        val type: Int, // 1=Username, 2=Remote, 3=Group
        val username: String?, // For Username type
        val nonce: Int, // For Username type
        val payload: ByteArray?, // For Remote/Group type
        val colourCode: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ScanResult

            if (type != other.type) return false
            if (nonce != other.nonce) return false
            if (colourCode != other.colourCode) return false
            if (username != other.username) return false
            if (payload != null) {
                if (other.payload == null) return false
                if (!payload.contentEquals(other.payload)) return false
            } else if (other.payload != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + nonce
            result = 31 * result + colourCode
            result = 31 * result + (username?.hashCode() ?: 0)
            result = 31 * result + (payload?.contentHashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return """
                    type: $type
                    username: $username
                    nonce: $nonce
                    payload: ${payload?.toList()} (${payload?.size} bytes)
                    color: $colourCode
            """.trimIndent()
        }
    }

    companion object {
        init {
            System.loadLibrary("kikCodes")
            System.loadLibrary("codeScanner")
        }

        @JvmStatic
        external fun scan(matrix: ByteArray, width: Int, height: Int, quality: Int): KikCode?

        @JvmStatic
        external fun encode(data: ByteArray?): ByteArray?

        @JvmStatic
        external fun decode(data: ByteArray?): KikCode?
    }
}
