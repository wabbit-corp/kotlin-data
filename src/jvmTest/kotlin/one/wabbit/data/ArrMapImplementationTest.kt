package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertSame

class ArrMapImplementationTest {
    @Test
    fun `put reuses hashes array when replacing an existing value`() {
        val original = ArrMap.from(mapOf("a" to 1, "b" to 2))
        val replaced = original.put("a", 9)

        assertSame(hashesOf(original), hashesOf(replaced))
    }

    private fun hashesOf(map: ArrMap<*, *>): IntArray {
        val field = ArrMap::class.java.getDeclaredField("hashes")
        field.isAccessible = true
        return field.get(map) as IntArray
    }
}
