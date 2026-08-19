package com.getcode.opencode.model.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class TokenBillCustomizations(
    val background: BillBackground,
    val texture: BillTexture?,
    val icon: ByteArray?,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenBillCustomizations

        if (background != other.background) return false
        if (texture != other.texture) return false
        if (!icon.contentEquals(other.icon)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + icon.contentHashCode()
        return result
    }
}

@Parcelize
@Serializable
data class BillTexture(
    val index: Int,
    val blendMode: BlendMode,
    val strength: Float,
) : Parcelable

@Parcelize
@Serializable
sealed interface BillBackground : Parcelable {
    @Serializable
    data class Solid(val colorHex: String) : BillBackground {
        companion object {
            fun from(colorInt: Int): Solid {
                return Solid(String.format("#%06X", 0xFFFFFF and colorInt))
            }
        }
    }

    @Serializable
    data class Gradient(val colors: List<String>) : BillBackground {
        companion object {
            fun from(colorInts: List<Int>): Gradient {
                return Gradient(colorInts.map { String.format("#%06X", 0xFFFFFF and it) })
            }
        }
    }

    companion object {
        /**
         * USDF / "Dollars" fixed gold branding (Figma) — the single source of truth shared by the
         * wallet card and the full-screen cash bill, so the two always match. USDF carries no
         * user-chosen bill colors, so both surfaces fall back to this gradient.
         */
        val Usdf: Gradient = Gradient(listOf("#C4980B", "#B06B00"))
    }
}

@Serializable
enum class BlendMode {
    Normal,
    Lighten,
    Screen,
    ColorDodge,
    PlusLighter,
    ;
}