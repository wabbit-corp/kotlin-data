// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrSpec {
    private class HashCodeBomb(private val value: String) {
        override fun equals(other: Any?): Boolean = other is HashCodeBomb && value == other.value

        override fun hashCode(): Int =
            error("hashCode should not be called on the cold equals path")
    }

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
    fun `arr fromArray does not alias caller array`() {
        val backing = arrayOf<Any?>(1, 2, null)
        val arr = Arr.fromArray(backing)

        backing[1] = 20

        assertEquals(listOf(1, 2, null), arr.toList())
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

        assertFailsWith<NoSuchElementException> { empty.first() }
        assertFailsWith<NoSuchElementException> { empty.last() }
        assertFailsWith<IndexOutOfBoundsException> { empty[0] }
        assertFailsWith<IndexOutOfBoundsException> { empty.update(0, 1) }

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
    fun `arr equals does not compute hash codes on the cold mismatch path`() {
        val left = Arr<HashCodeBomb>(arrayOf(HashCodeBomb("left"), HashCodeBomb("shared")))
        val right = Arr<HashCodeBomb>(arrayOf(HashCodeBomb("right"), HashCodeBomb("shared")))

        assertFalse(left == right)
    }

    @Test
    fun `arr exposes list style search and slicing helpers`() {
        val arr = arrOf(1, 2, 3, 2)

        assertTrue(arr.contains(2))
        assertFalse(arr.contains(9))
        assertTrue(arr.containsAll(listOf(1, 2)))
        assertFalse(arr.containsAll(listOf(1, 9)))
        assertEquals(1, arr.indexOf(2))
        assertEquals(3, arr.lastIndexOf(2))
        assertEquals(-1, arr.indexOf(9))
        assertEquals(listOf(2, 3), arr.subList(1, 3).toList())
        assertEquals(listOf(2, 3, 2), arr.listIterator(1).asSequence().toList())
    }

    @Test
    fun `arr listIterator validates bounds`() {
        val arr = arrOf(1, 2, 3)

        assertFailsWith<IndexOutOfBoundsException> { arr.listIterator(-1) }
        assertFailsWith<IndexOutOfBoundsException> { arr.listIterator(4) }
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
    fun `arrMap equals does not compute hash codes on the cold mismatch path`() {
        val left = ArrMap.from(mapOf("a" to HashCodeBomb("left"), "b" to HashCodeBomb("shared")))
        val right = ArrMap.from(mapOf("a" to HashCodeBomb("right"), "b" to HashCodeBomb("shared")))

        assertFalse(left == right)
    }

    @Test
    fun `arrMap empty access uses collection exception types`() {
        val empty = ArrMap.empty<String, Int>()

        assertFailsWith<NoSuchElementException> { empty.first() }
        assertFailsWith<NoSuchElementException> { empty.last() }
    }

    @Test
    fun `arrMap publishes its tiny map size envelope`() {
        assertEquals(16, ArrMap.RECOMMENDED_MAX_SIZE)
    }

    @Test
    fun `arrMap remove clear keys and values work`() {
        val map = ArrMap.from(linkedMapOf("a" to 1, "b" to 2, "c" to 3))

        val removed = map.remove("b")

        assertEquals(mapOf("a" to 1, "c" to 3), removed.toMap())
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), map.toMap())
        assertTrue(removed.remove("missing") === removed)
        assertTrue(map.clear().isEmpty())
        assertEquals(listOf("a", "b", "c"), map.keys().toList())
        assertEquals(listOf(1, 2, 3), map.values().toList())
    }

    @Test
    fun `arrMap exposes entry iteration and entry views`() {
        val map = ArrMap.from(linkedMapOf("a" to 1, "b" to 2, "c" to 3))

        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.toList())
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.entries().toList())
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), map.asSequence().toList())
    }
}
