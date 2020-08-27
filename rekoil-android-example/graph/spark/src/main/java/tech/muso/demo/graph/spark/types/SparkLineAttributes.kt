package tech.muso.demo.graph.spark.types

/**
 * Attributes of a spark line on the graph.
 *
 * TODO: add extra variables like margin top, padding, etc.
 *
 * @param start Pair<Int, T> of line's start, [Pair.first] is the index, [Pair.second] is the value
 * @param end Pair<Int, T> of line's end, [Pair.first] is the index, [Pair.second] is the value
 */
data class SparkLineAttributes<out T : Number>(
    val start: Pair<Int, T>? = null,
    val end: Pair<Int, T>? = null,
    val minMaxPair: DataRange,
    val scale: Float = 1f
) {
    val min = minMaxPair.min
    val max = minMaxPair.max
    val range = minMaxPair.range

    /*
     * Override times operator to allow for scale adjustment.
     * TODO: verify that this makes sense.
     */
    operator fun times(scale: Float): SparkLineAttributes<T> {
        return SparkLineAttributes(start, end, minMaxPair, scale)
    }
}

