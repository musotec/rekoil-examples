package tech.muso.demo.graph.spark

import java.util.*

class FifoList<E>(val capacity: Int) : AbstractList<E>() {
    private val linkedList =  LinkedList<E>()

    override fun add(e: E): Boolean {
        if (linkedList.size >= capacity) linkedList.removeFirst()
        return linkedList.add(e)
    }

    override fun add(index: Int, element: E) {
        linkedList.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        return linkedList.addAll(index, elements)
    }

    override fun clear() {
        linkedList.clear()
    }

    override fun removeAt(index: Int): E {
        return linkedList.removeAt(index)
    }

    override fun set(index: Int, element: E): E {
        return linkedList.set(index, element)
    }

    override val size: Int
        get() = linkedList.size

    override fun contains(element: E): Boolean {
        return linkedList.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return linkedList.containsAll(elements)
    }

    override fun get(index: Int): E {
        return linkedList.get(index)
    }

    override fun indexOf(element: E): Int {
        return linkedList.indexOf(element)
    }

    override fun isEmpty(): Boolean {
        return linkedList.isEmpty()
    }

    override fun iterator(): MutableIterator<E> {
        return linkedList.iterator()
    }

    override fun lastIndexOf(element: E): Int {
        return linkedList.lastIndexOf(element)
    }

    override fun listIterator(): MutableListIterator<E> {
        return linkedList.listIterator()
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return linkedList.listIterator(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        return linkedList.subList(fromIndex, toIndex)
    }
}