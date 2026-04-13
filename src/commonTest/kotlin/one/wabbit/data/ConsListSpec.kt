// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
