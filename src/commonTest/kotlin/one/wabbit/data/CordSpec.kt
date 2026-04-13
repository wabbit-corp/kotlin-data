// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordSpec {
    @Test
    fun `cord equality and hash code are logical`() {
        val left = Cord.of("ab").append("cd")
        val right = Cord.of("a") + Cord.of("bcd")

        assertEquals("abcd", left.toString())
        assertTrue(left == right)
        assertTrue(right == left)
        assertEquals(left.hashCode(), right.hashCode())
    }
}
