// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExceptionContractSpec {
    @Test
    fun `lazy list get rejects negative indices with index out of bounds`() {
        val list = lazyConsListOf(1, 2, 3)

        assertFailsWith<IndexOutOfBoundsException> {
            list[-1]
        }
    }

    @Test
    fun `chunk head uses no such element on empty chunk`() {
        assertFailsWith<NoSuchElementException> {
            Chunk.empty<Int>().head
        }
    }

    @Test
    fun `leftist heap empty access uses one empty element contract`() {
        val empty = LeftistHeap.empty<Int>()

        assertFailsWith<NoSuchElementException> {
            empty.findMin()
        }
        assertFailsWith<NoSuchElementException> {
            empty.deleteMin()
        }
    }

    @Test
    fun `arr iterator next throws no such element when exhausted`() {
        val iterator = arrOf(1).iterator()
        iterator.next()

        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }
    }

    @Test
    fun `float buffer iterators throw no such element at boundaries`() {
        val buffer = FloatBuffer(floatArrayOf(1f))

        val iterator = buffer.iterator()
        iterator.next()
        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }

        val listIterator = buffer.listIterator()
        assertFailsWith<NoSuchElementException> {
            listIterator.previous()
        }
        listIterator.next()
        assertFailsWith<NoSuchElementException> {
            listIterator.next()
        }
    }

    @Test
    fun `float deque get set and iterator use collection exception types`() {
        val deque = FloatDeque(floatArrayOf(1f))

        assertFailsWith<IndexOutOfBoundsException> {
            deque[-1]
        }
        assertFailsWith<IndexOutOfBoundsException> {
            deque[1] = 2f
        }

        val iterator = deque.iterator()
        iterator.next()
        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }
    }

    @Test
    fun `float deque pop operations use collection exception types`() {
        val empty = FloatDeque.empty()

        assertFailsWith<NoSuchElementException> {
            empty.popFirst()
        }
        assertFailsWith<NoSuchElementException> {
            empty.popLast()
        }
        assertFailsWith<IndexOutOfBoundsException> {
            empty.popFirst(-1)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            empty.popLast(1)
        }
    }

    @Test
    fun `float buffer empty access and index-like arguments use collection exception types`() {
        val buffer = FloatBuffer.empty()

        assertFailsWith<NoSuchElementException> {
            buffer.removeFirst()
        }
        assertFailsWith<NoSuchElementException> {
            buffer.removeLast()
        }
        assertFailsWith<NoSuchElementException> {
            buffer.reduce { left, right -> left + right }
        }
        assertFailsWith<IndexOutOfBoundsException> {
            buffer.insertAt(0, floatArrayOf(1f), startIndex = -1)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            buffer.insertAt(0, listOf(1f), endIndex = 2)
        }
    }
}
