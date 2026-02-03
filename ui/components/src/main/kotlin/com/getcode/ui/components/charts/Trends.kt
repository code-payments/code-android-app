package com.getcode.ui.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.getcode.theme.CodeTheme

sealed interface LineTrend {
    @get:Composable
    val color: Color
    @get:Composable
    val pressedColor: Color

    data object Up : LineTrend {
        override val color: Color
            @Composable get() = CodeTheme.colors.success
        override val pressedColor: Color
            @Composable get() = Color(0xFF113522)
    }

    data object Down : LineTrend {
        override val color: Color
            @Composable get() = CodeTheme.colors.error
        override val pressedColor: Color
            @Composable get() = Color(0xFF3C2525)
    }
}

sealed interface TrendType {
    data object LinearRegression : TrendType {
        override fun determineTrend(data: List<Double>): LineTrend {
            if (data.size < 2) return LineTrend.Up

            val n = data.size
            val sumX = (0 until n).sum().toFloat()
            val sumY = data.sum()
            val sumXY = data.mapIndexed { i, y -> i * y }.sum()
            val sumX2 = (0 until n).sumOf { it * it }.toFloat()

            val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
            return if (slope >= 0) LineTrend.Up else LineTrend.Down
        }
    }
    data object Momentum : TrendType {
        override fun determineTrend(data: List<Double>): LineTrend {
            if (data.size < 2) return LineTrend.Up

            val windowSize = (data.size * 0.2f).toInt().coerceAtLeast(1)
            val startAvg = data.take(windowSize).average()
            val endAvg = data.takeLast(windowSize).average()

            return if (endAvg >= startAvg) LineTrend.Up else LineTrend.Down
        }
    }
    data object Majority : TrendType {
        override fun determineTrend(data: List<Double>): LineTrend {
            if (data.size < 2) return LineTrend.Up

            val ups = data.zipWithNext().count { (a, b) -> b >= a }
            return if (ups >= data.size / 2) LineTrend.Up else LineTrend.Down
        }
    }

    data object FirstVsLast: TrendType {
        override fun determineTrend(data: List<Double>): LineTrend {
            if (data.size < 2) return LineTrend.Up
            return if (data.first() <= data.last()) LineTrend.Up else LineTrend.Down
        }
    }

    fun determineTrend(data: List<Double>): LineTrend
}