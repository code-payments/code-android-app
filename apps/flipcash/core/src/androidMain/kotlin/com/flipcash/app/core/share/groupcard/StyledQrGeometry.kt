package com.flipcash.app.core.share.groupcard

import java.util.Locale

/** One drawing step in the styled QR's geometry; coordinates are in module units. */
sealed interface PathOp {
    data class Move(val x: Float, val y: Float) : PathOp
    data class Line(val x: Float, val y: Float) : PathOp
    data class Cubic(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x: Float, val y: Float,
    ) : PathOp
    data object Close : PathOp
}

/** Which corners of a module are rounded. */
data class ModuleCorners(
    val topLeft: Boolean,
    val topRight: Boolean,
    val bottomRight: Boolean,
    val bottomLeft: Boolean,
)

/**
 * The group card's QR style, as plain geometry so the on-screen card, the PNG and the SVG draw
 * from one set of numbers. Everything is in module units on a `(modules + 8)` square, quiet zone
 * included; paths are meant to be filled even-odd.
 */
object StyledQrGeometry {
    const val QuietZone = 4

    /** The tile colour, sampled from the reference; neither app has a token near it. */
    const val TileColorHex = "#2E2E30"
    const val TileColorArgb = 0xFF2E2E30.toInt()
    const val TileCornerFraction = 0.12f

    private const val FinderSize = 7
    private const val LogoClear = 9
    private const val LogoBox = 5f
    private const val ModuleRadius = 0.5f
    private const val Kappa = 0.5522847498f

    /** The Flipcash mark: the three inner subpaths of `ic_logo_round_white` (no disc). */
    const val LogoPathData =
        "M24.843,15L24.811,15C22.154,15 20,17.154 20,19.811C20,22.469 22.154,24.623 24.811,24.623L24.811,24.623L34.434,24.623L34.434,24.623L37.642,24.623L39.245,24.623L39.246,24.623C41.903,24.623 44.057,22.469 44.057,19.812C44.057,17.154 41.903,15 39.246,15L39.245,15L37.642,15L34.434,15L34.434,15L24.843,15ZM34.434,27.188L36.038,27.188L36.038,27.188C38.695,27.188 40.849,29.342 40.849,32C40.849,34.657 38.695,36.811 36.038,36.811L36.038,36.811L34.434,36.811L24.858,36.811L24.811,36.811C22.154,36.811 20,34.657 20,32C20,29.343 22.154,27.188 24.811,27.188L24.811,27.188L24.811,27.188L34.434,27.188ZM29.623,44.189C29.623,41.531 27.469,39.377 24.811,39.377C22.154,39.377 20,41.531 20,44.189C20,46.846 22.154,49 24.811,49C27.469,49 29.623,46.846 29.623,44.189Z"
    private const val LogoMinX = 20f
    private const val LogoMaxX = 44.057f
    private const val LogoMinY = 15f
    private const val LogoMaxY = 49f

    /** Side of the whole tile in module units. */
    fun tileUnits(matrix: QrMatrix): Int = matrix.size + 2 * QuietZone

    private fun inFinder(size: Int, x: Int, y: Int): Boolean {
        val nearLeft = x < FinderSize
        val nearRight = x >= size - FinderSize
        val nearTop = y < FinderSize
        val nearBottom = y >= size - FinderSize
        return (nearLeft && nearTop) || (nearRight && nearTop) || (nearLeft && nearBottom)
    }

    private fun inLogoClear(size: Int, x: Int, y: Int): Boolean {
        val start = (size - LogoClear) / 2
        return x in start until start + LogoClear && y in start until start + LogoClear
    }

    /** A module is drawn as a module when it is dark and not part of a finder or the logo's clear area. */
    fun isDrawn(matrix: QrMatrix, x: Int, y: Int): Boolean =
        matrix[x, y] && !inFinder(matrix.size, x, y) && !inLogoClear(matrix.size, x, y)

    /**
     * A corner is rounded when neither of the two orthogonal neighbours sharing it is set, so
     * neighbouring modules read as one continuous shape.
     */
    fun cornersOf(matrix: QrMatrix, x: Int, y: Int): ModuleCorners {
        val up = isDrawn(matrix, x, y - 1)
        val down = isDrawn(matrix, x, y + 1)
        val left = isDrawn(matrix, x - 1, y)
        val right = isDrawn(matrix, x + 1, y)
        return ModuleCorners(
            topLeft = !up && !left,
            topRight = !up && !right,
            bottomRight = !down && !right,
            bottomLeft = !down && !left,
        )
    }

    /** Modules, finder shapes and logo, in one path to fill even-odd. */
    fun build(matrix: QrMatrix): List<PathOp> = buildList {
        val q = QuietZone.toFloat()
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!isDrawn(matrix, x, y)) continue
            val c = cornersOf(matrix, x, y)
            addRoundedRect(
                q + x, q + y, 1f, 1f,
                if (c.topLeft) ModuleRadius else 0f,
                if (c.topRight) ModuleRadius else 0f,
                if (c.bottomRight) ModuleRadius else 0f,
                if (c.bottomLeft) ModuleRadius else 0f,
            )
        }
        val far = (matrix.size - FinderSize).toFloat()
        for ((fx, fy) in listOf(0f to 0f, far to 0f, 0f to far)) {
            // A 7x7 ring one module thick, then its 3x3 centre.
            addRoundedRect(q + fx, q + fy, 7f, 7f, 2f, 2f, 2f, 2f)
            addRoundedRect(q + fx + 1, q + fy + 1, 5f, 5f, 1f, 1f, 1f, 1f)
            addRoundedRect(q + fx + 2, q + fy + 2, 3f, 3f, 1f, 1f, 1f, 1f)
        }
        addAll(logo(matrix))
    }

    /** The mark fitted to a 5x5-module box at the symbol's centre, by its own bounds. */
    fun logo(matrix: QrMatrix): List<PathOp> {
        val width = LogoMaxX - LogoMinX
        val height = LogoMaxY - LogoMinY
        val scale = LogoBox / maxOf(width, height)
        val centre = QuietZone + matrix.size / 2f
        val cx = (LogoMinX + LogoMaxX) / 2f
        val cy = (LogoMinY + LogoMaxY) / 2f
        return parsePathData(LogoPathData).map { op ->
            fun tx(x: Float) = centre + (x - cx) * scale
            fun ty(y: Float) = centre + (y - cy) * scale
            when (op) {
                is PathOp.Move -> PathOp.Move(tx(op.x), ty(op.y))
                is PathOp.Line -> PathOp.Line(tx(op.x), ty(op.y))
                is PathOp.Cubic -> PathOp.Cubic(tx(op.x1), ty(op.y1), tx(op.x2), ty(op.y2), tx(op.x), ty(op.y))
                PathOp.Close -> op
            }
        }
    }

    /** Absolute `M`, `L`, `C` and `Z` only, which is all the mark uses. */
    internal fun parsePathData(data: String): List<PathOp> {
        val ops = mutableListOf<PathOp>()
        val tokens = Regex("[MLCZ]|-?\\d*\\.?\\d+").findAll(data).map { it.value }.toList()
        var i = 0
        fun num() = tokens[i++].toFloat()
        var cmd = 'M'
        while (i < tokens.size) {
            if (tokens[i].first().isLetter()) cmd = tokens[i++].first()
            when (cmd) {
                'M' -> ops += PathOp.Move(num(), num()).also { cmd = 'L' }
                'L' -> ops += PathOp.Line(num(), num())
                'C' -> ops += PathOp.Cubic(num(), num(), num(), num(), num(), num())
                'Z' -> ops += PathOp.Close
                else -> error("Unsupported path command $cmd")
            }
        }
        return ops
    }

    private fun MutableList<PathOp>.addRoundedRect(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, tr: Float, br: Float, bl: Float,
    ) {
        val k = Kappa
        add(PathOp.Move(x + tl, y))
        add(PathOp.Line(x + w - tr, y))
        if (tr > 0f) add(PathOp.Cubic(x + w - tr + k * tr, y, x + w, y + tr - k * tr, x + w, y + tr))
        add(PathOp.Line(x + w, y + h - br))
        if (br > 0f) add(PathOp.Cubic(x + w, y + h - br + k * br, x + w - br + k * br, y + h, x + w - br, y + h))
        add(PathOp.Line(x + bl, y + h))
        if (bl > 0f) add(PathOp.Cubic(x + bl - k * bl, y + h, x, y + h - bl + k * bl, x, y + h - bl))
        add(PathOp.Line(x, y + tl))
        if (tl > 0f) add(PathOp.Cubic(x, y + tl - k * tl, x + tl - k * tl, y, x + tl, y))
        add(PathOp.Close)
    }

    /** [ops] as an SVG `d` attribute. */
    fun toSvgPathData(ops: List<PathOp>): String = buildString {
        for (op in ops) {
            when (op) {
                is PathOp.Move -> append('M').append(n(op.x)).append(' ').append(n(op.y))
                is PathOp.Line -> append('L').append(n(op.x)).append(' ').append(n(op.y))
                is PathOp.Cubic -> append('C').append(n(op.x1)).append(' ').append(n(op.y1)).append(' ')
                    .append(n(op.x2)).append(' ').append(n(op.y2)).append(' ')
                    .append(n(op.x)).append(' ').append(n(op.y))
                PathOp.Close -> append('Z')
            }
        }
    }

    private fun n(v: Float): String {
        val s = String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0" || s.isEmpty()) "0" else s
    }

    /** The whole export as a self-contained SVG: tile, then one white path. No raster. */
    fun toSvg(matrix: QrMatrix): String {
        val units = tileUnits(matrix)
        val radius = n(units * TileCornerFraction)
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $units $units">""" +
            """<rect width="$units" height="$units" rx="$radius" ry="$radius" fill="$TileColorHex"/>""" +
            """<path fill="#FFFFFF" fill-rule="evenodd" d="${toSvgPathData(build(matrix))}"/>""" +
            "</svg>\n"
    }
}
