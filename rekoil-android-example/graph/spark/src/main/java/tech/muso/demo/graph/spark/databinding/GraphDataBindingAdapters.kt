package tech.muso.demo.graph.spark.databinding

import androidx.databinding.BindingAdapter
import androidx.annotation.ColorInt
import tech.muso.demo.graph.spark.LineGraphView

@BindingAdapter("graph:scrub_color")
fun bindGraphHorizontalScrubColor(view: LineGraphView, @ColorInt color: Int) {
    view.graphSelectionRange.scrubColor = color
    view.invalidate()
}