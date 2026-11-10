package com.getcode.ed25519kmp

import com.getcode.crypt.Sha512

/**
 * Pure-Kotlin Ed25519, for targets that can't compile the orlp C sources (Kotlin/JS).
 *
 * The field and group arithmetic is a line-for-line port of TweetNaCl (public domain): 16 limbs
 * of 16 bits held in `Long`, as TweetNaCl holds them in `int64_t`, so no step relies on floating
 * point. The key layout and checks follow orlp, which Android and Apple compile, so a key or
 * signature from any platform behaves the same here:
 * - the 64-byte private key is the clamped SHA-512 expansion of the seed (`a || prefix`);
 * - [sign] takes that key plus the public key, and never recomputes `a * B`;
 * - [verify] rejects a signature whose top three bits are set, as `ed25519_verify` does;
 * - [onCurve] succeeds exactly when orlp's `ge_frombytes_negate_vartime` does.
 *
 * Not constant time: Kotlin/JS emulates `Long`, and the conditional swaps are only as constant as
 * that emulation. Fine for a Node client signing its own messages; not a hardened signer.
 */
internal object Ed25519Reference {

    fun createKeyPair(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "seed must be 32 bytes, was ${seed.size}" }
        val privateKey = Sha512.hash(seed)
        privateKey[0] = (privateKey[0].toInt() and 248).toByte()
        privateKey[31] = (privateKey[31].toInt() and 63 or 64).toByte()

        val p = point()
        scalarBase(p, privateKey.copyOf(32))
        val publicKey = ByteArray(32)
        pack(publicKey, p)
        return KeyPair(publicKey = publicKey, privateKey = privateKey)
    }

    fun sign(message: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        require(publicKey.size == 32) { "publicKey must be 32 bytes, was ${publicKey.size}" }
        require(privateKey.size == 64) { "privateKey must be 64 bytes, was ${privateKey.size}" }

        // r = H(prefix || M) mod L
        val r = Sha512.hash(privateKey.copyOfRange(32, 64) + message)
        reduce(r)

        val signature = ByteArray(64)
        val p = point()
        scalarBase(p, r)
        pack(signature, p)

        // k = H(R || A || M) mod L
        val k = Sha512.hash(signature.copyOf(32) + publicKey + message)
        reduce(k)

        // S = (r + k * a) mod L
        val x = LongArray(64)
        for (i in 0 until 32) x[i] = r[i].u()
        for (i in 0 until 32) for (j in 0 until 32) x[i + j] += k[i].u() * privateKey[j].u()
        val s = ByteArray(32)
        modL(s, x)
        s.copyInto(signature, destinationOffset = 32)
        return signature
    }

    fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        if (signature[63].toInt() and 224 != 0) return false

        val q = point()
        if (!unpackNeg(q, publicKey)) return false

        val h = Sha512.hash(signature.copyOf(32) + publicKey + message)
        reduce(h)

        // R' = h * (-A) + S * B
        val p = point()
        scalarMult(p, q, h)
        scalarBase(q, signature.copyOfRange(32, 64))
        add(p, q)
        val t = ByteArray(32)
        pack(t, p)
        return verify32(t, signature)
    }

    fun onCurve(publicKey: ByteArray): Boolean = publicKey.size == 32 && unpackNeg(point(), publicKey)

    // ── Field arithmetic mod 2^255 - 19 ─────────────────────────────────────────────────────────

    private fun gf(vararg limbs: Long): LongArray = LongArray(16).also { limbs.copyInto(it) }

    private val GF0 = gf()
    private val GF1 = gf(1)
    private val D = gf(
        0x78a3, 0x1359, 0x4dca, 0x75eb, 0xd8ab, 0x4141, 0x0a4d, 0x0070,
        0xe898, 0x7779, 0x4079, 0x8cc7, 0xfe73, 0x2b6f, 0x6cee, 0x5203,
    )
    private val D2 = gf(
        0xf159, 0x26b2, 0x9b94, 0xebd6, 0xb156, 0x8283, 0x149a, 0x00e0,
        0xd130, 0xeef3, 0x80f2, 0x198e, 0xfce7, 0x56df, 0xd9dc, 0x2406,
    )
    private val X = gf(
        0xd51a, 0x8f25, 0x2d60, 0xc956, 0xa7b2, 0x9525, 0xc760, 0x692c,
        0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 0x53fe, 0xcd6e, 0x36d3, 0x2169,
    )
    private val Y = gf(
        0x6658, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666,
        0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666,
    )
    private val I = gf(
        0xa0b0, 0x4a0e, 0x1b27, 0xc4ee, 0xe478, 0xad2f, 0x1806, 0x2f43,
        0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 0xdf0b, 0x4fc1, 0x2480, 0x2b83,
    )

    private fun set25519(r: LongArray, a: LongArray) = a.copyInto(r)

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += 38 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = LongArray(16)
        val t = n.copyOf()
        car25519(t)
        car25519(t)
        car25519(t)
        repeat(2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xff).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
    }

    private fun neq25519(a: LongArray, b: LongArray): Boolean {
        val c = ByteArray(32)
        val d = ByteArray(32)
        pack25519(c, a)
        pack25519(d, b)
        return !verify32(c, d)
    }

    private fun par25519(a: LongArray): Int {
        val d = ByteArray(32)
        pack25519(d, a)
        return d[0].toInt() and 1
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) o[i] = n[2 * i].u() + (n[2 * i + 1].u() shl 8)
        o[15] = o[15] and 0x7fff
    }

    private fun A(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun Z(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun M(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    private fun S(o: LongArray, a: LongArray) = M(o, a, a)

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            S(c, c)
            if (a != 2 && a != 4) M(c, c, i)
        }
        set25519(o, c)
    }

    private fun pow2523(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 250 downTo 0) {
            S(c, c)
            if (a != 1) M(c, c, i)
        }
        set25519(o, c)
    }

    // ── Group arithmetic, extended coordinates (X, Y, Z, T) ─────────────────────────────────────

    private fun point(): Array<LongArray> = Array(4) { LongArray(16) }

    private fun add(p: Array<LongArray>, q: Array<LongArray>) {
        val a = gf(); val b = gf(); val c = gf(); val d = gf(); val t = gf()
        val e = gf(); val f = gf(); val g = gf(); val h = gf()
        Z(a, p[1], p[0]); Z(t, q[1], q[0]); M(a, a, t)
        A(b, p[0], p[1]); A(t, q[0], q[1]); M(b, b, t)
        M(c, p[3], q[3]); M(c, c, D2)
        M(d, p[2], q[2]); A(d, d, d)
        Z(e, b, a); Z(f, d, c); A(g, d, c); A(h, b, a)
        M(p[0], e, f); M(p[1], h, g); M(p[2], g, f); M(p[3], e, h)
    }

    private fun cswap(p: Array<LongArray>, q: Array<LongArray>, b: Int) {
        for (i in 0 until 4) sel25519(p[i], q[i], b)
    }

    private fun pack(r: ByteArray, p: Array<LongArray>) {
        val tx = gf(); val ty = gf(); val zi = gf()
        inv25519(zi, p[2])
        M(tx, p[0], zi)
        M(ty, p[1], zi)
        pack25519(r, ty)
        r[31] = (r[31].toInt() xor (par25519(tx) shl 7)).toByte()
    }

    private fun scalarMult(p: Array<LongArray>, q: Array<LongArray>, s: ByteArray) {
        set25519(p[0], GF0); set25519(p[1], GF1); set25519(p[2], GF1); set25519(p[3], GF0)
        for (i in 255 downTo 0) {
            val b = (s[i / 8].toInt() shr (i and 7)) and 1
            cswap(p, q, b)
            add(q, p)
            add(p, p)
            cswap(p, q, b)
        }
    }

    private fun scalarBase(p: Array<LongArray>, s: ByteArray) {
        val q = point()
        set25519(q[0], X); set25519(q[1], Y); set25519(q[2], GF1); M(q[3], X, Y)
        scalarMult(p, q, s)
    }

    /** Decodes [p] into `-A`, as orlp's `ge_frombytes_negate_vartime`; false if not on the curve. */
    private fun unpackNeg(r: Array<LongArray>, p: ByteArray): Boolean {
        val t = gf(); val chk = gf(); val num = gf(); val den = gf()
        val den2 = gf(); val den4 = gf(); val den6 = gf()
        set25519(r[2], GF1)
        unpack25519(r[1], p)
        S(num, r[1]); M(den, num, D); Z(num, num, r[2]); A(den, r[2], den)
        S(den2, den); S(den4, den2); M(den6, den4, den2); M(t, den6, num); M(t, t, den)
        pow2523(t, t); M(t, t, num); M(t, t, den); M(t, t, den); M(r[0], t, den)
        S(chk, r[0]); M(chk, chk, den)
        if (neq25519(chk, num)) M(r[0], r[0], I)
        S(chk, r[0]); M(chk, chk, den)
        if (neq25519(chk, num)) return false
        if (par25519(r[0]) == (p[31].u() shr 7).toInt()) Z(r[0], GF0, r[0])
        M(r[3], r[0], r[1])
        return true
    }

    // ── Scalars mod L = 2^252 + 27742317777372353535851937790883648493 ──────────────────────────

    private val L = longArrayOf(
        0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
    )

    private fun modL(r: ByteArray, x: LongArray) {
        for (i in 63 downTo 32) {
            var carry = 0L
            var j = i - 32
            while (j < i - 12) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)]
                carry = (x[j] + 128) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0
        }
        var carry = 0L
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j]
            carry = x[j] shr 8
            x[j] = x[j] and 255
        }
        for (j in 0 until 32) x[j] -= carry * L[j]
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[i] = (x[i] and 255).toByte()
        }
    }

    /** Reduces the 64-byte little-endian [r] mod L in place; the result is its first 32 bytes. */
    private fun reduce(r: ByteArray) {
        val x = LongArray(64) { r[it].u() }
        r.fill(0)
        modL(r, x)
    }

    private fun verify32(x: ByteArray, y: ByteArray): Boolean {
        var d = 0
        for (i in 0 until 32) d = d or (x[i].toInt() xor y[i].toInt())
        return d == 0
    }

    private fun Byte.u(): Long = toLong() and 0xff
}
