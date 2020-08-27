package tech.muso.demo.graph.spark.types

inline val Pair<Number, Number>?.x: Float get() = if (this == null) 0f else first.toFloat()
inline val Pair<Number, Number>?.y: Float get() = if (this == null) 0f else second.toFloat()