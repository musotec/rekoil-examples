package tech.muso.demo.graph.spark.types

data class HorizontalGuideline(
    val above: Float = 0f,      // distance from start point to top of graph    (max)
    val below: Float = 0f       // distance from start point to bottom of graph (min)
) {
    /**
     * Apply a scaling factor to this scale; so that we can quickly compute the effect on the
     * data in the Line without recomputing the data set.
     */
    operator fun times(multiplicand: Float): HorizontalGuideline {
        return HorizontalGuideline(
            above = above * multiplicand,
            below = below * multiplicand
        )
    }

    val range: Float get() = above + below

//    val percent: Float = TODO("percentage from top/bottom")

    /**
     * Override equals to prevent Atom from triggering selectors.
     */
    override fun equals(other: Any?): Boolean {
        if (other is HorizontalGuideline) {
            return above == other.above && below == other.below
        }
        return super.equals(other)
    }
}