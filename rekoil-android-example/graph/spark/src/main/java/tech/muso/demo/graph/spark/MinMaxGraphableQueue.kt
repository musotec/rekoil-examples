package tech.muso.demo.graph.spark

import android.os.Build
import androidx.annotation.RequiresApi
import tech.muso.demo.graph.core.Graphable
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import java.util.*

class MinMaxGraphableQueue<T : Graphable>(
    rekoilScope: RekoilScope,
    private val initialValue: T,
    capacity: Int = Int.MAX_VALUE
) : RekoilQueue<T>(rekoilScope, initialValue, capacity) {

    @RequiresApi(Build.VERSION_CODES.N)
    val maxHeap = PriorityQueue<T> { a, b -> -a.top.compareTo(b.top) }

    @RequiresApi(Build.VERSION_CODES.N)
    val minHeap = PriorityQueue<T> { a, b -> a.bottom.compareTo(b.bottom) }

    val min: Atom<Float> = atom { initialValue.bottom }
    val max: Atom<Float> = atom { initialValue.top }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun updateHeaps() {
        min.value = minHeap.peek()?.bottom ?: 0f
        max.value = maxHeap.peek()?.top ?: 0f
    }

    override fun add(e: T): Boolean {
        val b = super.add(e)
        maxHeap.add(e)
        minHeap.add(e)
        updateHeaps()
        return b
    }

    override fun poll(): T? {
        val e = super.poll()
        maxHeap.remove(e)
        minHeap.remove(e)
        updateHeaps()
        return e
    }
}