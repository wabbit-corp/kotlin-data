// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DequeGrowthTest {
    @Test
    fun `empty deque access does not leak mutations between callers`() {
        val ints = IntDeque.empty()
        ints.pushLast(1)
        assertEquals(0, IntDeque.empty().length)
        assertEquals(0, IntDeque.empty().size)

        val bytes = ByteDeque.empty()
        bytes.pushLast(1)
        assertEquals(0, ByteDeque.empty().length)
        assertEquals(0, ByteDeque.empty().size)

        val shorts = ShortDeque.empty()
        shorts.pushLast(1)
        assertEquals(0, ShortDeque.empty().length)
        assertEquals(0, ShortDeque.empty().size)

        val longs = LongDeque.empty()
        longs.pushLast(1)
        assertEquals(0, LongDeque.empty().length)
        assertEquals(0, LongDeque.empty().size)

        val floats = FloatDeque.empty()
        floats.pushLast(1f)
        assertEquals(0, FloatDeque.empty().length)
        assertEquals(0, FloatDeque.empty().size)

        val doubles = DoubleDeque.empty()
        doubles.pushLast(1.0)
        assertEquals(0, DoubleDeque.empty().length)
        assertEquals(0, DoubleDeque.empty().size)

        val booleans = BooleanDeque.empty()
        booleans.pushLast(true)
        assertEquals(0, BooleanDeque.empty().length)
        assertEquals(0, BooleanDeque.empty().size)

        val chars = CharDeque.empty()
        chars.pushLast('x')
        assertEquals(0, CharDeque.empty().length)
        assertEquals(0, CharDeque.empty().size)
    }

    @Test
    fun `primitive deques grow past default capacity without losing order`() {
        val booleans = BooleanDeque()
        repeat(17) { booleans.pushLast(it % 2 == 0) }
        assertEquals(17, booleans.length)
        assertEquals(17, booleans.size)
        assertContentEquals(BooleanArray(17) { it % 2 == 0 }, booleans.toBooleanArray())

        val bytes = ByteDeque()
        repeat(17) { bytes.pushLast(it.toByte()) }
        assertEquals(17, bytes.length)
        assertEquals(17, bytes.size)
        assertContentEquals(ByteArray(17) { it.toByte() }, bytes.toByteArray())

        val chars = CharDeque()
        repeat(17) { chars.pushLast(('a'.code + it).toChar()) }
        assertEquals(17, chars.length)
        assertEquals(17, chars.size)
        assertContentEquals(CharArray(17) { ('a'.code + it).toChar() }, chars.toCharArray())

        val doubles = DoubleDeque()
        repeat(17) { doubles.pushLast(it.toDouble()) }
        assertEquals(17, doubles.length)
        assertEquals(17, doubles.size)
        assertContentEquals(DoubleArray(17) { it.toDouble() }, doubles.toDoubleArray())

        val floats = FloatDeque()
        repeat(17) { floats.pushLast(it.toFloat()) }
        assertEquals(17, floats.length)
        assertEquals(17, floats.size)
        assertContentEquals(FloatArray(17) { it.toFloat() }, floats.toFloatArray())

        val ints = IntDeque()
        repeat(17) { ints.pushLast(it) }
        assertEquals(17, ints.length)
        assertEquals(17, ints.size)
        assertContentEquals(IntArray(17) { it }, ints.toIntArray())

        val longs = LongDeque()
        repeat(17) { longs.pushLast(it.toLong()) }
        assertEquals(17, longs.length)
        assertEquals(17, longs.size)
        assertContentEquals(LongArray(17) { it.toLong() }, longs.toLongArray())

        val shorts = ShortDeque()
        repeat(17) { shorts.pushLast(it.toShort()) }
        assertEquals(17, shorts.length)
        assertEquals(17, shorts.size)
        assertContentEquals(ShortArray(17) { it.toShort() }, shorts.toShortArray())
    }

    @Test
    fun `deques expose ordinary queue basics and value semantics`() {
        val ints = IntDeque.empty()
        assertTrue(ints.isEmpty())
        assertFalse(ints.isNotEmpty())
        ints.pushLast(1)
        ints.pushLast(2)
        assertFalse(ints.isEmpty())
        assertTrue(ints.isNotEmpty())
        assertEquals(1, ints.peekFirst())
        assertEquals(2, ints.peekLast())

        val sameInts = IntDeque.of(1, 2)
        assertEquals(sameInts, ints)
        assertEquals(sameInts.hashCode(), ints.hashCode())

        ints.clear()
        assertTrue(ints.isEmpty())
        assertEquals(0, ints.size)

        val negativeZero = FloatDeque.of(-0.0f)
        val positiveZero = FloatDeque.of(0.0f)
        assertFalse(negativeZero == positiveZero)
        assertFalse(negativeZero.hashCode() == positiveZero.hashCode())

        val floatNan1 = FloatDeque.of(Float.fromBits(0x7fc00000))
        val floatNan2 = FloatDeque.of(Float.fromBits(0x7fc00001))
        assertEquals(floatNan1, floatNan2)
        assertEquals(floatNan1.hashCode(), floatNan2.hashCode())

        val doubleNan1 = DoubleDeque.of(Double.fromBits(0x7ff8000000000000L))
        val doubleNan2 = DoubleDeque.of(Double.fromBits(0x7ff8000000000001L))
        assertEquals(doubleNan1, doubleNan2)
        assertEquals(doubleNan1.hashCode(), doubleNan2.hashCode())
    }
}
