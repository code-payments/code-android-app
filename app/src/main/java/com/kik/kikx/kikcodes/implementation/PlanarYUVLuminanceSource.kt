/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kik.kikx.kikcodes.implementation

import android.graphics.Bitmap

import kotlin.experimental.and

/**
 * This object extends LuminanceSource around an array of YUV data returned from the camera driver,
 * with the option to crop to a rectangle within the full data. This can be used to exclude
 * superfluous pixels around the perimeter and speed up decoding.
 *
 * It works for any pixel format where the Y channel is planar and appears first, including
 * YCbCr_420_SP and YCbCr_422_SP.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 *
 * jmeyer: NOTE
 * This class used to extend LuminanceSource. It has been trimmed down to not require a ZXing import
 */
class PlanarYUVLuminanceSource(private val _yuvData: ByteArray, private val _dataWidth: Int, private val _dataHeight: Int, private val _left: Int, private val _top: Int, private val width: Int, private val height: Int, reverseHorizontal: Boolean) {

    // If the caller asks for the entire underlying image, save the copy and give them the
    // original data. The docs specifically warn that result.length must be ignored.
    // If the width matches the full width of the underlying data, perform a single copy.
    // Otherwise copy one cropped row at a time.
    val matrix: ByteArray
        get() {
            val width = width
            val height = height
            if (width == _dataWidth && height == _dataHeight) {
                return _yuvData
            }

            val area = width * height
            val matrix = ByteArray(area)
            var inputOffset = _top * _dataWidth + _left
            if (width == _dataWidth) {
                System.arraycopy(_yuvData, inputOffset, matrix, 0, area)
                return matrix
            }
            val yuv = _yuvData
            for (y in 0 until height) {
                val outputOffset = y * width
                System.arraycopy(yuv, inputOffset, matrix, outputOffset, width)
                inputOffset += _dataWidth
            }
            return matrix
        }

    val isCropSupported: Boolean
        get() = true

    init {

        if (_left + width > _dataWidth || _top + height > _dataHeight) {
//            LogUtils.throwOrLog(IllegalArgumentException("Crop rectangle does not fit within image data."))
        }
        if (reverseHorizontal) {
            reverseHorizontal(width, height)
        }
    }

    fun getRow(y: Int, row: ByteArray?): ByteArray {
        var row = row
        if (y < 0 || y >= height) {
            throw IllegalArgumentException("Requested row is outside the image: $y")
        }
        val width = width
        if (row == null || row.size < width) {
            row = ByteArray(width)
        }
        val offset = (y + _top) * _dataWidth + _left
        System.arraycopy(_yuvData, offset, row, 0, width)
        return row
    }

    fun crop(left: Int, top: Int, width: Int, height: Int): PlanarYUVLuminanceSource {
        return PlanarYUVLuminanceSource(_yuvData, _dataWidth, _dataHeight, this._left + left, this._top + top, width, height, false)
    }

    fun renderCroppedGreyscaleBitmap(): Bitmap {
        val width = width
        val height = height
        val pixels = IntArray(width * height)
        val yuv = _yuvData
        var inputOffset = _top * _dataWidth + _left

        for (y in 0 until height) {
            val outputOffset = y * width
            for (x in 0 until width) {
                val grey = yuv[inputOffset + x] and 0xff.toByte()
                pixels[outputOffset + x] = -0x1000000 or grey * 0x00010101
            }
            inputOffset += _dataWidth
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun reverseHorizontal(width: Int, height: Int) {
        val yuvData = this._yuvData
        var y = 0
        var rowStart = _top * _dataWidth + _left
        while (y < height) {
            val middle = rowStart + width / 2
            var x1 = rowStart
            var x2 = rowStart + width - 1
            while (x1 < middle) {
                val temp = yuvData[x1]
                yuvData[x1] = yuvData[x2]
                yuvData[x2] = temp
                x1++
                x2--
            }
            y++
            rowStart += _dataWidth
        }
    }
}
