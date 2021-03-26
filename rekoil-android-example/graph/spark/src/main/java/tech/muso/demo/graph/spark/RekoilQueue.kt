package tech.muso.demo.graph.spark

import android.os.Build
import androidx.annotation.RequiresApi
import tech.muso.demo.graph.core.Graphable
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import java.util.*

open class RekoilQueue<T : Any>(rekoilScope: RekoilScope, private val initialValue: T, val capacity: Int = Int.MAX_VALUE) :  RekoilScope by rekoilScope, LinkedList<T>() {

    val removed: Atom<T> = atom { initialValue }
    val added: Atom<T> = atom { initialValue }

    val end: Atom<T> = atom { initialValue }
    val start: Atom<T> = atom { initialValue }

    override fun add(e: T): Boolean {
        // if at capacity, then remove the oldest node
        if (size >= capacity) {
            poll()
            start.value = last // and update the node at the start of the list
        } else if (size == 0) {
            start.value = e
        }

        val b = super.add(e)
        added.value = e
        end.value = e
//        println("Added $e")
        return b
    }

    override fun poll(): T? {
        val e = super.poll()
        if (e != null) removed.value = e
//        println("Removed $e")
        return e
    }

}