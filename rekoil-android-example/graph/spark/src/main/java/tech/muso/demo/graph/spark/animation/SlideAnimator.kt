package tech.muso.demo.graph.spark.animation

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import androidx.core.animation.addListener
import tech.muso.demo.graph.core.CandleGraphable
import tech.muso.demo.graph.core.Graphable
import tech.muso.demo.graph.core.NullGraphable
import tech.muso.demo.graph.spark.FifoList
import kotlin.math.max

class SlideAnimator(val preRasterGraphables: FifoList<Graphable>): Animator() {
    val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

    // TODO: look into [Path.approximate(float acceptableError)] for API 26+

    // TODO: handle extra point generation, potentially label points as approximations???
    companion object {

        /** Linear interpolation between `startValue` and `endValue` by `fraction`.  */
        @JvmStatic fun lerp(startValue: Float, endValue: Float, fraction: Float): Float {
            return startValue + fraction * (endValue - startValue)
        }

        /** Linearly interpolate a list of the points between `startValue` and `endValue`
         *  by the total number of `steps` including the start and end points.
         *  The returned list will not include the start value.
         */
        @JvmStatic fun lerp(startValue: Float, endValue: Float, steps: Int): List<Float> {
            return (1 .. steps).map {
                lerp(startValue, endValue, it.toFloat()/steps)
            }
        }

        @JvmStatic fun lerp(startPoint: PointF, endPoint: PointF, steps: Int): List<PointF> {
            // TODO: worry about performance of this
            val xInterpolation = lerp(startPoint.x, endPoint.x, steps)
            val yInterpolation = lerp(startPoint.y, endPoint.y, steps)
            return xInterpolation.mapIndexed { i, x ->
                PointF(x, yInterpolation[i])
            }
        }

        /**
         * Get the greatest common factor using euclidean algorithm.
         */
        fun gcf(first: Int, second: Int): Int {
            // make sure that a > b
            val a = if (first > second) first else second
            var b = if (second < first) second else first
            var c = a - b
            while (c > b) c -= b
            while (b - c < 0) b -= c
            return b
        }

        /**
         * Compute the nearest fraction between [0, 1) by walking a Stern-Brocot tree.
         *
         * Runtime: O(log2(n))
         */
        fun ratioToFractionTuple(ratio: Float, errorMargin: Float = 0.05f): Pair<Int, Int> {
            // lower bound of tree 0/1
            var lowerNumerator = 0
            var lowerDenominator = 1
            // upper bound of tree 1/1
            var upperNumerator = 1
            var upperDenominator = 1

            while(true) {
                // calculate the mediant between upper and lower
                val midNumerator = lowerNumerator + upperNumerator
                val midDenominator = lowerDenominator + upperDenominator
                when {
                    // if below the middle, and outside margin of error; walk down tree to the left
                    midDenominator * (ratio + errorMargin) < midNumerator -> {
                        // walk left, mediant becomes upper bound
                        upperNumerator = midNumerator
                        upperDenominator = midDenominator
                    }
                    midNumerator < (ratio - errorMargin) * midDenominator -> {
                        // walk right, mediant becomes lower bound
                        lowerNumerator = midNumerator
                        lowerDenominator = midDenominator
                    }
                    else -> {
                        // exit condition, where we are within the margin of error
                        return Pair(midNumerator, midDenominator)
                    }
                }
            }
        }
    }

    lateinit var endingPointsAdjusted: List<Graphable>
    lateinit var startingPointsAdjusted: List<Graphable>

    @OptIn(ExperimentalStdlibApi::class)
    fun animateToDataSet(dataSet: List<Graphable>, doOnFinish: () -> Unit, doOnUpdate: () -> Unit): Animator {
        if (dataSet.isEmpty()) {
            return this
        }

        // make an extra point to start that is a copy of the most current point
        val startPoints = preRasterGraphables.toList() + preRasterGraphables.last()

        val isFull = preRasterGraphables.size == preRasterGraphables.capacity
        endingPointsAdjusted = if (!isFull) dataSet else listOf(NullGraphable()) + dataSet
        startingPointsAdjusted = startPoints

        val totalPoints = max(endingPointsAdjusted.size, startingPointsAdjusted.size)
        println("ANIMATING: total[$totalPoints] ${startPoints.size} -> ${dataSet.size}")

        // clear existing points, we will overwrite the values
        val lastCopy = preRasterGraphables.last() // add this to the end to transform
        preRasterGraphables.clear()
        for (i in 0 until totalPoints) {
            preRasterGraphables.add(NullGraphable())
        }
        preRasterGraphables.add(lastCopy)   // add a copy of the last element, will evict first if full


        if (isFull) {
            println("ANIMATING [F]: total[$totalPoints:${preRasterGraphables.size}] ${startingPointsAdjusted.size} -> ${endingPointsAdjusted.size}")

            // just in case. TODO: remove
//            valueAnimator.removeAllUpdateListeners()

            // animate when we are shifting candles
            val end = endingPointsAdjusted.last() as CandleGraphable
            val mostRecentCandle = startingPointsAdjusted.last()

            valueAnimator.addUpdateListener {
                val ratio: Float = it.animatedValue as Float
                // handle animating the last (and most recent) point/candle first
                println("start [${mostRecentCandle}] -> end [${end}]")
                preRasterGraphables[preRasterGraphables.size - 1] = mostRecentCandle.lerpTo(end, ratio)

                // update every point except for the most recent one
                for (i in 0 until preRasterGraphables.size - 1) {
                    val endPoint = endingPointsAdjusted[i]
                    val startPoint = startingPointsAdjusted[i + 1]
                    preRasterGraphables[i] = startPoint.lerpTo(endPoint, ratio)
                }
                doOnUpdate.invoke()
            }
        } else {
            println("ANIMATING [0]: total[$totalPoints:${preRasterGraphables.size}] ${startingPointsAdjusted.size} -> ${endingPointsAdjusted.size}")

            val end = endingPointsAdjusted[max(0, endingPointsAdjusted.size - 1)] as CandleGraphable

            println("ANIMATING [0]: end $end")
            val mostRecentCandle = CandleGraphable(
                x=end.x,
                open=end.open,
                close=end.open,
                high=end.open,
                low=end.open,
                volume=0
            )

            // animate when the number of candles is growing.
            valueAnimator.addUpdateListener {
                val ratio: Float = it.animatedValue as Float

                // update most recent candle
                preRasterGraphables[preRasterGraphables.size - 1] = mostRecentCandle.lerpTo(end, ratio)

                // map points; NOTE: needs to iterate from zero up to present
                for (i in 0 until preRasterGraphables.size - 1) {

                    val endPoint = if (i >= endingPointsAdjusted.size) {
                        endingPointsAdjusted.last()
                    } else {
                        endingPointsAdjusted[i]
                    }

                    val startPoint = if (i >= startingPointsAdjusted.size) {
                        startingPointsAdjusted.last()
                    } else {
                        startingPointsAdjusted[i]
                    }
//                    val endPoint = endingPointsAdjusted[i]
//                    val startPoint = startingPointsAdjusted[i]

                    preRasterGraphables[i] = startPoint.lerpTo(endPoint, ratio)
                }
                doOnUpdate.invoke()
            }
        }

        valueAnimator.addListener(
//            onCancel= {
//                // wont print because we remove this listener in the cancel() method
//                Log.i("MorphAnimator", "valueAnimator.doOnCancel() (animatedValue = ${valueAnimator.animatedValue})")
//            },
            onEnd= {
                // only invoke on completed animation.
                if (valueAnimator.animatedValue as Float == 1f) {
                    Log.i("MorphAnimator", "valueAnimator.doOnEnd() -> invoke final line set.")
                    doOnFinish.invoke()
                    valueAnimator.removeAllUpdateListeners()    // update listeners are separate for whatever reason lamo
                    valueAnimator.removeAllListeners()  // must remove listeners on finish because we reuse animator.
                }
            }
        )

        // NOTE: if duration is not shorter than time to receive a new update,
        //   a cancelled animation will appear to transform in place
        valueAnimator.duration = 300
        return valueAnimator
    }

    override fun isRunning(): Boolean = valueAnimator.isRunning

    override fun getDuration(): Long = valueAnimator.duration

    override fun getStartDelay(): Long = valueAnimator.startDelay

    override fun setStartDelay(startDelay: Long) {
        valueAnimator.startDelay = startDelay
    }

    override fun setInterpolator(value: TimeInterpolator?) {
        valueAnimator.interpolator = value
    }

    override fun setDuration(duration: Long): Animator {
        return valueAnimator.setDuration(duration)
    }

    /**
     * When cancelled, forward to our valueAnimator.
     */
    override fun cancel() {
        valueAnimator.removeAllUpdateListeners()
        valueAnimator.removeAllListeners() // remove listeners on cancel because we reuse
        preRasterGraphables.removeLast()
        return valueAnimator.cancel()
    }
}