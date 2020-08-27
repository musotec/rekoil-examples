package tech.muso.demo.graph.spark

import org.junit.Test

import org.junit.Assert.*

class GraphDataAdapterKtTest {

    @Test
    fun testMinMaxDefaultValues() {
        val t: MinMaxPair? = null // MinMaxPair(1f, 2f)
        assertEquals(0f, t.min)
        assertEquals(1f, t.max)
    }
}