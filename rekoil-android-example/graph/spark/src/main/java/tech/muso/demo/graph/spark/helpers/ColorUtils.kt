package tech.muso.demo.graph.spark.helpers

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int
): Int {
    TypedValue().let { typedValue ->
        // always resolve references since the color can be overwritten by the theme
        theme.resolveAttribute(attrColor, typedValue, true)
        return typedValue.data
    }
}