package com.kik.kikx.kincodes

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class KikCodeContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var encodedKikCode: ByteArray? = null
        set(value) {
            field = value
            invalidate()
        }

    var logo: Drawable?
        get() = renderer.badge
        set(value) {
            renderer.badge = value
            invalidate()
        }

    private val renderer = KikCodeContentRendererImpl()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // The shared geometry already keeps every mark inside its box (the outermost stroke reaches
        // ~0.94 of the radius), so the code fills the square directly. There used to be a 1.03
        // overscan here to undo a 0.93 inset the renderer applied; both are gone.
        val smallSide = min(width, height)
        canvas.translate((width - smallSide) / 2f, (height - smallSide) / 2f)

        val encodedKikCode = encodedKikCode ?: return
        renderer.render(encodedKikCode, smallSide, canvas)
    }
}
