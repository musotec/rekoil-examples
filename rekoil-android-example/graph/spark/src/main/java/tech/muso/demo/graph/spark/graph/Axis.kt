package tech.muso.demo.graph.spark.graph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import tech.muso.demo.graph.spark.types.ViewDimensions
import kotlin.reflect.KFunction2

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

    val startPoint: PointF = if (isHorizontal) PointF(0f, position) else PointF(position, 0f)
    val endPoint: PointF = if (isHorizontal) PointF(-1f, position) else PointF(position, -1f)

    private val points = listOf(startPoint, endPoint)
    val renderPoints = arrayListOf<PointF>()

    /**
     * Draw the axis to the canvas.
     *
     * TODO: FIX THIS TO BE GOOD INSTEAD OF WHAT IT IS
     */
    fun draw(canvas: Canvas, viewDimensions: ViewDimensions, computeFunction: KFunction2<List<PointF>, MutableList<PointF>, List<PointF>>) {
//        if (isHorizontal)
//            canvas.drawLine(0f, position, viewDimensions.width, position, paint)
//        else
//            canvas.drawLine(position, 0f, position, viewDimensions.height, paint)

        if (points[1].x != 0f) points[1].x = viewDimensions.width
        if (points[1].y != 0f) points[1].y = viewDimensions.height

        computeFunction(points, renderPoints)

        if (isHorizontal) renderPoints[1].x = viewDimensions.width

        canvas.drawLine(renderPoints[0].x, renderPoints[0].y, renderPoints[1].x, renderPoints[1].y, paint)
    }
}