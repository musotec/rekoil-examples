package tech.muso.demo.graph.spark.paint.shaders

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.ShapeDrawable

object HueGradientShader {

    @JvmStatic
    fun generate(startY: Float, endY: Float, isReversed: Boolean = false): Shader {
        val rainbow = intArrayOf(
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA
        )

        return LinearGradient(
            0f,
            startY,
            0f,
            endY,
            rainbow,
            (rainbow.indices).map { i -> i/(rainbow.size - 1).toFloat() }.toFloatArray(),
            Shader.TileMode.MIRROR  // repeat over x direction (unspecified 0 to 0)
        )
    }

    val sf = object : ShapeDrawable.ShaderFactory() {
        override fun resize(width: Int, height: Int): Shader {
            return LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.RED,
                    Color.YELLOW,
                    Color.GREEN,
                    Color.CYAN,
                    Color.BLUE,
                    Color.MAGENTA,
                    Color.RED
                ),
                floatArrayOf( 0f, 0.167f, 0.333f, 0.5f, 0.667f, 0.833f, 1f ),
                Shader.TileMode.CLAMP
            )
        }
    }

}