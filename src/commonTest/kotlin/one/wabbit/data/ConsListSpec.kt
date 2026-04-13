// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConsListSpec {
    @Test
    fun `cons list participates in kotlin list equality`() {
        val cons = consListOf(1, 2)
        val list = listOf(1, 2)

        assertTrue(list == cons)
        assertTrue(cons == list)
        assertEquals(list.hashCode(), cons.hashCode())
    }

    @Test
    fun `cons list behaves like a normal list`() {
        val cons: List<Int> = consListOf(1, 2, 3)

        assertEquals(3, cons.size)
        assertEquals(2, cons[1])
        assertEquals(1, cons.indexOf(2))
        assertEquals(2, cons.lastIndexOf(3))
        assertEquals(listOf(2, 3), cons.subList(1, 3))
        assertEquals(listOf(1, 2, 3), cons.toList())
    }

    @Test
    fun `cons listIterator is bidirectional without materializing`() {
        val cons: List<Int> = consListOf(1, 2, 3, 4)
        val iterator = cons.listIterator(2)

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasPrevious())
        assertEquals(2, iterator.nextIndex())
        assertEquals(1, iterator.previousIndex())
        assertEquals(2, iterator.previous())
        assertEquals(0, iterator.previousIndex())
        assertEquals(1, iterator.previous())
        assertEquals(-1, iterator.previousIndex())
        assertEquals(0, iterator.nextIndex())
        assertEquals(1, iterator.next())
        assertEquals(2, iterator.next())
        assertEquals(3, iterator.next())
        assertEquals(4, iterator.next())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun `cons subList and listIterator validate bounds like List`() {
        val cons: List<Int> = consListOf(1, 2, 3, 4)

        assertEquals(listOf(2, 3), cons.subList(1, 3))
        assertEquals(emptyList(), cons.subList(2, 2))
        assertFailsWith<IndexOutOfBoundsException> { cons.listIterator(-1) }
        assertFailsWith<IndexOutOfBoundsException> { cons.listIterator(5) }
        assertFailsWith<IndexOutOfBoundsException> { cons.subList(-1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { cons.subList(1, 5) }
        assertFailsWith<IllegalArgumentException> { cons.subList(3, 2) }
    }
}
