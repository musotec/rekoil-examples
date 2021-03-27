package tech.muso.demo.graph.core

import android.graphics.Color
import android.graphics.PointF
import tech.muso.demo.graph.core.Graphable.Companion.lerp

interface Graphable {
    companion object {
        /** Linear interpolation between `startValue` and `endValue` by `fraction`.  */
        @JvmStatic
        fun lerp(startValue: Float, endValue: Float, fraction: Float): Float {
            return startValue + fraction * (endValue - startValue)
        }
    }

    fun lerpTo(other: Graphable, ratio: Float): Graphable
    fun lerpBetween(other: Graphable, steps: Int): List<Graphable> {
        return (1 .. steps).map {
            this.lerpTo(other, it.toFloat()/steps)
        }
    }

    val top: Float
    val bottom: Float
    val center: PointF
    val x: Float
        get() = center.x
    val y: Float
        get() = center.y
}

class NullGraphable : Graphable {
    override fun lerpTo(other: Graphable, ratio: Float): Graphable {
        return other
    }

    override val top: Float
        get() = 0f
    override val bottom: Float
        get() = 0f
    override val center: PointF by lazy { PointF(0f, 0f) }
    override val x: Float
        get() = super.x
    override val y: Float
        get() = super.y
}

data class PointGraphable(val pointX: Float, val pointY: Float) : PointF(pointX,pointY), Graphable {
    override fun lerpTo(other: Graphable, ratio: Float): Graphable {
        return PointGraphable(lerp(pointX, other.x, ratio), lerp(pointY, other.y, ratio))
    }

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
    val volume: Int,
    val color: Int = if (open-close > 0) Color.GREEN else Color.RED
) : Graphable {
    override fun lerpTo(other: Graphable, ratio: Float): Graphable {
        if (other is CandleGraphable) {
            return CandleGraphable(
                x=lerp(x, other.x, ratio),
                open=lerp(open, other.open, ratio),
                close=lerp(close, other.close, ratio),
                high=lerp(high, other.high, ratio),
                low=lerp(low, other.low, ratio),
                volume=other.volume,
                color=other.color
            )
        } else {
            // otherwise just shrink candle down to new y position (at end will be point)
            return CandleGraphable(
                x=lerp(x, other.x, ratio),
                open=lerp(open, other.y, ratio),
                close=lerp(close, other.y, ratio),
                high=lerp(high, other.y, ratio),
                low=lerp(low, other.y, ratio),
                volume=0,
            )
        }
    }

    override val top: Float = high
    override val bottom: Float = low
    val height: Float = high - low
    override val center: PointF by lazy { PointF(x, height/2 + low) } // todo: maybe we also want to avoid extremes? then we can use abs(open-close)

    companion object {
        val EMPTY_CANDLE = CandleGraphable(0f, 0f, 0f, 0f, 0f, 0)
    }

//    fun scaledCandle(targetX: Float, targetY: Float): CandleGraphable {
//        center.y
//        return CandleGraphable(
//            x=targetX,
//            open=,
//            close=,
//            high=,
//            low=,
//            volume=volume
//        )
//    }
}