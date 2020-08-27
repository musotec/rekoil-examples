package tech.muso.demo.graph.spark

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.*
import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tech.muso.demo.graph.spark.graph.GraphRangeSelector
import tech.muso.demo.graph.spark.graph.Line
import tech.muso.demo.graph.spark.helpers.FibonacciHeap
import tech.muso.demo.graph.spark.helpers.Node
import tech.muso.demo.graph.spark.helpers.getColorFromAttr
import tech.muso.demo.graph.spark.helpers.makeHeap
import tech.muso.demo.graph.spark.types.HorizontalGuideline
import tech.muso.demo.graph.spark.types.ViewDimensions
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import tech.muso.rekoil.core.Selector
import tech.muso.rekoil.core.launch
import java.util.*

/**
 * TODO: DOKKA FOR THIS CLASS
 */
class LineGraphView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        const val TAG = "SparkLineGraph"
    }

    @Retention(AnnotationRetention.SOURCE)
    annotation class ScrubMode {
        companion object {
            /**
             * The scrubber constant for the selection mode. This will select the nearest real data point.
             */
            var NEAREST = 0

            /**
             * The scrubber constant for the selection mode. This will select the nearest point on the
             * spark line.
             */
            var APPROXIMATE = 1
        }
    }

    var onRangeSelected: () -> Unit = {}
    private val scrubGestureDetector: ScrubGestureDetector
    private val scrubEnabled: Boolean = true

    /**
     * returns the nearest index (into [.adapter]'s data) for the given x coordinate.
     */
    fun getNearestIndex(points: List<Float>, x: Float): Int {
        var index = Collections.binarySearch(points, x)

        // if binary search returns positive, we had an exact match, return that index
        if (index >= 0) return index

        // otherwise, calculate the binary search's specified insertion index
        index = -1 - index

        // if we're inserting at 0, then our guaranteed nearest index is 0
        if (index == 0) return index

        // if we're inserting at the very end, then our guaranteed nearest index is the final one
        if (index == points.size) return --index

        // otherwise we need to check which of our two neighbors we're closer to
        val deltaUp = points[index] - x
        val deltaDown = x - points[index - 1]
        if (deltaUp > deltaDown) {
            // if the below neighbor is closer, decrement our index
            index--
        }

        return index
    }

    val graphSelectionRange = GraphRangeSelector()
    var scrubListener: ScrubStateListener? = null

    interface ScrubStateListener {
        fun onRangeSelected()
        fun onScrubStateChanged(isScrubbing: Boolean)
    }

    private val _scrubListener = object : ScrubGestureDetector.ScrubListener {
        override fun onVibrate() {
            // do vibration
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onScrubStart(x: Float, y: Float) {
            scrubListener?.onScrubStateChanged(true)
            graphSelectionRange.startX = x
            invalidate()
        }

        override fun onScrubbed(x: Float, y: Float) {
            graphSelectionRange.endX = x
            invalidate()
        }

        override fun onScrubEnded() {
            Log.e("Scrub", "onScrubEnded()")
            scrubListener?.onScrubStateChanged(false)
                // only zoom if we have a start & end position.
                // we always have an end position; onScrubbed(x,y) is always called before this
                if (graphSelectionRange.startX != null) {
//                    scrubListener?.onRangeSelected(horizontalScrub)
                    // TODO: return a list of indices so that the adapters for each line can be queried.
                    onRangeSelected()
                }

            invalidate()
        }
    }

    val secondaryGestureDetector: GestureDetector =
        object : GestureDetector(context, object : SimpleOnGestureListener(){
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                // TODO: determine double tap action
                return true
            }
        }) {}

    init {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val handler = Handler()
        scrubGestureDetector = ScrubGestureDetector(_scrubListener, handler, touchSlop, secondaryGestureDetector)
        scrubGestureDetector.setEnabled(scrubEnabled)
        setOnTouchListener(scrubGestureDetector)
    }

    fun onVibrate() {
        this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private val textureBitmap = BitmapFactory.decodeResource(resources, R.drawable.stripe_tile)
    private val textureShader = BitmapShader(textureBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    // create the rekoil scope for the graph
    val graphRekoilScope: RekoilScope = RekoilScope(CoroutineScope(Dispatchers.Main))

    // the graph scope contains a list of adapters for each line
    val lines: Atom<List<LineRekoilAdapter>>
            = graphRekoilScope.atom { listOf<LineRekoilAdapter>() }

    // container for atoms that are defined by the graph to be exposed and observed by all lines
    data class GlobalAtoms(val rekoilScope: RekoilScope): RekoilScope by rekoilScope {
        // TODO: if there are ever read only atoms, use that type instead (don't expose global)
        val viewDimensions: Atom<ViewDimensions> = atom { ViewDimensions(0f,0f) }
        val redrawGraph: Atom<Boolean> = atom { true }
        val globalAxisMin: Atom<Float> = atom { 0f }
        val globalAxisMax: Atom<Float> = atom { 0f }
        val globalAlignGuideline: Atom<HorizontalGuideline> = atom { HorizontalGuideline() }

        // FIXME: currently, this id/size pair is superfluous as the node is stored by the selector
        //   but it is used for debug println of the line id when visualizing the fibonacci heap
        class NodeIdSizePair<T : Comparable<T>> (
            val id: Int,
            val size: T
        ) : Comparable<NodeIdSizePair<T>> {
            override fun compareTo(other: NodeIdSizePair<T>): Int {
                return this.size.compareTo(other.size)
            }

            override fun toString(): String {
                return "[$id]"
            }
        }
        companion object {
            const val DEBUG_FIB_HEAP = false

            // a constant "null" node that is a placeholder for unset atoms
            private val nullNode = Node<NodeIdSizePair<Float>>(NodeIdSizePair(-1, 0f))
        }

        // keep track of line properties using fibonacci heaps, which are amortized O(1) when doing
        // updates very frequently for decrease_key, which is the case here.
        // we need to know the new min/max quickly for large data sets when the current min/max gets superseded
        private val globalMinHeap = makeHeap<NodeIdSizePair<Float>>(property = FibonacciHeap.HeapProperty.MINIMUM)
        private val globalMaxHeap = makeHeap<NodeIdSizePair<Float>>(property = FibonacciHeap.HeapProperty.MAXIMUM)
        private val alignmentAboveHeap = makeHeap<NodeIdSizePair<Float>>(property = FibonacciHeap.HeapProperty.MAXIMUM)
        private val alignmentBelowHeap = makeHeap<NodeIdSizePair<Float>>(property = FibonacciHeap.HeapProperty.MAXIMUM)

        /**
         * Create a new selector and link the [LineRekoilAdapter] so that the global variables
         * can be computed from this data.
         *
         * @return selector which can be released when the line is removed.
         */
        fun link(adapter: LineRekoilAdapter): Selector<*> {
            Log.v("GRAPH", "[${adapter.id}] globals.link()")

            // create a few atoms that will encapsulate data across the execution of this method
            val currentMinNode: Atom<Node<NodeIdSizePair<Float>>> = atom { nullNode }
            val currentMaxNode: Atom<Node<NodeIdSizePair<Float>>> = atom { nullNode }
            val currentAboveNode: Atom<Node<NodeIdSizePair<Float>>> = atom { nullNode }
            val currentBelowNode: Atom<Node<NodeIdSizePair<Float>>> = atom { nullNode }

            // selector for obtaining the line information and updating the globals.
            return selector {
                // get the scale type, which specifies what computation to perform.
                val scaleType = get(adapter.scaleType)

                // when type changes, remove any of our nodes from prior mode
                if (scaleType != Line.ScaleMode.GLOBAL) {
                    if (currentMaxNode.value != nullNode &&
                        currentMinNode.value != nullNode) {
                        // remove our node from each heap and reset to "null"
                        globalMinHeap.delete(currentMinNode.value)
                        globalMaxHeap.delete(currentMaxNode.value)
                        currentMinNode.value = nullNode
                        currentMaxNode.value = nullNode
                        globalAxisMin.value = globalMinHeap.minimum()?.size ?: 0f
                        globalAxisMax.value = globalMaxHeap.maximum()?.size ?: 0f

                        if (DEBUG_FIB_HEAP) {
                            println("MaxHeap (removed node [${adapter.id}]):")
                            globalMaxHeap.visualize()
                            println("MinHeap (removed node [${adapter.id}]):")
                            globalMinHeap.visualize()
                        }
                    }
                }
                // remove alignment nodes
                if (scaleType != Line.ScaleMode.ALIGN_START) {
                    if (currentAboveNode.value != nullNode &&
                        currentBelowNode.value != nullNode) {
                            alignmentAboveHeap.delete(currentAboveNode.value)
                            alignmentBelowHeap.delete(currentBelowNode.value)
                            currentAboveNode.value = nullNode
                            currentBelowNode.value = nullNode
                            globalAlignGuideline.value = HorizontalGuideline(
                                above = alignmentAboveHeap.maximum()?.size ?: 0f,
                                below = alignmentBelowHeap.maximum()?.size ?: 0f
                            )

                        if (DEBUG_FIB_HEAP) {
                            println("AboveHeap (removed node [${adapter.id}]):")
                            alignmentAboveHeap.visualize()
                            println("Belowheap (removed node [${adapter.id}]):")
                            alignmentBelowHeap.visualize()
                        }
                    }
                }

                Log.d("SCALE TYPE", "computing alignment for ${adapter.id}")

                // TODO: handle change from GLOBAL -> ALIGN & remove old values.
                when (scaleType) {
                    Line.ScaleMode.FIT -> {} // does not matter
                    Line.ScaleMode.ALIGN_START, Line.ScaleMode.ALIGN_END -> {
                        val id = adapter.id
                        val (aboveValue, belowValue) = get(adapter.alignmentInfo)

                        // encapsulate in a id/pair adapter
                        val above = NodeIdSizePair(id, aboveValue)
                        val ourAboveNode = currentAboveNode.value
                        val below = NodeIdSizePair(id, belowValue)
                        val ourBelowNode = currentBelowNode.value

                        // important to get these values without get()
                        currentAboveNode.value =
                            if (ourAboveNode == nullNode) {
                                // if node not set, then init by inserting our value
                                alignmentAboveHeap.insert(above)
                            } else {
                                // otherwise update (either increase/decrease)
                                alignmentAboveHeap.updateKey(ourAboveNode, above)
                            }

                        // important to get these values without get()
                        currentBelowNode.value =
                            if (ourBelowNode == nullNode) {
                                // if node not set, then init by inserting our value
                                alignmentBelowHeap.insert(below)
                            } else {
                                // otherwise update (either increase/decrease)
                                alignmentBelowHeap.updateKey(ourBelowNode, below)
                            }

                        if (DEBUG_FIB_HEAP) {
                            println("AboveHeap (update node [$id]):")
                            alignmentAboveHeap.visualize()
                            println("BelowHeap (update node [$id]):")
                            alignmentBelowHeap.visualize()
                        }

                        // update the align axis values from heap after operation
                        globalAlignGuideline.value =
                            HorizontalGuideline(
                                above = alignmentAboveHeap.maximum()!!.size,
                                below = alignmentBelowHeap.maximum()!!.size
                            )

                        // TODO: handle range
//                        if (_maxAlignRangeSeen < alignScaleInfo.range) {
//                            _maxAlignRangeSeen = alignScaleInfo.range
//                            Log.d("SCALE BY", "maxRange: ${alignScaleInfo.range}")
//                            currentAlignment.startDelta = alignScaleInfo.startDelta
//                        }
                    }
                    Line.ScaleMode.GLOBAL -> {
                        val id = adapter.id
                        // encapsulate minimum in a id/pair adapter so we know
                        val min = NodeIdSizePair(id, get(adapter.min) ?: 0f)
                        val ourMinNode = currentMinNode.value

                        // important to get these values without get()
                        currentMinNode.value =
                            if (ourMinNode == nullNode) {
                                // if node not set, then init by inserting our value
                                globalMinHeap.insert(min)
                            } else {
                                // otherwise update (either increase/decrease)
                                globalMinHeap.updateKey(ourMinNode, min)
                            }

                        // update the global axis values from heap after operation
                        globalAxisMin.value = globalMinHeap.minimum()!!.size

                        val max = NodeIdSizePair(id, get(adapter.max) ?: 0f)
                        val ourMaxNode = currentMaxNode.value
                        // important to get these values without get()
                        currentMaxNode.value =
                            if (ourMaxNode == nullNode) {
                                // if node not set, then init by inserting our value
                                globalMaxHeap.insert(max)
                            } else {
                                // otherwise update (either increase/decrease)
                                globalMaxHeap.updateKey(ourMaxNode, max)
                            }

                        globalAxisMax.value = globalMaxHeap.maximum()!!.size

                        if (DEBUG_FIB_HEAP) {
                            println("MaxHeap (update node [$id]):")
                            globalMaxHeap.visualize()
                            println("MinHeap (update node [$id]):")
                            globalMinHeap.visualize()
                        }
                    }
                }
            }
        }
    }

    private val globals = GlobalAtoms(graphRekoilScope)

    init {
        graphRekoilScope.launch {
            selector {
                // invalidate the view if true to redraw
                if (get(globals.redrawGraph)) {
                    invalidate()
                }
            }
        }
    }

    fun remove(line: Line) {
        TODO()
    }

    @OptIn(ExperimentalStdlibApi::class)
    public fun add(lineBuilder: Line.Builder): Line {
        // make a new adapter for the new line
        val lineRekoilAdapter = LineRekoilAdapter(graphRekoilScope, globals)

        // build a new list, to push the update to the lines atom
        val newList = buildList {
            addAll(lines.value)
            // TODO: determine if order of operations below matter
            add(lineRekoilAdapter)
            Log.i("GRAPH", "[${lineRekoilAdapter.id}] add(line) : #${this.size}")
            lineBuilder.setAdapter(lineRekoilAdapter)
        }

        // then update the line
        val line = lineBuilder.build(graphRekoilScope)
        lineRekoilAdapter.line = line

        line.fillTexture = textureShader
//        line.fillPaint.shader = imageShader

        // at the end (so that we make sure the new rekoil adapter is good)
        lines.value = newList

        // alternatively? TODO: REMOVE?
//        lineRekoilAdapters.value.add(lineRekoilAdapter).also { invalidate() }
//        lines.invalidate()

        return line
    }

    override fun invalidate() {
        super.invalidate()
//        Log.w(TAG, "invalidate()")
    }

    // a point on the graph to highlight
    data class DataPoint(
        val todo: String // TODO()
    )

    @ColorInt var bgColor: Int = Color.TRANSPARENT

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
//        Log.d(TAG, "onDraw() - global range (min=${globals.globalAxisMin}, max=${globals.globalAxisMax})")

        canvas.drawColor(bgColor)

        // TODO: maybe speed up the accessors of the actual line on draw.
        // this will get all the clip paths, but leave nothing to draw??
        val clipPaths = lines.value.map { it.line.getClipPath() }
//        val clipPaths = arrayListOf<Path?>()

        // draw the lines from the adapter, filtering the thickest (selected) line to the front
        lines.value.map { it.line }.sortedBy{ line -> line.lineWidth }.forEach {
            it.fillRange(canvas, graphSelectionRange, clipPaths)
            it.draw(canvas)
//            clipPaths.add(it.getClipPath())
        }

        graphSelectionRange.draw(canvas)

        // indicate we have drawn the graph to our atom.
        globals.redrawGraph.value = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        globals.viewDimensions.value =
            ViewDimensions(w, h)
    }

    fun clearLines() {
        lines.value.forEach {
            it.unlink()
//            it.release()  // FIXME: this would be ideal, but we directly inherit scope...
        }
        lines.value = listOf()
    }

    /**
     * Initialize the view. This is intentionally at the bottom so all references resolve.
     */
    init {
        // AVOID HAVING TO SET LAYER TYPE TO SOFTWARE AT ALL COSTS
//        setLayerType(LAYER_TYPE_SOFTWARE, null)
        graphSelectionRange.scrubColor = context.getColorFromAttr(R.attr.colorOnSurface)

        invalidate()
    }

}