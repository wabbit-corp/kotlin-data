// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.CharArraySerializer
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.builtins.ShortArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationSpec {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `bankers queue serialization is based on logical contents`() {
        val left = BankersQueue.fromConsList(consListOf(1, 2)).enqueue(3)
        val right = BankersQueue.empty<Int>().enqueue(1).enqueue(2).enqueue(3)

        val leftJson = json.encodeToString<BankersQueue<Int>>(left)
        val rightJson = json.encodeToString<BankersQueue<Int>>(right)

        assertEquals("[1,2,3]", leftJson)
        assertEquals(leftJson, rightJson)
        assertEquals(left, json.decodeFromString<BankersQueue<Int>>(leftJson))
    }

    @Test
    fun `leftist heap serialization is based on logical contents`() {
        val left = LeftistHeap.of(3, 1, 5, 2)
        val right = LeftistHeap.of(2, 5, 1, 3)

        val leftJson = json.encodeToString<LeftistHeap<Int>>(left)
        val rightJson = json.encodeToString<LeftistHeap<Int>>(right)

        assertEquals("[1,2,3,5]", leftJson)
        assertEquals(leftJson, rightJson)
        assertEquals(left, json.decodeFromString<LeftistHeap<Int>>(leftJson))
    }

    @Test
    fun `chunk serialization is based on logical contents`() {
        val left = chunkOf(1, 2).append(3).update(1, 20)
        val right = Chunk.fromIntArray(intArrayOf(1, 20, 3))

        val leftJson = json.encodeToString<Chunk<Int>>(left)
        val rightJson = json.encodeToString<Chunk<Int>>(right)

        assertEquals("[1,20,3]", leftJson)
        assertEquals(leftJson, rightJson)
        assertEquals(left, json.decodeFromString<Chunk<Int>>(leftJson))
    }

    @Test
    fun `primitive buffers and deques use primitive array serializers`() {
        assertUsesPrimitiveArraySerializer(BooleanArraySerializer().descriptor, BooleanBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(ByteArraySerializer().descriptor, ByteBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(CharArraySerializer().descriptor, CharBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(DoubleArraySerializer().descriptor, DoubleBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(FloatArraySerializer().descriptor, FloatBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(IntArraySerializer().descriptor, IntBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(LongArraySerializer().descriptor, LongBuffer.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(ShortArraySerializer().descriptor, ShortBuffer.TypeSerializer().descriptor)

        assertUsesPrimitiveArraySerializer(BooleanArraySerializer().descriptor, BooleanDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(ByteArraySerializer().descriptor, ByteDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(CharArraySerializer().descriptor, CharDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(DoubleArraySerializer().descriptor, DoubleDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(FloatArraySerializer().descriptor, FloatDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(IntArraySerializer().descriptor, IntDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(LongArraySerializer().descriptor, LongDeque.TypeSerializer().descriptor)
        assertUsesPrimitiveArraySerializer(ShortArraySerializer().descriptor, ShortDeque.TypeSerializer().descriptor)
    }

    private fun assertUsesPrimitiveArraySerializer(expected: SerialDescriptor, actual: SerialDescriptor) {
        assertEquals(expected.serialName, actual.serialName)
    }
}
