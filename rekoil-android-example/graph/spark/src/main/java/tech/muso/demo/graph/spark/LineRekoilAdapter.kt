package tech.muso.demo.graph.spark

import android.graphics.PointF
import android.util.Log
import tech.muso.demo.graph.spark.graph.Axis
import tech.muso.demo.graph.spark.graph.Line
import tech.muso.demo.graph.spark.types.*
import tech.muso.rekoil.core.*
import kotlin.math.abs

/**
 * Contains Attributes for drawing the line on the graph relative to other lines.
 */
class LineRekoilAdapter(
    rekoilScope: RekoilScope,
    val globals: LineGraphView.GlobalAtoms
) : RekoilScope by rekoilScope, Iterable<PointF> {

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

    val data: Atom<List<PointF>> = atom<List<PointF>> {
        listOf<PointF>()
    }

    private val selectionRange = selector {
        val data = get(data)
        var startPct = get(globals.globalSelectionStartPct)
        var endPct = get(globals.globalSelectionEndPct)

        // swap points if they are backwards
        if (endPct < startPct) {
            val tmp = startPct
            startPct = endPct
            endPct = tmp
        }

        if (startPct == 0f || endPct == 0f) return@selector LinearDomain(0,0)

        val range = data.last().x - data.first().x
        val start = range * startPct + data.first().x
        val end = range * endPct + data.first().x

        var closestStartPointIndex = data.binarySearch { pointF -> pointF.x.compareTo(start) }
        var closestEndPointIndex = data.binarySearch { pointF -> pointF.x.compareTo(end) }

        // have to adjust because binarySearch returns negative if index not found
        if (closestStartPointIndex < 0) closestStartPointIndex = -1 * closestStartPointIndex - 1
        if (closestEndPointIndex < 0) closestEndPointIndex = -1 * closestEndPointIndex - 1

        LinearDomain(closestStartPointIndex, closestEndPointIndex)
    }

    val selectionStartPoint = selector {
        val range = get(selectionRange) ?: return@selector null
        get(data)[range.start]
    }

    val selectionEndPoint = selector {
        val range = get(selectionRange) ?: return@selector null
        get(data)[range.end.coerceIn(0, data.value.size - 1)]
    }

    val trapezoidalIntegralApprox = selector {
        val data = get(data)
        val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@selector null

        // avoid empty case
        if (closestEndPointIndex - closestStartPointIndex < 2) return@selector null

        Log.w("SELECTOR", "[$id] closestStart: $closestStartPointIndex, closestEnd: $closestEndPointIndex")

        try {
            val rightHandApprox =
                data.subList(closestStartPointIndex, closestEndPointIndex).map { p -> p.y }
                    .reduce { fl, acc ->
                        fl + acc
                    }
            val leftHandApprox =
                data.subList(closestStartPointIndex + 1, closestEndPointIndex + 1).map { p -> p.y }
                    .sum()

            return@selector (rightHandApprox + leftHandApprox) / 2f
        } catch (ignored: Exception) {
            return@selector null
        }
    }

    val deltaX: Selector<Double?> = selector {
        // FIXME: assumes points equally spaced; x/time axis not actually implemented.
        val data = get(data)
        val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@selector null
        (data[closestEndPointIndex].x - data[closestStartPointIndex].x).toDouble()
    }

    val mean: Selector<Double?> = selector {
        val data = get(data)
        val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@selector null
        data.subList(closestStartPointIndex, closestEndPointIndex).run {
            sumByDouble { it.y.toDouble() } / size
        }
    }

    val variance: Selector<Double?> = selector {
        val data = get(data)
        val mean = get(mean) ?: return@selector null
        val (closestStartPointIndex, closestEndPointIndex) = get(selectionRange) ?: return@selector null
        data.subList(closestStartPointIndex, closestEndPointIndex).run {
            map {
                val v = (it.y - mean)
                return@map v*v
            }.sum() / (size - 1)
        }
    }

    val start: Selector<PointF?> = selector {
        get(data).first().also {
            // and update the axis while we are here
            axisArray.first().position = it.y
        }
    }

    val end: Selector<PointF?> = selector {
        get(data).last()
    }

    val max: Selector<Float?> = selector {
        get(data).maxByOrNull { point -> point.y }?.y ?: 0f
    }

    val min: Selector<Float?> = selector {
        get(data).minByOrNull { point -> point.y }?.y ?: 0f
    }

    val axisArray: MutableList<Axis> = mutableListOf<Axis>().apply {
        add(Axis(0f, true))
    }

//    val lineAttributes: Selector<SparkLineAttributes<Float>?> = selector {
//        Log.v("SELECTOR", "[$id] lineAttributes.get(data)")
//        val data = get(data)
//        Log.v("SELECTOR", "[$id] lineAttributes.get(data) [data.size=${data.size}]")
//
//        return@selector if (data.isEmpty())
//            SparkLineAttributes()
//        else
//            SparkLineAttributes<Float>(
//                start = Pair(0, data[0].y),
//                end = Pair(data.size - 1, data[data.size - 1].y),
//                minMaxPair = dataRange ?: DataRange(),
//                scale = 1f
//            ).also {
//                Log.i("LINE ATTRS", "[$id] lineAttributes.update() [lineAttributes=$it]")
//            }
//    }

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
            val data = get(data)   // if data changes, we need to invalidate our min/max, etc
            val verticalRatio = 1f
            val start = data.first()
            val end = data.last()
            val min = get(min)!!
            val max = get(max)!!
            when (get(scaleType)) {
                Line.ScaleMode.ALIGN_START, Line.ScaleMode.ALIGN_END -> {
                    alignmentInfo.value = (HorizontalGuideline(
                        max - start.y,
                        start.y - min
                    ) * verticalRatio).also {
                        Log.i("ALIGN INFO", "#$id - align info: $it")
                    }
                }
            }
        }
    }

    var count: RekoilContext.ValueNode<Int?> = selector {
        get(data).size
    }

    val iterator = selector {
        get(data).mapIndexed { i, point -> PointF(i.toFloat(), data.value[i].y) }.iterator()
    }

    override fun iterator(): Iterator<PointF> {
//        if (bounds.isUnset) return listOf<PointF>().iterator()
        // use int progression over the bounds to control step by; then map to our data.
//        return (bounds.start until bounds.end step resolution).map { i -> PointF(i.toFloat(), data.value[i].y) }.iterator()
        return iterator.value ?: listOf<PointF>().iterator()
    }

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