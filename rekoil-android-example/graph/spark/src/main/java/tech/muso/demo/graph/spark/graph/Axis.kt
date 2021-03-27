package tech.muso.demo.graph.spark.graph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import tech.muso.demo.graph.core.PointGraphable
import tech.muso.demo.graph.spark.types.ViewDimensions

/**
 * Represents an axis that extends infinitely in a specific direction.
 */
data class Axis(var position: Float,
                var isHorizontal: Boolean) {
    /**
     * The angle of the axis. TODO: support rotational axis via points, but maybe vector class?
     */
    var angle: Float = 0f
    val paint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 1f
    }

    val startPoint: PointGraphable = if (isHorizontal) PointGraphable(0f, position) else PointGraphable(position, 0f)
    val endPoint: PointGraphable = if (isHorizontal) PointGraphable(-1f, position) else PointGraphable(position, -1f)

    private val points = listOf(startPoint, endPoint)
    val renderPointsOut = arrayListOf<PointGraphable>()

    /**
     * Draw the axis to the canvas.
     *
     * TODO: FIX THIS TO BE GOOD INSTEAD OF WHAT IT IS
     */
    fun draw(canvas: Canvas, viewDimensions: ViewDimensions, getAlignment: (List<PointGraphable>, MutableList<PointGraphable>) -> Unit) {
//        if (isHorizontal)
//            canvas.drawLine(0f, position, viewDimensions.width, position, paint)
//        else
//            canvas.drawLine(position, 0f, position, viewDimensions.height, paint)

        // TODO: THIS CODE BELOW IS GOOD PROBABLY
        if (points[1].x != 0f) points[1].x = viewDimensions.width
        if (points[1].y != 0f) points[1].y = viewDimensions.height

//        computeFunction(points, renderPoints)
        getAlignment(points, renderPointsOut)

//        renderPoints[0].y = alignBy
//        renderPoints[1].y = alignBy

        if (isHorizontal) renderPointsOut[1].x = viewDimensions.width

        canvas.drawLine(renderPointsOut[0].x, renderPointsOut[0].y, renderPointsOut[1].x, renderPointsOut[1].y, paint)
    }
}