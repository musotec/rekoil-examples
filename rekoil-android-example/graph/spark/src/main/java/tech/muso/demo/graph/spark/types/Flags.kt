package tech.muso.demo.graph.spark.types

// improve reading/writing of flag checks
@Suppress("NOTHING_TO_INLINE")
inline infix fun Int.has(other: Int): Boolean = this and other == other



