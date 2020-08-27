package tech.muso.demo.graph.spark.graph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log

// TODO: rename class (horizontal bounded scrub?)
data class GraphRangeSelector(var startX: Float? = null, var endX: Float? = null) {

    val scrubLineWidth = 4f
    var scrubColor = Color.WHITE
    set(value) {
        scrubLinePaint.color = value
        field = value
    }

    inline val isRangeBound get() = endX != null

    private val scrubLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = scrubLineWidth
        color = scrubColor
        strokeCap = Paint.Cap.ROUND
    }

//    private val scrubLineStartPath = Path()
//    private val scrubLineEndPath = Path()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Canvas.drawVerticalAt(x: Float) {
        drawLine(x, 0f, x, height.toFloat(), scrubLinePaint)
    }

    fun reset() {
        startX = null
        endX = null
    }

    fun adjustPoints() {
        startX?.let { start ->
            endX?.let { end ->
                // swap points if needed
                if (start > end) {
                    startX = end
                    endX = start
                    Log.e("Scrub", "Swapped Start/End $start, $end -> $startX, $endX")
                }
            }
        }
    }

    fun draw(canvas: Canvas) {
        // TODO: make vertical flavor
        startX?.let { canvas.drawVerticalAt(it) }
        endX?.let { canvas.drawVerticalAt(it) }
    }

}