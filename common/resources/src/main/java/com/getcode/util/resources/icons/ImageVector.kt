package com.getcode.util.resources.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.group

fun ImageVector.Companion.copyFrom(
    src: ImageVector,
    mirror: Boolean = false,
    rotation: Float = src.root.rotation,
    pivotX: Float = src.defaultWidth.value / 2,
    pivotY: Float = src.defaultHeight.value / 2,
) = ImageVector.Builder(
    name = src.name,
    defaultWidth = src.defaultWidth,
    defaultHeight = src.defaultHeight,
    viewportWidth = src.viewportWidth,
    viewportHeight = src.viewportHeight,
    tintColor = src.tintColor,
    tintBlendMode = src.tintBlendMode,
    autoMirror = src.autoMirror,
).addGroup(
    src = src.root,
    rotation = rotation,
    pivotX = pivotX,
    pivotY = pivotY,
    scaleX = if (mirror) -1f else 1f,
    scaleY = if (mirror) 1f else 1f,
).build()

private fun ImageVector.Builder.addNode(node: VectorNode) {
    when (node) {
        is VectorGroup -> addGroup(node)
        is VectorPath -> addPath(node)
    }
}

private fun ImageVector.Builder.addGroup(
    src: VectorGroup,
    rotation: Float = src.rotation,
    pivotX: Float = src.pivotX,
    pivotY: Float = src.pivotY,
    scaleX: Float = src.scaleX,
    scaleY: Float = src.scaleY,
) = apply {
    group(
        name = src.name,
        rotate = rotation,
        pivotX = pivotX,
        pivotY = pivotY,
        scaleX = scaleX,
        scaleY = scaleY,
        translationX = src.translationX,
        translationY = src.translationY,
        clipPathData = src.clipPathData,
    ) {
        src.forEach { addNode(it) }
    }
}

private fun ImageVector.Builder.addPath(src: VectorPath) = apply {
    addPath(
        pathData = src.pathData,
        pathFillType = src.pathFillType,
        name = src.name,
        fill = src.fill,
        fillAlpha = src.fillAlpha,
        stroke = src.stroke,
        strokeAlpha = src.strokeAlpha,
        strokeLineWidth = src.strokeLineWidth,
        strokeLineCap = src.strokeLineCap,
        strokeLineJoin = src.strokeLineJoin,
        strokeLineMiter = src.strokeLineMiter,
    )
}