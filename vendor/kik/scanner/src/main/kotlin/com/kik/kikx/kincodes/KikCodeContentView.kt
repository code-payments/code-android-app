package com.kik.kikx.kincodes

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

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

        val smallSide = (min(width, height) * 1.03f).roundToInt()
        canvas.translate((width - smallSide) / 2f, (height - smallSide) / 2f)

        val encodedKikCode = encodedKikCode ?: return
        renderer.render(encodedKikCode, smallSide, canvas)
    }

}

