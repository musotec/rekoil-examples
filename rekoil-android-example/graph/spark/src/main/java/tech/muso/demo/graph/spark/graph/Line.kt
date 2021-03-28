package tech.muso.demo.graph.spark.graph

import android.graphics.*
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import kotlinx.coroutines.CoroutineScope
import tech.muso.demo.graph.core.CandleGraphable
import tech.muso.demo.graph.spark.types.annotation.FillType
import tech.muso.demo.graph.spark.types.annotation.ClipType
import tech.muso.demo.graph.spark.LineGraphView
import tech.muso.demo.graph.spark.LineRekoilAdapter
import tech.muso.demo.graph.spark.paint.shaders.HueGradientShader
import tech.muso.demo.graph.spark.types.*
import tech.muso.demo.graph.core.Graphable
import tech.muso.demo.graph.core.PointGraphable
import tech.muso.demo.graph.spark.FifoList
import tech.muso.demo.graph.spark.animation.GraphableAnimator
import tech.muso.demo.graph.spark.animation.MorphAnimator
import tech.muso.demo.graph.spark.animation.SlideAnimator
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import tech.muso.rekoil.core.launch
import tech.muso.rekoil.ktx.getValue
import tech.muso.rekoil.ktx.setValue
import java.lang.IndexOutOfBoundsException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max

data class Line private constructor(private val rekoilScope: RekoilScope, private val builder: Builder) {

    enum class ScaleMode {
        FIT,            // scale the line to the view boundaries
        GLOBAL,         // scale the line relative to the graph
        ALIGN_START,    // scale the line relative to the largest range
        ALIGN_END
    }

    var identifier: Any = 0 // TODO: remove
    val id = View.generateViewId()  // generate an id for this line, such that it does not resolve to any view ids

    val adapter: LineRekoilAdapter = builder.adapter

    // TODO: investigate custom delegates
    private val globalAxisMin: Float
            by adapter.globals.globalAxisMin

    private val globalAxisMax: Float
            by adapter.globals.globalAxisMax

    private val globalAlignGuideline: HorizontalGuideline
            by adapter.globals.globalAlignGuideline

    // minimum and maximum values on the graph
    public val max: Float get() = adapter.max.value ?: 0f
    public val min: Float get() = adapter.min.value ?: 0f
    public val start: Graphable get() = adapter.start.value ?: PointGraphable(0f,0f)
    public val end: Graphable get() = adapter.end.value ?: PointGraphable(0f,0f)
    public val range: Float get() = max - min

    init {
        rekoilScope.apply {

            // selector to handle render invalidation
            selector {
                if (get(adapter.invalidated)) {
                    Log.e("SELECTOR", "#${identifier} invalidate.populateRenderingPath()")
                    populateRenderingPath() // populate path on invalidation to generate the next frame
                }
            }

            // selector to invalidate when view dimensions change
            selector {
                // if view dimensions changed
                get(adapter.globals.viewDimensions)
                Log.i("SELECTOR", "#${identifier} viewDimens.populateDataPath()")
                populateRenderingPath() // recompute rendering path immediately when view resized
            }

            // selector to handle data invalidation
            val dataSelector = selector {   // TODO: remove. this should be obsolete
                // trigger on the following atoms
//                get(adapter.data) // if data changes, start animation
                get(adapter.start)
                get(adapter.end)
                // work to do for rendering change
                populateDataPath()  // update the data for the animation

                // NOTE: do not update rendering path, else animation completes instantly.
                Log.i("SELECTOR", "#${identifier} populateDataPath()")
            }

            // selector that invalidates any time the graph scale changes
            // TODO: select type/only invalidate if type matches scale change
            val globalScaleSelector = selector {
                get(adapter.scaleType)
                get(adapter.globals.globalAxisMin)
                get(adapter.globals.globalAxisMax)
                val globalAlignScale = get(adapter.globals.globalAlignGuideline)

                Log.d(
                    "SELECTOR",
                    "#${identifier} populateRenderingPath() [range:$range;globalAlign:$globalAlignScale]"
                )
            }

            // selector for when to animate the rendering path.
            val renderAnimationSelector = selector {
                // if data ever changes.
                val data = get(dataSelector)
                // if scale ever changes.
                val scale = get(globalScaleSelector)
                get(adapter.min)
                get(adapter.max)
//                get(adapter.end)

                Log.w("SELECTOR", "#${identifier} animateTransition - scaleSelector: $scale")


                // then re-render the path, animating the transition
                animateTransition {
                    Log.i("AnimateTransition", "#$identifier finished animation")
                    populateRenderingPath()
                } // animate transition (data changed)
            }
        }
    }


    // internal atoms for rendering variables.
    val lineColorAtom: Atom<Int> = rekoilScope.atom { builder.lineColor }
    var lineColor: Int by lineColorAtom

    private val lineWidthAtom: Atom<Float> = rekoilScope.atom { builder.lineWidth }
    var lineWidth: Float by lineWidthAtom

    private val fillTypeAtom: Atom<Int> = rekoilScope.atom { FillType.TOWARD_ZERO }
    var fillType: Int by fillTypeAtom
    init {
        rekoilScope.selector {
            // TODO: rethink if this is necessary when fill types are changeable
            if (get(fillTypeAtom) != FillType.NONE) {
//                    populateRenderingPath()
            }
        }
    }

    private val fillColorAtom: Atom<Int> = rekoilScope.atom { builder.lineColor }
    var fillColor: Int by fillColorAtom
    init {
        rekoilScope.selector {
            val fillType = get(fillTypeAtom)
            if (fillType != FillType.NONE) {
                generateFillPaint()
            }
        }
    }

    class Builder {
        @ColorInt internal var lineColor: Int = 0
        internal var lineWidth = 4f
        internal lateinit var adapter: LineRekoilAdapter

        fun setColor(@ColorInt color: Int): Builder {
            lineColor = color
            return this
        }

        internal fun build(lineGraphView: LineGraphView): Line {
            return build(lineGraphView.graphRekoilScope)
        }

        internal fun build(rekoilScope: RekoilScope): Line {
            return Line(rekoilScope, this)
        }

        internal fun build(coroutineScope: CoroutineScope): Line {
            return this.build(RekoilScope(coroutineScope))
        }

        /**
         * Must be called.
         */
        internal fun setAdapter(lineRekoilAdapter: LineRekoilAdapter): Builder {
            this.adapter = lineRekoilAdapter
            return this
        }
    }

    var fillTexture: BitmapShader? = null

    var scaleMode: ScaleMode by adapter.scaleType
    private var viewDimensions: ViewDimensions by adapter.globals.viewDimensions

    private var drawLines: Boolean by adapter.connectPoints

    @ClipType
    var clipType: Int =
        ClipType.CLIP_ABOVE

    // NOTE: points for paths are relative to the min and max of the graph; scaled from 0 to 1
    private var renderPath =
        Path()  // the current path drawn on the screen.
    private var fillPath =
        Path()  // the current path for the entire fill area.
    private var clipPath =
        Path()  // the current path for clipping other lines

    val resolutionX = viewDimensions.width
    val resolutionY = viewDimensions.height

    /*
     *  An array of points formatted for use by Canvas.drawLines(pts, paint)
     *  expected form [x0 y0 x1 y1 x1 y1 x2 y2 ... xn yn]
     *
     *  x0 y0 x1 y1 defines the first line, from the 1st to 2nd point
     *  x1 y1 x2 y2 defines the next line, from the 2nd to 3rd point
     *  etc.
     */
    private var renderLinesFloatArray: FloatArray
            = FloatArray(0)

    /*
     * An array of points to be used by Canvas.drawPoints(pts, paint)
     * expected form [x0 y0 x1 y1 x2 y2 ... xn yn]
     */
    private var renderPointsFloatArray: FloatArray
            = FloatArray(0)

    private var renderCandlesArray: Array<CandleGraphable?> = emptyArray()

    init {
        rekoilScope.launch {
            // selector logic for handling line paint attributes
            selector {
                val color = get(lineColorAtom)
                val width = get(lineWidthAtom)
                linePaint.color = color
                linePaint.strokeWidth = width
                fillPaint.color = color
                fillPaint.alpha = 0x8f  // have to update alpha any time color is changed.

                // update axis paints
                adapter.axisArray.forEach {
                    it.paint.apply {
                        this.color = color
                        this.alpha = 0x7f
                        this.strokeWidth = width/2
                    }
                }

//                Log.i("LINE", "#$identifier - set line color: " +
//                        "${Integer.toHexString(color)}, width: $width")

                adapter.redraw()
            }

            // selector for monitoring the view size
            selector {
                // when view dimensions change, update the size of the float array
                val width = get(adapter.globals.viewDimensions).width.toInt()
                // if our width is larger; render float array has form ([x0 y0 x1 y1 ...])
                if (width * 4 > renderLinesFloatArray.size) {
                    renderLinesFloatArray = FloatArray(width * 4)
                    renderPointsFloatArray = FloatArray(width * 2)  // also update points
                    renderCandlesArray = arrayOfNulls(width)
                }
            }
        }
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // TODO: extension rekoil.fun Any.selector(scope) { apply }
        .apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = lineWidth
            strokeCap = Paint.Cap.ROUND

            val cornerRadius = 4f

            // TODO: validate the corner radius range; use annotation for range spec.
            if (cornerRadius != 0f) {
                pathEffect = CornerPathEffect(cornerRadius)
            }
        }

    private val fillPaint = Paint().apply {
        set(linePaint)
        style = Paint.Style.FILL
        color = fillColor
        alpha = 0x8f
        strokeWidth = 0f
    }

    private val candlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // TODO: extension rekoil.fun Any.selector(scope) { apply }
        .apply {
            style = Paint.Style.FILL_AND_STROKE
            color = lineColor
            strokeWidth = 2f
            strokeCap = Paint.Cap.SQUARE
        }


    private val rawGraphableList get() = adapter.data
    private var postAnimationGraphables = FifoList<Graphable>(rawGraphableList.capacity)

    // TODO: support specification of Animation type
    private val animator: GraphableAnimator = MorphAnimator(postAnimationGraphables)
//    private val animator: GraphableAnimator = SlideAnimator(postAnimationGraphables)

    private var animateFrame = true

    private fun animateTransition(speed: Float = 1f, onAnimationFinished: () -> Unit) {
        if (rawGraphableList.size == 0 || postAnimationGraphables.size == 0) {
            Log.e("AnimateTransition", "#$identifier ANIMATION FINISHED EARLY")
            onAnimationFinished()
            return
        }

        // TODO: RATE LIMIT THIS ANIMATION FUNCTION!! ?
        if (animator.isStarted) {
            Log.e("AnimateTransition", "#$identifier currentAnimator?.cancel() -> expect invoke doOnCancel()")
            animator.cancel()
        }

        animator.duration = (300L * speed).toLong()

//        Log.w("AnimateTransition", "#$identifier creating path animation: Assumptions [ globalAlignScale=$globalAlignGuideline; max=$max, min=$min, start=$start, end=$end; verticalRatio=$verticalRatio; ]")
//        Log.w("AnimateTransition", "#$identifier creating path animation: RenderStart [ start=${postAnimationGraphables.first()}, end=${postAnimationGraphables.last()} ]")
//        Log.w("AnimateTransition", "#$identifier creating path animation: RawTransformDestination [ start=${rawGraphableList.first()}, end=${rawGraphableList.last()} ]")

        // update the capacity in case it changed
        postAnimationGraphables.capacity = rawGraphableList.capacity

        rawGraphableList.useLock {  // use lock during mutation of graphables
            // in a new array, compute the final path
            val computedFinalPath = scaleGraphables(rawGraphableList)
            if (computedFinalPath.isNotEmpty()) Log.w(
                "AnimateTransition",
                "#$identifier creating path animation: RenderEnd [ start=${computedFinalPath.first()}, end=${computedFinalPath.last()} ]"
            )

            // abort animation if start = end (nothing to do)
            if (computedFinalPath.first() == postAnimationGraphables.first() &&
                computedFinalPath.last() == postAnimationGraphables.last()
            ) {
                // TODO: this equality check is not valid if middle points move // FIXME outside of demo
                return@useLock // exit before starting animator.
            }

            // if our rendered points list has bad info (scale not yet set) then don't animate
            if (postAnimationGraphables.first().y.isNaN() || postAnimationGraphables.last().y.isNaN()) {
                onAnimationFinished()   // just populate
                return@useLock
            }

            // TODO: improve readability by using builder pattern.
            animator.animateToDataSet(computedFinalPath, onAnimationFinished) {
                // do on animation frame
                rasterize(postAnimationGraphables)
                rasterizeClipFillPath(postAnimationGraphables)
                adapter.redraw()
            }.start()
        }
    }

    private fun populateDataPath() { // TODO: remove this. not needed as we use the array directly?
//        adapter.let {
//            /*
//             * Generate the list of raw data points for reference during animation.
//             */
//            rawGraphableList.clear()
//
//            // add all the points from the adapter
//            adapter.forEachIndexed { index, graphable ->
//                rawGraphableList.add(graphable)
//            }
//
//            if (rawGraphableList.isNotEmpty()) {
//                Log.d("LINE", "#$identifier Populate Data Points: start=${rawGraphableList.first()}, end=${rawGraphableList.last()}")
//            }
//        }
    }

    /*
     * Generate the rendering path to be shown on the graph.
     * This fills the rendering path immediately with the final data in the raw data set.
     */
    private fun populateRenderingPath() {
        // do full reset since this is called only when the Path is finished.
        // the final path is smaller than when animated
        renderPath.reset()
        fillPath.reset()
        Log.e("Line", "#$identifier populateRenderingPath()")
        // update the capacity in case it changed
        postAnimationGraphables.capacity = rawGraphableList.capacity
        rawGraphableList.useLock {  // use lock during mutation of graphables
            scaleGraphables(rawGraphableList, postAnimationGraphables)
            rasterize(postAnimationGraphables)
            rasterizeClipFillPath(postAnimationGraphables)   // populate path when finished for fill
            adapter.validate()
            adapter.redraw()
        }
    }

    private var renderCount = 0    // should always equal rawDataPoints-1

    private fun rasterize(animatedGraphables: MutableList<Graphable>) {
        if (animatedGraphables.isEmpty() || renderLinesFloatArray.isEmpty() || rawGraphableList.isEmpty()) return

        val lineCount = rawGraphableList.size - 1
        var index = 0
        var j = 0
        renderCount = 0    // used to compute number of items to render

        animatedGraphables.toTypedArray()
        val isCandle = rawGraphableList[0] is CandleGraphable
        try {
            for (i in 0 until lineCount) {
                val startPoint = animatedGraphables[i]
                val endPoint = animatedGraphables[i + 1]
                val x0 = startPoint.x
                val y0 = startPoint.y
                val x1 = endPoint.x
                val y1 = endPoint.y
                renderLinesFloatArray[index++] = x0
                renderPointsFloatArray[j++] = x0

                renderLinesFloatArray[index++] = y0
                renderPointsFloatArray[j++] = y0

                renderLinesFloatArray[index++] = x1
                renderLinesFloatArray[index++] = y1
                renderCount++

                renderCandlesArray[i] = animatedGraphables[i] as? CandleGraphable

//                if (isCandle) {
//                    val c =  renderCandlesArray[i] ?: rawGraphableList[i] as CandleGraphable
////                    val c = renderedCandles[i]
////                    val c = rawGraphableList[i] as CandleGraphable
////                    println("Candle [$range : y=$y0] $c")
//                    val d = 0// c.y - y0
//                    renderCandlesArray[i] = c
//                        CandleGraphable(x0, c.open + d, c.close + d, c.high + d, c.low + d, c.volume)
////                        CandleGraphable(x0, y0 - 50, y0 + 50, y0 + 100, y0 - 100, 100)
//                }
            }
            // add last candle; missed during line drawing (skips last index; 2 points in a line)
            if (isCandle) renderCandlesArray[lineCount] = animatedGraphables.last() as? CandleGraphable
        } catch (ex: IndexOutOfBoundsException) {
            return  // can occur when converting and array size changes asynchronously
        }
    }

    /**
     * Populate the rendering path and prepare it to be drawn.
     * TODO: refactor - drawing by path is sub-optimal
     *
     * @param renderPoints the list of points to render.
     */
    private fun rasterizeClipFillPath(renderPoints: MutableList<Graphable>) {
        renderPath.rewind() // TODO: determine speed of reset() vs rewind()
        fillPath.rewind()

//        Log.d("ComputeRender", "#$identifier convertPointsToRenderPath() max=$max, min=$min, start=$start, end=$end; verticalRatio=$verticalRatio")

        // generate the rendering path.
        renderPoints.forEachIndexed { i, point ->

            if (i == 0) {
                renderPath.moveTo(point.x, point.y)
            } else {
                renderPath.lineTo(point.x, point.y)
            }

            // if last index, perform special case to close the fill area.
            if (i == renderPoints.size - 1) {
                if (fillType != FillType.NONE && fillType != FillType.ALL) {
                    fillPath.set(renderPath)

//                    Log.d("ComputeRender", "#$identifier generating fillPath start=${renderPoints[0]}, end=${point}")
                }

                val fillFlags = fillType
                when(fillFlags) {
                    FillType.DOWN -> {
                        fillPath.lineTo(point.x, viewDimensions.width)
                        fillPath.lineTo(0f, viewDimensions.height) // TODO: start.x
                    }
                    FillType.UP -> {
                        fillPath.lineTo(point.x, 0f)
                        fillPath.lineTo(0f, 0f)
                    }
                    FillType.TOWARD_ZERO -> {
                        // TODO: find zero on graph and bisect; avoid suspicious axis assumptions
                        fillPath.lineTo(point.x, adapter.axisArray[0].renderPointsOut[0].y)
                        fillPath.lineTo(0f, adapter.axisArray[0].renderPointsOut[0].y)
                    }
                    FillType.ALL -> {
                        fillPath.moveTo(0f, 0f)
                        fillPath.lineTo(viewDimensions.height, 0f)
                        fillPath.lineTo(viewDimensions.width, viewDimensions.height)
                        fillPath.lineTo(0f, viewDimensions.width)
                        fillPath.close()
                    }
                    else -> {

                    }
                }

                if (clipType has ClipType.CLIP_ABOVE) {
                    clipPath.set(renderPath)
                    clipPath.lineTo(point.x, 0f)
                    clipPath.lineTo(0f, 0f) // TODO: start.x
                }

                // TODO: support clip above and below.
                if (clipType has ClipType.CLIP_BELOW) {
                    clipPath.set(renderPath)
                    clipPath.lineTo(point.x, viewDimensions.height)
                    clipPath.lineTo(0f, viewDimensions.height) // TODO: start.x
                }
            }
        }
    }

    // TODO: REFACTOR
    var verticalRatio = 1f
        set(value) {
            // don't do an animation (or anything) if we already are at this value
            if (field != value) {

                val amount = abs(field - value)

                // using precision allows for us to avoid "wiggles" in graph due to very slight changes.
                val precision = 0.02f // only adjust for values within precision; TODO: allow specification of this??
                if (amount < precision) {
                    return
                }

                Log.e("Line", "#$identifier verticalRatio.set($field -> $value) delta=${field-value}")

//            alignScaleInfo *= (value/field)
                field = value
            }

//        animateTransition(1f) {
//            populateRenderingPath() // vertical ratio changed
//        }
        }

    val topMargin = 0.05f
    val bottomMargin = 0.05f

    /**
     * Populate the rendering path based on the state of the animation.
     */
    private fun scaleGraphables(
        rawGraphableList: List<Graphable>,
        preRenderGraphableListOut: MutableList<Graphable> = arrayListOf()
    ): List<Graphable> {
        if (rawGraphableList.isEmpty()) return preRenderGraphableListOut

        val adjustedViewHeight = viewDimensions.height * (1f - topMargin - bottomMargin)
        val horizontalPadding = 10f
        val xrange: Float = max(rawGraphableList.size.toFloat(), 2f) - 1f // range is always at least 2 points (in a line)
//        val xscale = (viewDimensions.width + lineWidth) / xrange
        val xscale = (viewDimensions.width - (horizontalPadding * 2)) / xrange
//        println("xrange: $xrange [${adapter.data.size.toFloat()} : ${rawGraphableList.size.toFloat()}]")

        val yscale =  verticalRatio * (adjustedViewHeight - 2 * lineWidth) /
                if (scaleMode == ScaleMode.FIT) range else (globalAlignGuideline.range)

        val midpoint = if (scaleMode == ScaleMode.FIT) (0.7f) else (globalAlignGuideline.below/globalAlignGuideline.range)

        val verticalRatioInset = (adjustedViewHeight * midpoint * (1f - verticalRatio))  // px inset based on vertical fill ratio
        val ytranslation = min * yscale

        val centerPxY = viewDimensions.height/2
        val centerPtY = max - range/2

        val adjust = (globalAlignGuideline.below + min)

        // adjust y scale to the height of the view
        fun fitVertically(y: Float) = adjustedViewHeight - (y * yscale) + ytranslation + topMargin * viewDimensions.height

        val startPointPreAdjust = fitVertically(start.y)
        val adjustPoint = fitVertically(adjust)
        val adjustPx = adjustPoint - (startPointPreAdjust) - verticalRatioInset
        val logging = false
        @Suppress("ConstantConditionIf")
        if (logging) {
            when(scaleMode) {
                ScaleMode.GLOBAL -> {
                    Log.e("LineScale", "$id GLOBAL Scale: range(min=$globalAxisMin,max=$globalAxisMax)")
                }
                ScaleMode.FIT -> {
                    Log.e("LineScale",  "$id FIT Scale: viewDimensions.height: ${viewDimensions.height}; yscale=$yscale; ytranslation=$ytranslation")
                }
                ScaleMode.ALIGN_START -> {
                    Log.e("LineScale", "$id ALIGN_START Scale: range=${globalAlignGuideline.range}, delta=${startPointPreAdjust} => adjust = ${adjustPx}; min: $min, max: $max")
                }
            }
        }

        val globalScaleVertical = (viewDimensions.height - 2 * lineWidth) / (globalAxisMax - globalAxisMin)
        fun applyScaling(y: Float): Float {
            return when (scaleMode) {
                ScaleMode.FIT -> {
                    fitVertically(y)
                }
                ScaleMode.GLOBAL -> {
                    viewDimensions.height - (y * globalScaleVertical) + (globalAxisMin * globalScaleVertical)
                }
                ScaleMode.ALIGN_START -> {
                    fitVertically(y) + adjustPx
                }
                ScaleMode.ALIGN_END -> TODO()
            }
        }

        // add the points to the list of rendered points
        preRenderGraphableListOut.clear()
        val isCandle = (rawGraphableList[0] is CandleGraphable) && renderCandlesArray.isNotEmpty()
        // transform dataPoints and store them into preRenderGraphables
        rawGraphableList.mapIndexedTo(preRenderGraphableListOut) { i, graphable ->
//        dataPoints.mapIndexed { i, graphable ->
            // TODO: clean up this
            val x = horizontalPadding + (i.toFloat() * xscale)
            val y = applyScaling(graphable.y)

            if (isCandle) {
                val candle = graphable as CandleGraphable
                val finalCandle =
                    CandleGraphable(
                        x,
                        applyScaling(candle.open),
                        applyScaling(candle.close),
                        applyScaling(candle.high),
                        applyScaling(candle.low),
                        candle.volume
                    )

                renderCandlesArray[i] = finalCandle
            }

            // return as a Candle or a PointF
            if (isCandle) renderCandlesArray[i]!! else PointGraphable(x,y)
        }
        // return our graphables for rasterization
        return preRenderGraphableListOut
    }

    private fun generateFillPaint(maxValue: Float) {
        fillPaint.shader =
            fillTexture?.run {
                ComposeShader(
                    HueGradientShader.generate(maxValue, viewDimensions.height),
                    this,
                    PorterDuff.Mode.DST_OUT
                )
            } ?: HueGradientShader.generate(maxValue, viewDimensions.height)
    }

    private fun generateFillPaint() {
        // update our shader for the height
//        fillPaint.shader = GraphFillShader.generate(fillColor, viewHeight, fillType.and(FillType.REVERSE_GRADIENT) == FillType.REVERSE_GRADIENT)
//        fillPaint.shader = HueGradientShader.generate(viewHeight)
    }

    /**
     * Populate the rendering area for the horizontal scrub area and ...?
     */
    fun fillRange(canvas: Canvas, horizontalScrub: GraphRangeSelector, clippedByPaths: List<Path?>? = null) {
        // exit conditions for where there is nothing to fill.
        if (!adapter.fill.value || horizontalScrub.startX == null || fillType == FillType.NONE ||  fillColor == 0 ) return

        // save current context
        canvas.withSave {
            // generate clip rect for the bounds
            val clipRect = RectF().apply {
                left = horizontalScrub.startX ?: 0f
                right = horizontalScrub.endX ?: 0f
                bottom = this@Line.viewDimensions.height
            }

            // apply to the current clipping rect
            canvas.clipRect(clipRect)

            // if we are respecting other clip paths
            if (clipType != ClipType.IGNORE_OTHERS) {
                // clip by any of the paths provided (so long as it isn't ours)
                clippedByPaths?.forEach {
                    it?.let {
                        if (it != clipPath)
                            canvas.clipPath(it)
                    }
                }
            }

            // draw the fill in the new rect
            canvas.drawPath(fillPath, fillPaint)
        }   // context restored
    }

    /**
     * Get the clipping path for other fill regions.
     */
    fun getClipPath(): Path? {
        if (!adapter.clip.value || clipType == ClipType.NONE)
            return null
        return clipPath
    }

    // draw to the canvas; todo: probably require passing a scale helper.
    fun draw(canvas: Canvas) {
//        Log.v(
//            LineGraphView.TAG,
//            "draw($canvas)[${viewDimensions.width} x ${viewDimensions.height}] #$identifier range: ($min, $max) d[$range] (start: ${start.y}, end: ${end.y})"
//        )

        // update the capacity in case it changed
        postAnimationGraphables.capacity = rawGraphableList.capacity
        adapter.axisArray.forEach {
            it.draw(canvas, viewDimensions) { points, renderPointsOut ->
                scaleGraphables(points, renderPointsOut as MutableList<Graphable>)
            }
        }

        // TODO: Determine how long the render takes to convert to path + fill for the area;
        //  and then pre-compute on the animation cycles only the keyframes that we will need for the next draw.
        animateFrame = true

        when {
            rawGraphableList.firstOrNull() is CandleGraphable -> {
                canvas.drawCandles(renderCandlesArray, renderCount, candlePaint)
            }
            drawLines -> {
                canvas.drawLines(renderLinesFloatArray, 0, renderCount shl 2, linePaint)
            }
            else -> {
                canvas.drawPoints(renderPointsFloatArray, 0, renderCount shl 1, linePaint)
            }
        }
//        if (identifier == 0) Log.i("RENDERARRAY", "drew $sublineCount lines")
    }
}

private fun Canvas.drawCandles(renderCandlesArray: Array<CandleGraphable?>, count: Int, linePaint: Paint) {
    if (renderCandlesArray.isEmpty()) return

    val s = 4
    renderCandlesArray.forEachIndexed { index, candleGraphable ->
        // exit out of this loop if we reach the count
        if (index > count) return@forEachIndexed

        candleGraphable?.let { candle ->
            linePaint.color = candle.color
//            if (candle.color) {
//                linePaint.color = Color.GREEN
//            } else {
//                linePaint.color = Color.RED
//            }

            drawLine(candle.x, candle.low, candle.x, candle.high,linePaint)
            drawRect(candle.x-s, candle.open, candle.x+s, candle.close, linePaint)
        }
    }
}
