package com.kik.kikx.kikcodes

class KikCodeColorMapper {

    fun indexToKikCodeColor(colorIndex: Int): KikCodeColor {
        return KikCodeColor.values()[colorIndex % KikCodeColor.values().size]
    }
}
