package tech.muso.demo.graph.spark.animation

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.PointF
import android.util.Log
import androidx.core.animation.addListener
import java.lang.IndexOutOfBoundsException
import kotlin.math.ceil
import kotlin.math.max

class MorphAnimator(val renderPoints: MutableList<PointF>): Animator() {
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
    lateinit var endingPointsAdjusted: List<PointF>
    lateinit var startingPointsAdjusted: List<PointF>

    @OptIn(ExperimentalStdlibApi::class)
    fun animatePathToDataSet(endingPoints: List<PointF>, doOnFinish: () -> Unit, doOnUpdate: () -> Unit): Animator {
        if (endingPoints.isEmpty()) {
            return this
        }

        val startSize = renderPoints.size
        val endSize = endingPoints.size
        val totalPointsAnimation = max(startSize, endSize)

        endingPointsAdjusted = if (startSize <= totalPointsAnimation) {
            endingPoints
//                .also { Log.e("POINT", "endingPoints.size = ${it.size}") }
        } else {
            // amount of points to interpolate
            val stepSize: Int =
                (ceil(startSize.toDouble()/endSize) + 2).toInt() // add 2 for start/end of lerp

            // save list of start point so we can merge; since our lerp excludes the first value
            listOf(endingPoints[0]) + (
            // iterate from bottom to top joining the lists doing a lerp between points
            endingPoints.windowed(2, 1).map {
                val start: PointF = it[0]
                val end: PointF = it[1]
                lerp(start, end, stepSize)  // generate a list of the points between
            }).flatten().also { Log.e("POINT", "endingPoints flatten.size = ${it.size}") }    // now flatten our list of lists
        }

        // TODO: refactor out common code.
        startingPointsAdjusted = when {
            endSize < totalPointsAnimation -> {
                // ensure equal size before iterating during the animation by extending extra points to last value.
                renderPoints + (0..(endingPointsAdjusted.size + 1 - renderPoints.size)).map { renderPoints[startSize - 1] }
                // this saves us from having to do an index check during our map inside the animation.
            }
            endSize != startSize -> {
                val stepSize: Int =
                    (ceil(endSize.toDouble()/startSize) + 2).toInt() // add 2 for start/end of lerp

                // resolution is what the max amount of expansion we will allow (1x -> 10x)
                val resolution = 20f    // TODO: should match the draw resolution to avoid excessive points

                val (reducedStart, reducedEnd) = ratioToFractionTuple(
                    ratio = startSize.toFloat()/endSize.toFloat(),
                    errorMargin = 1f/resolution
                )

                Log.w("AnimateTransition", "Adding extra points:: start=$startSize,end=$endSize -> s=$reducedStart,e=$reducedEnd")

                buildList(endSize) {
                    // window by one extra each time to have last value be next lerp.
                    renderPoints.windowed(reducedStart, reducedStart, partialWindows = true).forEach {
                        addAll(it) // add the window
                        // then fill the remaining points
                        for (i in reducedStart until reducedEnd) {
//                            Log.d("AnimateTransition", "Extending point: $it")
                            add(it.last())  // then extend the last index over the empty
                        }
                    }
                }.also {
                    Log.e("POINT", "Extended to ${it.size}")
                }
            }
            endSize == startSize -> {
                buildList(endSize) {
                    addAll(renderPoints)
                }
            }
            else -> {
                // TODO: ??? I DON'T REMEMBER WHY THIS WORKS - PROBABLY THE SAME AS A COPY??
                //   DOES THIS EVEN RUN???
                val stepSize: Int =
                    (ceil(endSize.toDouble()/startSize) + 2).toInt() // add 2 for start/end of lerp

                listOf(renderPoints[0]) + (
                        // iterate from bottom to top joining the lists doing a lerp between points
                        renderPoints.windowed(2, 1).map {
                            val start: PointF = it[0]
                            val end: PointF = it[1]
                            lerp(start, end, stepSize)  // generate a list of the points between
                        }).flatten() // flatten our list of lists (from windowed mapping of lerps)
            }
        }


        // clear existing points, as the mapTo function adds to the list instead of overwriting
        renderPoints.clear()
        val totalPoints = max(endingPointsAdjusted.size, startingPointsAdjusted.size)

        for (i in 0..totalPoints) {
            renderPoints.add(PointF(0f, 0f))
        }

        valueAnimator.addUpdateListener {
            val ratio: Float = it.animatedValue as Float
            // map points; total points may be larger when this evaluates
            for (i in 0..totalPoints) {

                val endPoint = try {
                    endingPointsAdjusted[i]
                } catch (ex: IndexOutOfBoundsException) {
                    endingPointsAdjusted.last()
                }

                val startPoint = try {
                    startingPointsAdjusted[i]
                } catch (ex: IndexOutOfBoundsException) {
                    startingPointsAdjusted.last()
                }

                // index may exceed number of render points because line has shrunk in size
                try {
                    renderPoints[i].x = lerp(startPoint.x, endPoint.x, ratio)
                    renderPoints[i].y = lerp(startPoint.y, endPoint.y, ratio)
                } catch (ex: IndexOutOfBoundsException) {
                    break   // in this case, exit the for loop
                }
            }
            doOnUpdate.invoke()
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
                    valueAnimator.removeAllListeners()  // must remove listeners on finish because we reuse animator.
                }
            }
        )

        valueAnimator.duration = 1000
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
        valueAnimator.removeAllListeners() // remove listeners on cancel because we reuse
        return valueAnimator.cancel()
    }
}