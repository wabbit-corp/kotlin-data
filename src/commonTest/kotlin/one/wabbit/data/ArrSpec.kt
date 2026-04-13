// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ArrSpec {
    @Test
    fun `arr constructor does not alias caller array and cached hash stays coherent`() {
        val backing = arrayOf<Any?>(1, 2, null)
        val arr = Arr<Int?>(backing)
        val hash = arr.hashCode()

        backing[0] = 99
        backing[2] = 42

        assertEquals(listOf(1, 2, null), arr.toList())
        assertEquals(hash, arr.hashCode())
    }

    @Test
    fun `arr hash code supports nullable elements`() {
        val left = Arr<String?>(arrayOf("a", null))
        val right = Arr.fromList(listOf("a", null))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `arr empty access uses collection exception types`() {
        val empty = Arr.empty<Int>()

        assertFailsWith<NoSuchElementException> {
            empty.first()
        }
        assertFailsWith<NoSuchElementException> {
            empty.last()
        }
        assertFailsWith<IndexOutOfBoundsException> {
            empty[0]
        }
        assertFailsWith<IndexOutOfBoundsException> {
            empty.update(0, 1)
        }

        assertEquals(null, empty.firstOrNull())
        assertEquals(null, empty.lastOrNull())

        val values = arrOf(1, 2, 3)
        assertEquals(1, values.firstOrNull())
        assertEquals(3, values.lastOrNull())
    }

    @Test
    fun `arr does not compare equal to plain lists`() {
        val arr = arrOf(1, 2, 3)
        val list = listOf(1, 2, 3)

        assertFalse(arr.equals(list))
        assertFalse(list == arr)
    }

    @Test
    fun `arrMap factory does not alias caller input and cached hash stays coherent`() {
        val source = linkedMapOf("a" to 1)
        val map = ArrMap.from(source)
        val hash = map.hashCode()

        source.clear()
        source["b"] = 2

        assertEquals(mapOf("a" to 1), map.toMap())
        assertEquals(1, map["a"])
        assertEquals(hash, map.hashCode())
    }

    @Test
    fun `arrMap hash code supports nullable values`() {
        val left = ArrMap.from<String, Int?>(mapOf("a" to null))
        val right = ArrMap.from<String, Int?>(mapOf("a" to null))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `arrMap empty access uses collection exception types`() {
        val empty = ArrMap.empty<String, Int>()

        assertFailsWith<NoSuchElementException> {
            empty.first()
        }
        assertFailsWith<NoSuchElementException> {
            empty.last()
        }
    }

    @Test
    fun `arrMap publishes its tiny map size envelope`() {
        assertEquals(16, ArrMap.RECOMMENDED_MAX_SIZE)
    }
}
