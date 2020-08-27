package tech.muso.demo.graph.spark.types

abstract class GraphableDataSet<out T : Number> {

    open var data: List<Number> = listOf<T>()
        set(value) {
            field = value
            onDataSetChanged()
        }

    val datasetSize: Int get() = data.size

    abstract fun onDataSetChanged()

    abstract fun getItem(index: Int): T

    /** @return the relative Y position within the graph. **/
    open fun getY(index: Int): Float = getItem(index).toFloat()

    // TODO: explore caching mapped Y values to save repeated calls to .toFloat()
}