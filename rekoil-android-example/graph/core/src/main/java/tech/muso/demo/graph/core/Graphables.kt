package tech.muso.demo.graph.core

import android.graphics.PointF

interface Graphable {
    val top: Float
    val bottom: Float
    val center: PointF
    val x: Float
        get() = center.x
    val y: Float
        get() = center.y
}

data class PointGraphable(val pointX: Float, val pointY: Float) : PointF(pointX,pointY), Graphable {
    override val top: Float = pointY
    override val bottom: Float = pointY
    override val center: PointF
        get() = this
}

data class CandleGraphable(
    override val x: Float,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Int
) : Graphable {
    override val top: Float = high
    override val bottom: Float = low
    override val center: PointF by lazy { PointF(x, (high - low) ) } // todo: maybe we also want to avoid extremes? then we can use abs(open-close)
}