package tech.muso.demo.graph.spark.types.annotation

@Retention(AnnotationRetention.SOURCE)
annotation class FillType {
    companion object {
        const val NONE = 0
        const val UP                = 1 shl 0
        const val DOWN              = 1 shl 1
        const val ALL               = 3
        const val TOWARD_ZERO       = 1 shl 2
        const val REVERSE_GRADIENT  = 1 shl 3
    }
}
