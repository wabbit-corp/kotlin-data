// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingBufferSpec {
    @Test
    fun `float buffer equality hash and lookup use boxed semantics`() {
        val negativeZero = FloatBuffer(floatArrayOf(-0.0f))
        val positiveZero = FloatBuffer(floatArrayOf(0.0f))
        assertFalse(negativeZero == positiveZero)
        assertFalse(positiveZero == negativeZero)
        assertFalse(negativeZero.hashCode() == positiveZero.hashCode())

        val nan1 = Float.fromBits(0x7fc00000)
        val nan2 = Float.fromBits(0x7fc00001)
        assertEquals(FloatBuffer(floatArrayOf(nan1)), FloatBuffer(floatArrayOf(nan2)))
        assertEquals(
            FloatBuffer(floatArrayOf(nan1)).hashCode(),
            FloatBuffer(floatArrayOf(nan2)).hashCode(),
        )

        val withNaNs = FloatBuffer(floatArrayOf(nan1, -0.0f, 0.0f))

        assertTrue(withNaNs.contains(nan2))
        assertEquals(0, withNaNs.indexOf(nan1))
        assertEquals(0, withNaNs.indexOf(nan2))
        assertEquals(1, withNaNs.indexOf(-0.0f))
        assertEquals(2, withNaNs.indexOf(0.0f))
        assertEquals(1, withNaNs.indexOfLast(-0.0f))
        assertEquals(2, withNaNs.indexOfLast(0.0f))

        val removed = FloatBuffer(floatArrayOf(nan1, nan2, nan1, 1.0f))
        assertTrue(removed.removeAll(nan2))
        assertEquals(listOf(1.0f), removed.toList())

        val distinct = FloatBuffer(floatArrayOf(nan1, nan2, -0.0f, 0.0f, nan1)).distinct()
        assertEquals(listOf(nan1, -0.0f, 0.0f), distinct.toList())

        val sorted = FloatBuffer(floatArrayOf(0.0f, nan1, -0.0f)).sorted()
        assertEquals(0, sorted.binarySearch(-0.0f))
        assertEquals(1, sorted.binarySearch(0.0f))
        assertEquals(2, sorted.binarySearch(nan2))
    }

    @Test
    fun `double buffer equality hash and lookup use boxed semantics`() {
        val negativeZero = DoubleBuffer(doubleArrayOf(-0.0))
        val positiveZero = DoubleBuffer(doubleArrayOf(0.0))
        assertFalse(negativeZero == positiveZero)
        assertFalse(positiveZero == negativeZero)
        assertFalse(negativeZero.hashCode() == positiveZero.hashCode())

        val nan1 = Double.fromBits(0x7ff8000000000000L)
        val nan2 = Double.fromBits(0x7ff8000000000001L)
        assertEquals(DoubleBuffer(doubleArrayOf(nan1)), DoubleBuffer(doubleArrayOf(nan2)))
        assertEquals(
            DoubleBuffer(doubleArrayOf(nan1)).hashCode(),
            DoubleBuffer(doubleArrayOf(nan2)).hashCode(),
        )

        val withNaNs = DoubleBuffer(doubleArrayOf(nan1, -0.0, 0.0))

        assertTrue(withNaNs.contains(nan2))
        assertEquals(0, withNaNs.indexOf(nan1))
        assertEquals(0, withNaNs.indexOf(nan2))
        assertEquals(1, withNaNs.indexOf(-0.0))
        assertEquals(2, withNaNs.indexOf(0.0))
        assertEquals(1, withNaNs.indexOfLast(-0.0))
        assertEquals(2, withNaNs.indexOfLast(0.0))

        val removed = DoubleBuffer(doubleArrayOf(nan1, nan2, nan1, 1.0))
        assertTrue(removed.removeAll(nan2))
        assertEquals(listOf(1.0), removed.toList())

        val distinct = DoubleBuffer(doubleArrayOf(nan1, nan2, -0.0, 0.0, nan1)).distinct()
        assertEquals(listOf(nan1, -0.0, 0.0), distinct.toList())

        val sorted = DoubleBuffer(doubleArrayOf(0.0, nan1, -0.0)).sorted()
        assertEquals(0, sorted.binarySearch(-0.0))
        assertEquals(1, sorted.binarySearch(0.0))
        assertEquals(2, sorted.binarySearch(nan2))
    }
}
