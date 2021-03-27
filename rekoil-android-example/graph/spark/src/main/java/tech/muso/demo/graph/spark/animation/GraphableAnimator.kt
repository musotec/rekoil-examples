package tech.muso.demo.graph.spark.animation

import android.animation.Animator
import android.animation.ValueAnimator
import tech.muso.demo.graph.core.Graphable
import tech.muso.demo.graph.spark.FifoList

abstract class GraphableAnimator(val graphables: FifoList<Graphable>) : Animator() {
    val valueAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)
    abstract fun animateToDataSet(
        endingGraphables: List<Graphable>,
        doOnFinish: () -> Unit,
        doOnUpdate: () -> Unit
    ): Animator
}