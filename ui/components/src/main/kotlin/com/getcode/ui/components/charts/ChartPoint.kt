package com.getcode.ui.components.charts

data class ChartPoint<X, Y>(
    val x: X,
    val y: Y,
)

val <X, Y> List<ChartPoint<X, Y>>.yValues: List<Y> get() = map { it.y }
val <X, Y> List<ChartPoint<X, Y>>.xValues: List<X> get() = map { it.x }