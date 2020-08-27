package tech.muso.demo.graph.spark.types

import java.io.Serializable

data class DataRange(
    val min: Float,
    val max: Float) : Serializable {

    // initialize with min at +inf and max at -inf for comparisons to "unset"
    constructor() : this(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

    val range: Float get() = max - min

    override fun equals(other: Any?): Boolean {
        return if (other !is DataRange) false
        else min == other.min && max == other.max
    }

    operator fun times(vertialRatio: Float): DataRange {
        return DataRange(min * vertialRatio, max * vertialRatio)
    }
}

// extension properties to default min/max to unset value MinMaxPair properties
inline val DataRange?.min: Float get() = this?.min ?: Float.POSITIVE_INFINITY
inline val DataRange?.max: Float get() = this?.max ?: Float.NEGATIVE_INFINITY