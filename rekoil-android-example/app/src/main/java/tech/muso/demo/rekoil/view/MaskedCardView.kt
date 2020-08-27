package tech.muso.demo.rekoil.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.google.android.material.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.ShapeAppearancePathProvider
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.RectF

/**
 * A Card view that clips the content of the shape defined, this should be done upstream in Google's
 * MaterialComponents library.
 *
 * I am not sure what they have planned instead, but this definitely should be the default case for
 * MaterialCardView, since it is a layout container. We can work around it for now.
 */
class MaskedCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyle) {

    // Get the internal API so that we can simply fix the upstream code ourselves.
    @SuppressLint("RestrictedApi")
    private val pathProvider = ShapeAppearancePathProvider()
    private val path: Path = Path()
    private val shapeAppearance: ShapeAppearanceModel = ShapeAppearanceModel.builder(
        context,
        attrs,
        defStyle,
        R.style.Widget_MaterialComponents_CardView
    ).build()
    private val rectF = RectF(0f, 0f, 0f, 0f)

    // Above we follow the way the core library creates paths from the xml to achieve drawing rects
    override fun onDraw(canvas: Canvas) {
        // only we simply apply the path as a clipping mask to the canvas to trim the contents
        canvas.clipPath(path)
        super.onDraw(canvas)
    }

    // Add an additional callback that will recompute the appearance when we resize the layout
    @SuppressLint("RestrictedApi")
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rectF.right = w.toFloat()
        rectF.bottom = h.toFloat()
        pathProvider.calculatePath(shapeAppearance, 1f, rectF, path)
        super.onSizeChanged(w, h, oldw, oldh)
    }
}