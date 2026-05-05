// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IteratorsTest {
    // Helper: collect an Iterator into a List without using Sequences.
    private fun <T> Iterator<T>.toList(): List<T> {
        val out = mutableListOf<T>()
        while (this.hasNext()) out += this.next()
        return out
    }

    // --- iteratorOf ------------------------------------------------------------------------------

    @Test
    fun iteratorOf_empty_behaves() {
        val it = iteratorOf()
        assertFalse(it.hasNext(), "Empty iterator should report no elements.")
        assertFailsWith<NoSuchElementException> { it.next() }
    }

    @Test
    fun iteratorOf_single_behaves() {
        val it = iteratorOf(42)
        assertTrue(it.hasNext())
        assertEquals(42, it.next())
        assertFalse(it.hasNext())
        assertFailsWith<NoSuchElementException> { it.next() }
    }

    @Test
    fun iteratorOf_two_three_four_in_order() {
        assertEquals(listOf(1, 2), iteratorOf(1, 2).toList())
        assertEquals(listOf(1, 2, 3), iteratorOf(1, 2, 3).toList())
        assertEquals(listOf(1, 2, 3, 4), iteratorOf(1, 2, 3, 4).toList())
    }

    // --- filter ----------------------------------------------------------------------------------

    @Test
    fun filter_basic_even_numbers() {
        val it = iteratorOf(1, 2, 3, 4).filter { x -> x % 2 == 0 }
        assertEquals(listOf(2, 4), it.toList())
    }

    @Test
    fun filter_allows_nulls() {
        val it = iteratorOf<Int?>(null, 1, null, 2).filter { x -> x == null }
        assertEquals(listOf(null, null), it.toList())
    }

    @Test
    fun filter_hasNext_is_idempotent_and_prefetches() {
        var calls = 0
        val it =
            iteratorOf(1, 2, 3).filter { x ->
                calls++
                x % 2 == 0
            }
        assertTrue(it.hasNext())
        // hasNext had to test 1 (false) and 2 (true)
        assertEquals(2, calls, "hasNext should prefetch until it finds a match.")
        assertTrue(it.hasNext(), "Repeated hasNext should not advance.")
        assertEquals(2, it.next())
        assertFalse(it.hasNext(), "Only 2 is even in [1,2,3].")
    }

    // --- map -------------------------------------------------------------------------------------

    @Test
    fun map_basic() {
        val it = iteratorOf(1, 2, 3).map { x -> x * 10 }
        assertEquals(listOf(10, 20, 30), it.toList())
    }

    @Test
    fun map_retry_after_exception_on_same_value() {
        var first = true
        val it =
            iteratorOf(5).map { x ->
                if (first) {
                    first = false
                    throw RuntimeException("boom")
                } else {
                    x * 2
                }
            }

        // First attempt throws:
        assertFailsWith<RuntimeException> { it.next() }

        // Implementation should "remember" the same value and retry:
        assertTrue(it.hasNext(), "Iterator should still have the pending value after error.")
        assertEquals(10, it.next())
        assertFalse(it.hasNext())
    }

    @Test
    fun map_propagates_virtual_machine_error() {
        val it = iteratorOf(5).map<Int, Int> { throw StackOverflowError("kaboom") }
        assertFailsWith<StackOverflowError> { it.next() }
    }

    // --- flatMap ---------------------------------------------------------------------------------

    @Test
    fun flatMap_basic_concatenation() {
        val it = iteratorOf(1, 2).flatMap { v -> iteratorOf(v, -v) }
        // EXPECTED: [1, -1, 2, -2]
        // Current implementation prematurely stops after the first inner iterator. This should fail
        // until flatMap is fixed to advance past an exhausted inner iterator.
        assertEquals(listOf(1, -1, 2, -2), it.toList())
    }

    @Test
    fun flatMap_skips_empty_inners() {
        val it = iteratorOf(1, 2).flatMap { v -> if (v == 1) iteratorOf() else iteratorOf(42) }
        // EXPECTED: [42]
        // Current implementation returns false as soon as it hits an empty inner, ignoring
        // remaining upstream.
        assertEquals(listOf(42), it.toList())
    }

    @Test
    fun flatMap_hasNext_on_empty_upstream_returns_false_not_throw() {
        val it = iteratorOf().flatMap { iteratorOf(1) }
        // EXPECTED: false, not an exception.
        // Current implementation may throw NoSuchElementException from hasNext(); this should fail
        // until fixed.
        assertFalse(it.hasNext())
    }

    @Test
    fun flatMap_retry_after_exception_on_same_value() {
        var first = true
        val it =
            iteratorOf(7).flatMap { x ->
                if (first) {
                    first = false
                    throw RuntimeException("try again")
                } else {
                    iteratorOf(x, x + 1)
                }
            }

        assertFailsWith<RuntimeException> { it.next() }
        assertTrue(
            it.hasNext(),
            "After mapper throws once, iterator should retry the same upstream value.",
        )
        assertEquals(listOf(7, 8), it.toList())
    }

    // --- zip -------------------------------------------------------------------------------------

    @Test
    fun zip_stops_at_shorter_input() {
        val it = iteratorOf(1, 2).zip(iteratorOf("a", "b", "c"))
        assertEquals(listOf(1 to "a", 2 to "b"), it.toList())
    }

    @Test
    fun zip_next_after_exhaustion_throws() {
        val it = iteratorOf(1).zip(iteratorOf("x"))
        assertEquals(1 to "x", it.next())
        assertFalse(it.hasNext())
        assertFailsWith<NoSuchElementException> { it.next() }
    }

    @Test
    fun map_and_filter_composition() {
        val it = iteratorOf(1, 2, 3, 4).map { it * it }.filter { it % 2 == 0 }
        assertEquals(listOf(4, 16), it.toList())
    }

    @Test
    fun hasNext_idempotent_on_map() {
        val it = iteratorOf(9).map { it }
        assertTrue(it.hasNext())
        assertTrue(it.hasNext())
        assertEquals(9, it.next())
        assertFalse(it.hasNext())
    }
}
