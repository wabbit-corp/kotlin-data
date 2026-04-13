// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChainSpec {
    @Test
    fun `append and prepend use chain payloads and preserve order`() {
        val prepended = Chain.of(2).prepend(Chain.of(1))
        val appended = Chain.of(1).append(Chain.of(2))

        assertEquals(listOf(1, 2), prepended.toList())
        assertEquals(listOf(1, 2), appended.toList())
    }

    @Test
    fun `append and prepend size internal stack for deeper chains`() {
        val deepLeft = (((Chain.of(1) + Chain.of(2)) + Chain.of(3)) + Chain.of(4)) + Chain.of(5)
        val deepRight = (((Chain.of(2) + Chain.of(3)) + Chain.of(4)) + Chain.of(5)) + Chain.of(6)

        val prepended = Chain.of(6).prepend(deepLeft)
        val appended = Chain.of(1).append(deepRight)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), prepended.toList())
        assertEquals(listOf(1, 2, 3, 4, 5, 6), appended.toList())
    }

    @Test
    fun `chain string equality and hash code are value based`() {
        val left = Chain.of(1).append(Chain.of(2)).append(Chain.of(3))
        val right = Chain.fromList(listOf(1, 2, 3))
        val different = Chain.fromList(listOf(1, 2, 4))

        assertEquals("Chain(1, 2, 3)", left.toString())
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
        assertFalse(left == different)
    }
}
