package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcurrentHashMapSpec {
    @Test
    fun `put get and replace work`() {
        val map = ConcurrentHashMap<String, Int>()

        assertNull(map.put("a", 1))
        assertEquals(1, map["a"])
        assertEquals(1, map.put("a", 2))
        assertEquals(2, map["a"])
        assertEquals(1, map.size())
    }

    @Test
    fun `putIfAbsent and containsKey work`() {
        val map = ConcurrentHashMap<String, Int>()

        assertNull(map.putIfAbsent("a", 1))
        assertEquals(1, map.putIfAbsent("a", 2))
        assertTrue(map.containsKey("a"))
        assertFalse(map.containsKey("b"))
        assertEquals(1, map.size())
    }

    @Test
    fun `remove variants work`() {
        val map = ConcurrentHashMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        assertFalse(map.remove("a", 2))
        assertTrue(map.remove("a", 1))
        assertNull(map["a"])
        assertEquals(2, map.remove("b"))
        assertEquals(0, map.size())
    }

    @Test
    fun `entriesSnapshot returns current entries`() {
        val map = ConcurrentHashMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        val entries = map.entriesSnapshot().toSet()

        assertEquals(setOf("a" to 1, "b" to 2), entries)
    }

    @Test
    fun `clear empties the map`() {
        val map = ConcurrentHashMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        map.clear()

        assertEquals(0, map.size())
        assertFalse(map.containsKey("a"))
        assertTrue(map.entriesSnapshot().isEmpty())
    }
}
