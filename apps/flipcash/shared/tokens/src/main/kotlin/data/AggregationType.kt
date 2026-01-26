package com.flipcash.app.tokens.data

sealed interface AggregationType {
    fun aggregate(points: List<MarketCapPoint>): Double?

    data object Last : AggregationType {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.maxByOrNull { it.x }?.y
        }
    }
    data object First : AggregationType {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.minByOrNull { it.x }?.y
        }
    }
    data object Average : AggregationType {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.map { it.y }.average()
        }
    }
    data object Max : AggregationType {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.maxOfOrNull { it.y }
        }
    }
    data object Min : AggregationType {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.minOfOrNull { it.y }
        }
    }
}