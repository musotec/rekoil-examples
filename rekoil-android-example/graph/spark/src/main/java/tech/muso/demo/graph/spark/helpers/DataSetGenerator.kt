package tech.muso.demo.graph.spark.helpers

import android.graphics.PointF
import java.lang.Math.sin
import java.lang.Math.toRadians
import kotlin.reflect.KFunction1

fun IntRange.generate(f: (Double) -> Double) : List<Float> {
//    for (i in 0..100) {
//        kFunction1(i/100.0)
//    }

    return this.map { v -> f(v.toDouble()).toFloat() }
}

/**
 * Compose two functions, resulting in f(g(x))
 */
fun compose(f: (Double) -> Double, g: (Double) -> Double): (Double) -> Double = { f(g(it)) }