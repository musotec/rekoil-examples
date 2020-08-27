package tech.muso.demo.graph.spark.types

import java.io.Serializable

data class ViewDimensions(val width: Float, val height: Float) : Serializable {
    constructor(width: Number, height: Number) : this(width.toFloat(), height.toFloat())
}