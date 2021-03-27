package tech.muso.demo.graph.spark

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import tech.muso.rekoil.core.launch
import java.util.*

open class RekoilQueue<T : Any>(rekoilScope: RekoilScope, private val initialValue: T, var capacity: Int = Int.MAX_VALUE) :  RekoilScope by rekoilScope, LinkedList<T>() {

    private val mutex = Mutex()

    val removed: Atom<T> = atom { initialValue }
    val added: Atom<T> = atom { initialValue }

    val end: Atom<T> = atom { initialValue }
    val start: Atom<T> = atom { initialValue }

    fun setList(list: List<T>) {
        // update the capacity
        capacity = list.size
        // clear the backing list first

        runBlocking {
            mutex.withLock {
                clear()
            }
        }

//        while(poll() != null) {}    // remove all items using poll
        // the list should be set quickly, then update the end/start
//        addAll(list)
//        start.value = last
//        end.value = first

        // add individual in case class is extended, this way we update the heaps
        list.forEach {
            add(it)
        }
    }

    override fun add(e: T): Boolean {
        var b = false
        runBlocking {
            mutex.withLock {
                // if at capacity, then remove the oldest node
                if (size >= capacity) {
                    poll()
                    start.value = last // and update the node at the start of the list
                } else if (size == 0) {
                    start.value = e
                }

                b = super.add(e)
                added.value = e
                end.value = e
//               println("Added $e")
            }
        }
        return b
    }

    override fun poll(): T? {
        var e: T? = null
        launch {
            mutex.withLock {
                e = super.poll()
                if (e != null) removed.value = e as T
//                println("Removed $e")
            }
        }
        return e
    }

    fun useLock(function: () -> Unit) {
        launch {
            mutex.withLock {
                function()
            }
        }
    }
}