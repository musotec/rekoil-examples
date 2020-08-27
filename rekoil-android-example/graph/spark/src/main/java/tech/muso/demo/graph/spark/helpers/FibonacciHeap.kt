package tech.muso.demo.graph.spark.helpers

// version 1.2.21 -
// modified from source: https://rosettacode.org/wiki/Fibonacci_heap#Kotlin
// changes include convenience inversion of heap property via enum for MAX/MIN

class Node<V : Comparable<V>>(var value: V) {
    var parent: Node<V>? = null
    var child:  Node<V>? = null
    var prev:   Node<V>? = null
    var next:   Node<V>? = null
    var rank = 0
    var mark = false

    fun meld1(node: Node<V>) {
        this.prev?.next = node
        node.prev = this.prev
        node.next = this
        this.prev = node
    }

    fun meld2(node: Node<V>) {
        this.prev?.next = node
        node.prev?.next = this
        val temp = this.prev
        this.prev = node.prev
        node.prev = temp
    }
}

// task requirement
fun <V: Comparable<V>> makeHeap(property: FibonacciHeap.HeapProperty? = FibonacciHeap.HeapProperty.MINIMUM)
        = FibonacciHeap<V>(property = property)

class FibonacciHeap<V: Comparable<V>>(var node: Node<V>? = null, val property: HeapProperty? = HeapProperty.MINIMUM) {
    enum class HeapProperty {
        MINIMUM,
        MAXIMUM
    }

    private val compare: (Int) -> Boolean =
        when(property) {
            HeapProperty.MINIMUM -> { it -> it < 0 }
            HeapProperty.MAXIMUM -> { it -> it > 0 }
            else -> throw IllegalArgumentException()
        }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun compare(x: V, y: V): Boolean {
        return compare(x.compareTo(y))
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun compare(x: Node<V>, y: Node<V>): Boolean {
        return compare(x.value.compareTo(y.value))
    }

    // task requirement
    fun insert(v: V): Node<V> {
        val x = Node(v)
        if (this.node == null) {
            x.next = x
            x.prev = x
            this.node = x
        }
        else {
            this.node!!.meld1(x)
            if (compare(x.value,this.node!!.value)) this.node = x
        }
        return x
    }

    // task requirement
    fun union(other: FibonacciHeap<V>) {
        if (this.node == null) {
            this.node = other.node
        }
        else if (other.node != null) {
            this.node!!.meld2(other.node!!)
            if (compare(other.node!!.value, this.node!!.value)) this.node = other.node
        }
        other.node = null
    }

    // task requirement
    fun minimum(): V? = this.node?.value
    fun maximum(): V? = this.node?.value

    // task requirement
    fun extractMin(): V? {
        if (this.node == null) return null
        val min = minimum()
        val roots = mutableMapOf<Int, Node<V>>()

        fun add(r: Node<V>) {
            r.prev = r
            r.next = r
            var rr = r
            while (true) {
                var x = roots[rr.rank] ?: break
                roots.remove(rr.rank)
                if (compare(x, rr)) {
                    val t = rr
                    rr = x
                    x = t
                }
                x.parent = rr
                x.mark = false
                if (rr.child == null) {
                    x.next = x
                    x.prev = x
                    rr.child = x
                }
                else {
                    rr.child!!.meld1(x)
                }
                rr.rank++
            }
            roots[rr.rank] = rr
        }

        var r = this.node!!.next
        while (r != this.node) {
            val n = r!!.next
            add(r)
            r = n
        }
        val c = this.node!!.child
        if (c != null) {
            c.parent = null
            var rr = c.next!!
            add(c)
            while (rr != c) {
                val n = rr.next!!
                rr.parent = null
                add(rr)
                rr = n
            }
        }
        if (roots.isEmpty()) {
            this.node = null
            return min
        }
        val d = roots.keys.first()
        var mv = roots[d]!!
        roots.remove(d)
        mv.next = mv
        mv.prev = mv
        for ((_, rr) in roots) {
            rr.prev = mv
            rr.next = mv.next
            mv.next!!.prev = rr
            mv.next = rr
            if (compare(rr,mv)) mv = rr
        }
        this.node = mv
        return min
    }

    // task requirement
    fun decreaseKey(n: Node<V>, v: V) {
        require (!compare(n.value, v)) {
            "In 'decreaseKey' new value greater (wrong direction) of existing value"
        }
        n.value = v
        if (n == this.node) return
        val p = n.parent
        if (p == null) {
            if (compare(v,this.node!!.value)) this.node = n
            return
        }
        cutAndMeld(n)
    }

    // extension to add ability to change root to higher value - O(log n)
    fun increaseKey(n: Node<V>, v: V): Node<V> {
        // TODO: evaluate performance of this. may not need to delete node if root/node is large heap.
        //   or if the new value keeps the heap property.
        delete(n)
        return insert(v)
    }

    fun updateKey(n: Node<V>, v: V): Node<V> {
        if (compare(n.value, v)) {   // old < new
            return increaseKey(n, v)    // O(log n)
        } else {
            decreaseKey(n, v)           // O(1)
            return n
        }
    }

    private fun cut(x: Node<V>) {
        val p = x.parent
        if (p == null) return
        p.rank--
        if (p.rank == 0) {
            p.child = null
        }
        else {
            p.child = x.next
            x.prev?.next = x.next
            x.next?.prev = x.prev
        }
        if (p.parent == null) return
        if (!p.mark) {
            p.mark = true
            return
        }
        cutAndMeld(p)
    }

    private fun cutAndMeld(x: Node<V>) {
        cut(x)
        x.parent = null
        this.node?.meld1(x)
    }

    // task requirement
    fun delete(n: Node<V>) {
        val p = n.parent
        if (p == null) {
            if (n == this.node) {
                extractMin()
                return
            }
            n.prev?.next = n.next
            n.next?.prev = n.prev
        }
        else {
            cut(n)
        }
        var c = n.child
        if (c == null) return
        while (true) {
            c!!.parent = null
            c = c.next
            if (c == n.child) break
        }
        this.node?.meld2(c!!)
    }

    fun visualize() {
        if (this.node == null) {
            println("<empty>")
            return
        }

        fun f(n: Node<V>, pre: String) {
            var pc = "│ "
            var x = n
            while (true) {
                if (x.next != n) {
                    print("$pre├─")
                }
                else {
                    print("$pre└─")
                    pc = "  "
                }
                if (x.child == null) {
                    println("╴ ${x.value}")
                }
                else {
                    println("┐ ${x.value}")
                    f(x.child!!, pre + pc)
                }
                if (x.next == n) break
                x = x.next!!
            }
        }
        f(this.node!!, "")
    }
}