// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeftistHeapSpec {
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
