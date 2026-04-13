// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyListSpec {
    @Test
    fun `lazy list supports ordinary iteration`() {
        val values = mutableListOf<Int>()
        for (value in lazyConsListOf(1, 2, 3)) {
            values += value
        }

        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun `lazy list equality and hash code are logical for finite lists`() {
        val strict = lazyConsListOf(1, 2, 3)
        val delayed = LazyList.from(listOf(1, 2, 3))

        assertTrue(strict == delayed)
        assertTrue(delayed == strict)
        assertEquals(strict.hashCode(), delayed.hashCode())
    }
}
