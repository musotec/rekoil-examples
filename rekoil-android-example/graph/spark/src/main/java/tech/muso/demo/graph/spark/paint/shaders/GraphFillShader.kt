package tech.muso.demo.graph.spark.paint.shaders

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.ColorInt


inline fun @receiver:androidx.annotation.ColorInt Int.withAlpha(alpha: Int): Int {
    return this and (alpha shr 24)
}

fun Paint.setGraphFillShader(@ColorInt color: Int, width: Float) {
    val shader: LinearGradient = LinearGradient(
        0f, 0f, 0f, width, color,
        color.withAlpha(0x4f), Shader.TileMode.MIRROR
    )

//    val matrix = Matrix()
//    matrix.setRotate(90)
//    shader.setLocalMatrix(matrix)

    this.shader = shader
}

object GraphFillShader {
    @JvmStatic
    fun generate(color: Int, width: Float, isReversed: Boolean = false): Shader {
        if (!isReversed) {
            return LinearGradient(
                0f, 0f, 0f, width, color,
                color.withAlpha(0x4f), Shader.TileMode.MIRROR
            )
        } else {
            return LinearGradient(
                0f, width, 0f, 0f, color,
                color.withAlpha(0x4f), Shader.TileMode.MIRROR
            )
        }
    }
}