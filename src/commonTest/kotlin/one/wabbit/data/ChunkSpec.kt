package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChunkSpec {
    @Test
    fun `chunkOf supports concat slice and options`() {
        val chunk = chunkOf(1, 2, 3) + chunkOf(4, 5)

        assertEquals(listOf(1, 2, 3, 4, 5), chunk.toList())
        assertEquals(listOf(2, 3, 4), chunk.slice(1, 4).toList())
        assertEquals(Some(1), chunk.headOption())
        assertEquals(Some(5), chunk.lastOption())
    }

    @Test
    fun `primitive and string factories work`() {
        assertEquals(listOf<Byte>(1, 2, 3), Chunk.fromByteArray(byteArrayOf(1, 2, 3)).toList())
        assertEquals(listOf(true, false), Chunk.fromBooleanArray(booleanArrayOf(true, false)).toList())
        assertEquals("abc", Chunk.fromString("abc").toStringChunk())
    }

    @Test
    fun `map filter partition and unfold work`() {
        val values = chunkOf(1, 2, 3, 4)

        assertEquals(listOf(2, 4, 6, 8), values.map { it * 2 }.toList())
        assertEquals(listOf(2, 4), values.filter { it % 2 == 0 }.toList())

        val (left, right) = values.partitionMap { if (it % 2 == 0) Right(it) else Left(it) }
        assertEquals(listOf(1, 3), left.toList())
        assertEquals(listOf(2, 4), right.toList())

        val unfolded = Chunk.unfold(0) { n -> if (n == 4) null else n to (n + 1) }
        assertEquals(listOf(0, 1, 2, 3), unfolded.toList())
    }

    @Test
    fun `builder and iterators produce slices`() {
        val builder = ChunkBuilder<Int>()
        builder += 10
        builder += 20
        builder.add(30)

        val chunk = builder.result()
        val iterator = chunk.chunkIterator().sliceIterator(1, 2)

        assertIs<Chunk.NonEmpty<Int>>(chunk)
        assertTrue(iterator.hasNextAt(0))
        assertEquals(20, iterator.nextAt(0))
        assertEquals(30, iterator.nextAt(1))
    }

    @Test
    fun `append and prepend use optimized chunk nodes`() {
        val appended = chunkOf(1, 2).append(3).append(4)
        val prepended = chunkOf(3, 4).prepend(2).prepend(1)

        assertEquals(listOf(1, 2, 3, 4), appended.toList())
        assertEquals(listOf(1, 2, 3, 4), prepended.toList())
    }

    @Test
    fun `update overlays values without materializing immediately`() {
        val updated = chunkOf(1, 2, 3, 4)
            .update(1, 20)
            .update(3, 40)

        assertEquals(listOf(1, 20, 3, 40), updated.toList())
        assertEquals(20, updated[1])
        assertEquals(40, updated[3])
    }
}
