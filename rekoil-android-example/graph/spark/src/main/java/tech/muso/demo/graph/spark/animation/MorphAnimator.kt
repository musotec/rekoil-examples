package tech.muso.demo.graph.spark.animation

import android.animation.Animator
import android.animation.TimeInterpolator
import android.util.Log
import androidx.core.animation.addListener
import tech.muso.demo.graph.core.Graphable
import tech.muso.demo.graph.core.NullGraphable
import tech.muso.demo.graph.spark.FifoList
import java.lang.IndexOutOfBoundsException
import kotlin.math.ceil
import kotlin.math.max

class MorphAnimator(graphables: FifoList<Graphable>): GraphableAnimator(graphables) {

    // TODO: look into [Path.approximate(float acceptableError)] for API 26+
    // TODO: handle extra point generation, potentially label points as approximations???
    companion object {

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
    override fun animateToDataSet(
        endingGraphables: List<Graphable>,
        doOnFinish: () -> Unit,
        doOnUpdate: () -> Unit
    ) : Animator {
        if (endingGraphables.isEmpty()) {
            return this
        }

        val startSize = graphables.size
        val endSize = endingGraphables.size
        val totalPointsAnimation = max(startSize, endSize)

        endingPointsAdjusted = if (startSize <= totalPointsAnimation) {
            endingGraphables
//                .also { Log.e("POINT", "endingPoints.size = ${it.size}") }
        } else {
            // amount of points to interpolate
            val stepSize: Int =
                (ceil(startSize.toDouble()/endSize) + 2).toInt() // add 2 for start/end of lerp

            // save list of start point so we can merge; since our lerp excludes the first value
            listOf(endingGraphables[0]) + (
            // iterate from bottom to top joining the lists doing a lerp between points
            endingGraphables.windowed(2, 1).map {
                val start = it[0]
                val end   = it[1]
                start.lerpBetween(end, stepSize)
//                lerp(start, end, stepSize)  // generate a list of the points between
            }).flatten().also { Log.e("POINT", "endingPoints flatten.size = ${it.size}") }    // now flatten our list of lists
        }

        // TODO: refactor out common code.
        startingPointsAdjusted = when {
            endSize < totalPointsAnimation -> {
                // ensure equal size before iterating during the animation by extending extra points to last value.
                graphables + (0..(endingPointsAdjusted.size + 1 - graphables.size)).map { graphables[startSize - 1] }
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
                    graphables.windowed(reducedStart, reducedStart, partialWindows = true).forEach {
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
                    addAll(graphables)
                }
            }
            else -> {
                // TODO: ??? I DON'T REMEMBER WHY THIS WORKS - PROBABLY THE SAME AS A COPY??
                //   DOES THIS EVEN RUN???
                val stepSize: Int =
                    (ceil(endSize.toDouble()/startSize) + 2).toInt() // add 2 for start/end of lerp

                listOf(graphables[0]) + (
                        // iterate from bottom to top joining the lists doing a lerp between points
                        graphables.windowed(2, 1).map {
                            val start = it[0]
                            val end   = it[1]
                            start.lerpBetween(end, stepSize)  // generate a list of the points between
                        }).flatten() // flatten our list of lists (from windowed mapping of lerps)
            }
        }

        val totalPoints = max(endingPointsAdjusted.size, startingPointsAdjusted.size)

        // clear existing points, and we will overwrite them
        graphables.clear()
        for (i in 0..totalPoints) {
            graphables.add(NullGraphable())
        }

        // animate the graphables in the array.
        valueAnimator.addUpdateListener {
            val ratio: Float = it.animatedValue as Float

            // map points; total points may be larger when this evaluates
            for (i in 0..totalPoints) {

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

                // index may exceed number of render points because line has shrunk in size
                try {
                    graphables[i] = startPoint.lerpTo(endPoint, ratio)
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