package tech.muso.demo.graph.spark.types


/**
 * Class can be improved by implementing code from IntProgressionIterator without doing method call.
 */
class DisjointedIntProgressionIterator(
    val first: IntIterator,
    val second: IntIterator
) : IntIterator() {
    override fun hasNext(): Boolean = first.hasNext() or second.hasNext()
    override fun nextInt(): Int {
        return if (first.hasNext()) first.nextInt() else second.nextInt()
    }
}

public infix fun DisjointedIntProgression.step(step: Int): DisjointedIntProgression {
    return DisjointedIntProgression(first step step, second step step)
}

class DisjointedIntProgression(
    val first: IntProgression,
    val second: IntProgression
): Iterable<Int> {

    override fun iterator(): DisjointedIntProgressionIterator =
        DisjointedIntProgressionIterator(first.iterator(), second.iterator())

}

/**
 * Represents an iterable of a domain on a 1-dimensional line (single axis).
 * Two [LinearDomain] can be subtracted or added to get a composite iterable.
 *
 * TODO: implement minus/add operators correctly and allow chaining.
 */
data class LinearDomain(
    val start: Int = Int.MIN_VALUE,
    val end: Int = Int.MIN_VALUE
) {

    // implement range check explicitly instead of creating IntRange to avoid extra bytecode.
    operator fun contains(index: Int): Boolean = this.start <= index && index <= this.end

    // implement comparator to know if one range contains another
    operator fun compareTo(old: LinearDomain): Int = this.size.compareTo(old.size)

    // FIXME: should be able to represent IntProgression without allocation of a list of integers.
//    operator fun minus(new: PlotBounds): DisjointedIntProgression {
//        val first = (this.start until new.start)
//        val second = (this.end until new.end)
//        return DisjointedIntProgression(first, second)
//    }

    operator fun minus(new: LinearDomain): List<Int> {
        val oldRange = (this.start until this.end)
        val newRange = (new.start until  new.end)
        return oldRange - newRange
    }

    val size = end - start

    inline val isUnset: Boolean get() = start == Int.MIN_VALUE && end == Int.MIN_VALUE
}