package com.kik.kikx.kincodes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.experimental.and

class KikCodeContentRendererImpl : KikCodeContentRenderer {

    companion object {
        private const val RING_COUNT = 6
        private const val SCALE_FACTOR = 8

        private const val INNER_RING_RATIO = 0.32f
        private const val FIRST_RING_RATIO = 0.425f
        private const val LAST_RING_RATIO = 0.95f

        private val FINDER_BYTES = byteArrayOf(0xB2.toByte(), 0xCB.toByte(), 0x25.toByte(), 0xC6.toByte())
    }

    var badge: Drawable? = null

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        style = Paint.Style.FILL_AND_STROKE
        setARGB(255, 255, 255, 255)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        setARGB(255, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    override fun render(encodedKikCode: ByteArray, size: Int, canvas: Canvas) {
        val midX = size / 2f
        val midY = size / 2f

        val dataByteArray = FINDER_BYTES + encodedKikCode

        // Offset maxRadius by border
        val maxRadius = (size / 2 * 0.93).toFloat()

        // Calculate all the radii
        val innerRingRadius = maxRadius * INNER_RING_RATIO
        val firstRingRadius = maxRadius * FIRST_RING_RATIO
        val lastRingRadius = maxRadius * LAST_RING_RATIO

        val ringRadius = (lastRingRadius - firstRingRadius) / RING_COUNT
        val dotSize = ringRadius * 3 / 4

        arcPaint.strokeWidth = dotSize
        var bitsRead = 0

        for (i in 0 until RING_COUNT) {
            var currentRingRadius = ringRadius * i + firstRingRadius

            if (i == 0) {
                currentRingRadius -= innerRingRadius / 10
            }

            val bitsPerRing = 32 + SCALE_FACTOR * i

            val anglePerBit = 2 * Math.PI / bitsPerRing

            val bitsReadBeforeCurrentRing = bitsRead
            val currentRadius = currentRingRadius + ringRadius / 2

            var bitsInARow = 0
            var startAngle = 0.0

            for (j in 0 until bitsPerRing) {
                // This is so that the angle will start at the apex of circle
                val angle = j * anglePerBit - Math.PI / 2

                // get correct bit from byte
                val bitMask = 0x1 shl bitsRead % 8

                // check if bit is on or off
                val currentBit = (dataByteArray[bitsRead / 8] and bitMask.toByte()) != 0.toByte()
                if (!currentBit) {
                    bitsRead++
                    continue
                }

                if (bitsInARow == 0) {
                    startAngle = angle
                }
                bitsInARow++

                val nextOffset = (bitsRead - bitsReadBeforeCurrentRing + 1) % bitsPerRing + bitsReadBeforeCurrentRing
                val nextBitMask = 0x1 shl nextOffset % 8
                var nextBit = (dataByteArray[nextOffset / 8] and nextBitMask.toByte()) != 0.toByte()

                // This is for the edge case where the start bit of the ring and the end bit of the ring both are there to draw over
                // Note:: nextbit in this case would be the first bit of the current ring (nextOffset is modded by bitsPerRing)
                if (j + 1 == bitsPerRing && nextBit) {
                    bitsInARow++
                    // Set to false so it will draw
                    nextBit = false
                }

                // If the next bit is not present, draw the arc, draw's 1 arc to avoid weird artifacts
                if (!nextBit) {
                    if (bitsInARow > 1) {
                        val rectF = RectF(midX - currentRadius, midY - currentRadius, midX + currentRadius, midY + currentRadius)
                        canvas.drawArc(rectF, Math.toDegrees(startAngle).toFloat(), Math.toDegrees(anglePerBit * (bitsInARow - 1)).toFloat(), false, arcPaint)
                    } else {
                        val currentX = midX + currentRadius * Math.cos(angle)
                        val currentY = midY + currentRadius * Math.sin(angle)

                        canvas.drawCircle(currentX.toFloat(), currentY.toFloat(), dotSize / 2, circlePaint)
                    }
                    bitsInARow = 0
                }

                bitsRead++
            }
        }

        // Render logo in the middle
        badge?.apply {
            setBounds((midX - innerRingRadius).toInt(), (midY - innerRingRadius).toInt(), (midX + innerRingRadius).toInt(), (midY + innerRingRadius).toInt())
            draw(canvas)
        }
    }
}
