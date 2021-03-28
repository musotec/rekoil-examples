package tech.muso.demo.graph.spark


import android.util.Log
import tech.muso.demo.graph.spark.graph.Axis
import tech.muso.demo.graph.spark.graph.Line
import tech.muso.demo.graph.spark.types.*
import tech.muso.demo.graph.core.Graphable
import tech.muso.demo.graph.core.NullGraphable
import tech.muso.rekoil.core.*
import java.util.*

/**
 * Contains Attributes for drawing the line on the graph relative to other lines.
 */
class LineRekoilAdapter(
    rekoilScope: RekoilScope,
    val globals: LineGraphView.GlobalAtoms
) : RekoilScope by rekoilScope, Iterable<Graphable> {

    /**
     * The line that this adapter is linked to.
     */
    lateinit var line: Line

    val id: Int = android.view.View.generateViewId()

    // link globals
    private val globalUpdater = globals.link(this)

    val invalidated = atom { false }.also {
        it.subscribe {
            Log.w("UPDATE", "$id - invalid:$it")
        }
    }

    fun invalidate() {
        invalidated.value = true
    }

    /*
     * Reset the invalidate flag when the line data has been populated.
     */
    internal fun validate() {
        invalidated.value = false
    }

    /**
     * Trigger the LineGraphView to be redrawn so that we can re-render our line.
     */
    fun redraw() {
//        Log.d("POST", "redraw.value = true")
        globals.redrawGraph.value = true
    }

    val data: MinMaxGraphableQueue<Graphable> =
        MinMaxGraphableQueue(this, NullGraphable() as Graphable, capacity = 60)

    val min get() = data.min
    val max get() = data.max

    private val selectionRange = selector {
        // FIXME: needs to be invalidated when data changes... immediately!
        val first = get(start)!!
        val last = get(end)!!

        var startPct = get(globals.globalSelectionStartPct)
        var endPct = get(globals.globalSelectionEndPct)

        // swap points if they are backwards
        if (endPct < startPct) {
            val tmp = startPct
            startPct = endPct
            endPct = tmp
        }

        if (startPct == 0f || endPct == 0f) return@selector LinearDomain(0,0)

        val range = last.x - first.x
        val start = range * startPct + first.x
        val end = range * endPct + first.x

        data.use {
            // use binary search to get the closest point by the x position on the graph.
            // FIXME: x position in data need not be zero aligned, so this is not correct!
            // NOTE: the data is assumed to be a time series, so binary search is valid.
            var closestStartPointIndex = data.binarySearch { pointF -> pointF.x.compareTo(start) }
            var closestEndPointIndex = data.binarySearch { pointF -> pointF.x.compareTo(end) }

            // have to adjust because binarySearch returns negative if index not found
            if (closestStartPointIndex < 0) closestStartPointIndex = -1 * closestStartPointIndex - 1
            if (closestEndPointIndex < 0) closestEndPointIndex = -1 * closestEndPointIndex - 1

            if (data.isEmpty()) return@use null

            // finally, ensure we return a value within the range of our list
            LinearDomain(
                closestStartPointIndex.coerceIn(0 until data.size),
                closestEndPointIndex.coerceIn(0 until data.size)
            )
//                .also { println("~$id LinearDomain[${data.size}] = $it") }
        }
    }

    val selectionStartPoint = selector {
        val range = get(selectionRange) ?: return@selector null
        if (data.isEmpty()) null else data[range.start.coerceIn(0, data.size - 1)]
    }

    val selectionEndPoint = selector {
        val range = get(selectionRange) ?: return@selector null
        if (data.isEmpty()) null else data[range.end.coerceIn(0, data.size - 1)]
    }

    val trapezoidalIntegralApprox = selector {
        data.use {
            val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@use 0.0

            // avoid empty case
            if (closestEndPointIndex - closestStartPointIndex < 2) return@use null

//        Log.w("SELECTOR", "[$id] closestStart: $closestStartPointIndex, closestEnd: $closestEndPointIndex")

            val rightHandApprox =
                data.subList(closestStartPointIndex, closestEndPointIndex).map { p -> p.y }
                    .reduce { fl, acc ->
                        fl + acc
                    }
            val leftHandApprox =
                data.subList(closestStartPointIndex + 1, closestEndPointIndex + 1)
                    .map { p -> p.y }
                    .sum()
            (rightHandApprox + leftHandApprox) / 2f
        }
    }

    val deltaX: Selector<Double?> = selector {
        // FIXME: assumes points equally spaced; x/time axis not actually implemented.
        data.use {
            val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@use 0.0
            if (data.isEmpty()) null
            else (data[closestEndPointIndex.coerceAtMost(data.size - 1)].x - data[closestStartPointIndex].x).toDouble()
        }
    }

    val mean: Selector<Double?> = selector {
        data.use {
            val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@use 0.0
            data.subList(closestStartPointIndex, closestEndPointIndex).run {
                sumByDouble { it.y.toDouble() } / size
            }
        }
    }

    val variance: Selector<Double?> = selector {
        data.use {
            val mean = get(mean) ?: return@use 0.0
            val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@use 0.0
            println("~$id [$closestStartPointIndex, $closestEndPointIndex] : ${data.size}")
            data.subList(closestStartPointIndex, closestEndPointIndex).run {
                map {
                    val v = (it.y - mean)
                    return@map v * v
                }.sum() / (size - 1)
            }
        }
    }

    val start: Selector<Graphable?> = selector {
//        val data = get(data).also { if (it.isEmpty()) return@selector null }
//        data.first().also {
//            // and update the axis while we are here
//            axisArray.first().position = it.y
//        }
        get(data.start)
    }

    val end: Selector<Graphable?> = selector {
//        val data = get(data).also { if (it.isEmpty()) return@selector null }
//        data.last()
        get(data.end)
    }

    val axisArray: MutableList<Axis> = mutableListOf<Axis>().apply {
        add(Axis(0f, true))
    }

    val scaleType: Atom<Line.ScaleMode> = atom {
        Line.ScaleMode.GLOBAL
    }

    val connectPoints: Atom<Boolean> = atom { true }
    val fill: Atom<Boolean> = atom { false }
    val clip: Atom<Boolean> = atom { false }

    val alignmentInfo: Atom<HorizontalGuideline> = atom {
        HorizontalGuideline()
    }

    init {
        selector {
            // if data changes, we need to invalidate our min/max, etc
//            val data = get(data).also { if (it.isEmpty()) return@selector null }
//            get(data)
            val verticalRatio = 1f
//            val start = data.first()
//            val end = data.last()
            val start = get(start)!!
            val end = get(end)!!
            val min = get(min)
            val max = get(max)
            when (get(scaleType)) {
                Line.ScaleMode.ALIGN_START, Line.ScaleMode.ALIGN_END -> {
                    alignmentInfo.value = (HorizontalGuideline(
                        max - start.y,
                        start.y - min
                    ) * verticalRatio).also {
                        Log.i("ALIGN INFO", "#$id - align info: $it")
                    }
                }
                else -> {}
            }
        }
    }

    override fun iterator() = data.iterator()

    fun unlink() {
        globalUpdater.release()
    }

//    fun setBoundsFromXCoordinates(start: Float, end: Float, totalWidth: Int) {
//        val step = totalWidth.toFloat()/bounds.size
//        val startIndexAdjusted = start / step
//        val newStart = startIndexAdjusted.roundToInt() + bounds.start
//        val endIndexAdjusted = end / step
//        val newEnd = endIndexAdjusted.roundToInt() + bounds.start
//        // coerce into proper range. TODO: verify the math.
//        val dataSize = data.size
//        val s = newStart.coerceIn(0, dataSize)
//        val e = newEnd.coerceIn(0, dataSize)
//        if (s == e) return  // todo: if within certain resolution range. announce disable of zoom
//        bounds = LinearDomain(s, e)
//        return
//    }

}