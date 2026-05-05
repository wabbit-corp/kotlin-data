// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(
            listOf(true, false),
            Chunk.fromBooleanArray(booleanArrayOf(true, false)).toList(),
        )
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
        val updated = chunkOf(1, 2, 3, 4).update(1, 20).update(3, 40)

        assertEquals(listOf(1, 20, 3, 40), updated.toList())
        assertEquals(20, updated[1])
        assertEquals(40, updated[3])
    }

    @Test
    fun `append prepend update and plus keep earlier chunks persistent`() {
        val appendBase = chunkOf(1, 2).append(3)
        val appended = appendBase.append(4)
        assertEquals(listOf(1, 2, 3), appendBase.toList())
        assertEquals(listOf(1, 2, 3, 4), appended.toList())

        val plusBase = chunkOf(1, 2) + chunkOf(3)
        val plusExtended = plusBase + chunkOf(4)
        assertEquals(listOf(1, 2, 3), plusBase.toList())
        assertEquals(listOf(1, 2, 3, 4), plusExtended.toList())

        val prependBase = chunkOf(3, 4).prepend(2)
        val prepended = prependBase.prepend(1)
        assertEquals(listOf(2, 3, 4), prependBase.toList())
        assertEquals(listOf(1, 2, 3, 4), prepended.toList())

        val updateBase = chunkOf(1, 2, 3, 4).update(1, 20)
        val updated = updateBase.update(3, 40)
        assertEquals(listOf(1, 20, 3, 4), updateBase.toList())
        assertEquals(listOf(1, 20, 3, 40), updated.toList())
    }

    @Test
    fun `prepend chunk rejects negative indices instead of reading from buffer slots`() {
        val prepended = chunkOf<String?>("tail").prepend(null).prepend("head")

        assertFailsWith<IndexOutOfBoundsException> { prepended[-1] }
    }

    @Test
    fun `chunk equality and hash code are value based across representations`() {
        val arrayChunk = chunkOf(1, 2, 3)
        val concatChunk = chunkOf(1, 2) + chunkOf(3)
        val sliceChunk = chunkOf(0, 1, 2, 3, 4).slice(1, 4)
        val appendChunk = chunkOf(1, 2).append(3)
        val updateChunk = chunkOf(9, 2, 3).update(0, 1)
        val intArrayChunk = Chunk.fromIntArray(intArrayOf(1, 2, 3))

        assertTrue(arrayChunk == concatChunk)
        assertTrue(concatChunk == arrayChunk)
        assertTrue(arrayChunk == sliceChunk)
        assertTrue(sliceChunk == arrayChunk)
        assertTrue(arrayChunk == appendChunk)
        assertTrue(appendChunk == arrayChunk)
        assertTrue(arrayChunk == updateChunk)
        assertTrue(updateChunk == arrayChunk)
        assertTrue(arrayChunk == intArrayChunk)
        assertTrue(intArrayChunk == arrayChunk)

        val expectedHash = arrayChunk.hashCode()
        assertEquals(expectedHash, concatChunk.hashCode())
        assertEquals(expectedHash, sliceChunk.hashCode())
        assertEquals(expectedHash, appendChunk.hashCode())
        assertEquals(expectedHash, updateChunk.hashCode())
        assertEquals(expectedHash, intArrayChunk.hashCode())

        val stringChunk = Chunk.fromString("abc")
        val charChunk = chunkOf('a', 'b', 'c')
        assertTrue(stringChunk == charChunk)
        assertTrue(charChunk == stringChunk)
        assertEquals(charChunk.hashCode(), stringChunk.hashCode())
    }

    @Test
    fun `concat uses a specialized iterator instead of the generic indexed iterator`() {
        val concat = Chunk.Concat(arrayOf(chunkOf(1, 2), chunkOf(3), chunkOf(4, 5)))
        val leafIteratorClass = chunkOf(1, 2).chunkIterator()::class

        val iterator = concat.chunkIterator()

        assertEquals(listOf(1, 2, 3, 4, 5), (0 until iterator.length).map(iterator::nextAt))
        assertTrue(iterator::class != leafIteratorClass)
        assertTrue(concat.slice(1, 4).chunkIterator()::class != leafIteratorClass)
        assertEquals(listOf(1, 2, 3, 4, 5), concat.toList())
    }

    @Test
    fun `chunk is a normal iterable`() {
        val chunk = chunkOf(1, 2) + chunkOf(3, 4)
        val iterated = mutableListOf<Int>()
        for (value in chunk) {
            iterated += value
        }

        assertEquals(listOf(1, 2, 3, 4), iterated)
    }

    @Test
    fun `indexWhere handles concat heavy chunks from an offset`() {
        val chunk =
            Chunk.Concat(arrayOf(chunkOf(10, 20), chunkOf(30), chunkOf(40, 50), chunkOf(60)))

        assertEquals(2, chunk.indexWhere({ it == 30 }))
        assertEquals(4, chunk.indexWhere({ it == 50 }, from = 3))
        assertEquals(-1, chunk.indexWhere({ it == 20 }, from = 2))
    }

    @Test
    fun `foldRight preserves order across concat heavy chunks`() {
        val chunk = Chunk.Concat(arrayOf(chunkOf(1, 2), chunkOf(3), chunkOf(4, 5), chunkOf(6)))

        assertEquals("123456", chunk.foldRight("") { value, acc -> value.toString() + acc })
    }

    @Test
    fun `rebalance builds a balanced concat tree instead of a flat wide node`() {
        val leaves = (0 until 8).map { chunkOf(it) }
        val nested =
            Chunk.Concat(
                arrayOf(
                    Chunk.Concat(arrayOf(leaves[0], leaves[1], leaves[2], leaves[3])),
                    Chunk.Concat(arrayOf(leaves[4], leaves[5], leaves[6], leaves[7])),
                )
            )

        val rebalanced = nested.rebalance()

        assertIs<Chunk.Concat<Int>>(rebalanced)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), rebalanced.toList())
        assertEquals(2, maxConcatWidth(rebalanced))
    }
}

private fun maxConcatWidth(chunk: Chunk<Int>): Int =
    when (chunk) {
        is Chunk.Concat -> maxOf(chunk.chunks.size, chunk.chunks.maxOf(::maxConcatWidth))
        else -> 0
    }
