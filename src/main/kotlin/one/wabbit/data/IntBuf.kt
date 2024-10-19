package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = IntBuf.TypeSerializer::class)
class IntBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = IntArray(capacity)

    constructor(values: IntArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = IntArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Int> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Int) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: IntArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: IntBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Int) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: IntArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: IntBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Int {
        return buffer[--size]
    }

    fun popLast(count: Int): IntArray {
        val result = IntArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Int {
        return buffer[index]
    }

    operator fun set(index: Int, value: Int) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class TypeSerializer : KSerializer<IntBuf> {
        override val descriptor: SerialDescriptor = ListSerializer(Int.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: IntBuf) {
            encoder.encodeSerializableValue(ListSerializer(Int.serializer()), value.toList())
        }

        override fun deserialize(decoder: Decoder): IntBuf {
            return IntBuf(decoder.decodeSerializableValue(ListSerializer(Int.serializer())).toIntArray())
        }
    }
}