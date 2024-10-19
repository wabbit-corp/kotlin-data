package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = LongBuf.TypeSerializer::class)
class LongBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = LongArray(capacity)

    constructor(values: LongArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = LongArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Long> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Long) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: LongArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: LongBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Long) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: LongArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: LongBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Long {
        return buffer[--size]
    }

    fun popLast(count: Int): LongArray {
        val result = LongArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Long {
        return buffer[index]
    }

    operator fun set(index: Int, value: Long) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class TypeSerializer : KSerializer<LongBuf> {
        override val descriptor: SerialDescriptor = ListSerializer(Long.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: LongBuf) {
            encoder.encodeSerializableValue(ListSerializer(Long.serializer()), value.toList())
        }

        override fun deserialize(decoder: Decoder): LongBuf {
            return LongBuf(decoder.decodeSerializableValue(ListSerializer(Long.serializer())).toLongArray())
        }
    }
}