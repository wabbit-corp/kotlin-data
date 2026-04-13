// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeftistHeapSpec {
    @Test
    fun `leftist heap size stays accurate across heap operations`() {
        val heap = LeftistHeap.of(3, 1, 5, 2, 4)

        assertEquals(5, heap.size)
        assertEquals(4, heap.deleteMin().size)
        assertEquals(6, heap.insert(0).size)
        assertEquals(7, heap.merge(LeftistHeap.of(6, 7)).size)
        assertEquals(0, LeftistHeap.empty<Int>().size)
    }

    @Test
    fun `leftist heap exposes merge`() {
        val merged = LeftistHeap.of(3, 1, 5).merge(LeftistHeap.of(4, 2))

        var current = merged
        val values = mutableListOf<Int>()
        while (current !is LeftistHeap.Empty) {
            values += current.findMin()
            current = current.deleteMin()
        }

        assertEquals(listOf(1, 2, 3, 4, 5), values)
    }

    @Test
    fun `leftist heap equality and hash code are logical`() {
        val left = LeftistHeap.of(3, 1, 5, 2)
        val right = LeftistHeap.of(2, 5, 1, 3)

        assertTrue(left == right)
        assertTrue(right == left)
        assertEquals(left.hashCode(), right.hashCode())
    }
}
