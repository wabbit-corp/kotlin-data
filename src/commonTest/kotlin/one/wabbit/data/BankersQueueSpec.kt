// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankersQueueSpec {
    @Test
    fun `snocReversed counts all appended elements`() {
        val queue = BankersQueue
            .fromConsList(consListOf(1, 2))
            .snocReversed(consListOf(4, 3))

        assertEquals(listOf(1, 2, 3, 4), queue.toList())
    }

    @Test
    fun `queue aliases read like queue operations`() {
        val queue = BankersQueue
            .empty<Int>()
            .enqueue(1)
            .enqueue(2)

        assertEquals(2, queue.size)
        assertEquals(queue.size, queue.frontSize + queue.backSize)

        val first = queue.dequeue().value
        val second = first!!.second.dequeue().value

        assertEquals(1, first.first)
        assertEquals(2, second!!.first)
        assertEquals(null, second.second.dequeueOrNull())
    }

    @Test
    fun `bankers queue equality and hash code are logical`() {
        val left = BankersQueue.empty<Int>().enqueue(1).enqueue(2).enqueue(3)
        val right = BankersQueue.fromConsList(consListOf(1, 2)).enqueue(3)

        assertTrue(left == right)
        assertTrue(right == left)
        assertEquals(left.hashCode(), right.hashCode())
    }

    private fun <A> BankersQueue<A>.toList(): List<A> {
        val result = mutableListOf<A>()
        var current = this
        while (true) {
            val next = current.uncons().value ?: return result
            result += next.first
            current = next.second
        }
    }
}
