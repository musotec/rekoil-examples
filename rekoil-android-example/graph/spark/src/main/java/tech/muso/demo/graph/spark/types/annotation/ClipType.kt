package tech.muso.demo.graph.spark.types.annotation

@Retention(AnnotationRetention.SOURCE)
annotation class ClipType {
    companion object {
        const val NONE = 0          // do not clip
        const val CLIP_ABOVE = 1    // clip above our line
        const val CLIP_BELOW = 2    // clip below our line
        const val CLIP_LAYERS_BELOW = 4
        const val CLIP_LAYERS_ABOVE = 5
        const val IGNORE_OTHERS = -1  // ignore all other clip paths
//        const val CLIP_ABOVE_LAYERS_BELOW_FILL = 1 shl 2
//        const val CLIP_BELOW_LAYERS_ABOVE_FILL = 1 shl 3
//        const val CLIP_BELOW_LAYERS_BELOW_FILL = 1 shl 4
//        const val CLIP_SELF_FILL_LAYERS_BELOW = 1 shl 5
//        const val CLIP_SELF_FILL_LAYERS_ABOVE = 1 shl 6
    }
}