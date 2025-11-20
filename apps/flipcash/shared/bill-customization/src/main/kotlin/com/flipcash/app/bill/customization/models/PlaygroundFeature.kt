package com.flipcash.app.bill.customization.models

import com.flipcash.shared.bill.customizations.R

sealed class PlaygroundFeature(val labelRes: Int) {
    interface Colorable
    interface Graphic

    data object Background: PlaygroundFeature(R.string.label_billCreatorBackground), Colorable
    data object Textures: PlaygroundFeature(R.string.label_billCreatorTextures), Graphic

    companion object {
        val entries = listOf(Background, /*Textures*/)
    }
}