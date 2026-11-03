package com.flipcash.app.core.share.groupcard

import android.graphics.Bitmap
import com.flipcash.app.core.util.Linkify
import com.flipcash.services.models.chat.ChatId
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GroupCardQrTest {

    private val caseOneId = ChatId(
        byteArrayOf(
            0x52, 0xfb.toByte(), 0xa6.toByte(), 0xa9.toByte(), 0x39, 0x40, 0x89.toByte(), 0x9e.toByte(),
            0xaf.toByte(), 0x16, 0xed.toByte(), 0xdf.toByte(), 0x7c, 0x63, 0x64, 0xb2.toByte(),
        )
    )
    private val caseOneUrl = "https://app.flipcash.com/chat/52fba6a9-3940-899e-af16-eddf7c6364b2"

    @Test
    fun `1 - the card url is the existing invite builder's output`() {
        assertEquals(caseOneUrl, Linkify.groupChatInvite(caseOneId))
    }

    @Test
    fun `2 - any group id gives a lowercase dashed uuid url with no trailing slash`() {
        val pattern = Regex("^https://app\\.flipcash\\.com/chat/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")
        val random = java.util.Random(7)
        repeat(50) {
            val bytes = ByteArray(16).also(random::nextBytes)
            assertTrue(pattern.matches(Linkify.groupChatInvite(ChatId(bytes))!!))
        }
    }

    @Test
    fun `3 - the styled qr rendered with its logo decodes to the url`() {
        val bitmap = StyledQrBitmap.render(QrMatrix.encodeGroupCard(caseOneUrl))
        assertEquals(caseOneUrl, decode(bitmap))
    }

    // 32 random bytes as unpadded base64url: the longest link a card encodes (spec Open 5).
    private val inviteUrl = "https://app.flipcash.com/chat/invite#s=kQ3vR8nYw2pLx7TzA0bC9dEfGhIjKlMnOpQrStUxT9a"

    @Test
    fun `3b - an invite link fits version 6 and the styled qr with its logo decodes to it`() {
        assertEquals(82, inviteUrl.length)
        val matrix = QrMatrix.encodeGroupCard(inviteUrl)
        assertEquals(41, matrix.size)
        assertEquals(inviteUrl, decode(StyledQrBitmap.render(matrix)))
    }

    @Test
    fun `4 - the qr is error correction Q, version 6, 41 modules`() {
        // encodeGroupCard checks level and version itself; this proves it ran and the size is 41.
        val matrix = QrMatrix.encodeGroupCard(caseOneUrl)
        assertEquals(41, matrix.size)
        val code = com.google.zxing.qrcode.encoder.Encoder.encode(
            caseOneUrl,
            com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q,
            mapOf(com.google.zxing.EncodeHintType.QR_VERSION to 6),
        )
        assertEquals(6, code.version.versionNumber)
        assertEquals(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q, code.ecLevel)
    }

    @Test
    fun `6 - an empty title exports as Group Card`() {
        assertEquals("Group Card", name(""))
        assertEquals("Group Card", name(null))
        assertEquals("Group Card", name("  "))
    }

    @Test
    fun `7 - slash and colon are stripped from the filename`() {
        assertEquals("Join AB Test", name("A/B: Test"))
        assertEquals("Join Night Owls", name("Night Owls"))
    }

    private fun name(title: String?) =
        groupCardBaseName(title, joinFormat = { "Join $it" }, fallback = "Group Card")

    @Test
    fun `8 - png is 1024 square with a clear corner and a white logo centre`() {
        val bitmap = StyledQrBitmap.render(QrMatrix.encodeGroupCard(caseOneUrl))
        assertEquals(1024, bitmap.width)
        assertEquals(1024, bitmap.height)
        assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(512, 512))
    }

    @Test
    fun `9 - svg viewBox is modules plus quiet zone`() {
        val svg = StyledQrGeometry.toSvg(QrMatrix.encodeGroupCard(caseOneUrl))
        assertTrue(svg.contains("viewBox=\"0 0 49 49\""))
        assertTrue(svg.contains("fill=\"#2E2E30\""))
        assertFalse(svg.contains("<image"))
    }

    @Test
    fun `12 - a 3 by 1 run rounds only the outer corners of its end modules`() {
        val bits = BooleanArray(41 * 41)
        for (x in 30..32) bits[30 * 41 + x] = true
        val m = QrMatrix(41, bits)
        assertEquals(ModuleCorners(true, false, false, true), StyledQrGeometry.cornersOf(m, 30, 30).let {
            // left end: top-left and bottom-left rounded, right side joins the middle module
            ModuleCorners(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft)
        })
        assertEquals(ModuleCorners(false, false, false, false), StyledQrGeometry.cornersOf(m, 31, 30))
        assertEquals(ModuleCorners(false, true, true, false), StyledQrGeometry.cornersOf(m, 32, 30))
    }

    @Test
    fun `the logo area and finders are not drawn as modules`() {
        val bits = BooleanArray(41 * 41) { true }
        val m = QrMatrix(41, bits)
        assertFalse(StyledQrGeometry.isDrawn(m, 20, 20))
        assertFalse(StyledQrGeometry.isDrawn(m, 3, 3))
        assertFalse(StyledQrGeometry.isDrawn(m, 37, 3))
        assertFalse(StyledQrGeometry.isDrawn(m, 3, 37))
        assertTrue(StyledQrGeometry.isDrawn(m, 37, 37))
        assertNotEquals(0, StyledQrGeometry.build(m).size)
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // Composite onto black like a viewer would the transparent corners, then try the image as
        // is and inverted: light-on-dark codes are the case decoders differ on.
        val flat = IntArray(pixels.size) { i ->
            val p = pixels[i]
            if (p ushr 24 == 0) 0xFF000000.toInt() else p or 0xFF000000.toInt()
        }
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, flat)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: com.google.zxing.NotFoundException) {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
        }
    }
}
