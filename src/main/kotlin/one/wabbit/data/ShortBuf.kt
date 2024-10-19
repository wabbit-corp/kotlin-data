package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ShortBuf.TypeSerializer::class)
class ShortBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = ShortArray(capacity)

    constructor(values: ShortArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = ShortArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Short> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Short) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: ShortArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: ShortBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Short) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: ShortArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: ShortBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Short {
        return buffer[--size]
    }

    fun popLast(count: Int): ShortArray {
        val result = ShortArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Short {
        return buffer[index]
    }

    operator fun set(index: Int, value: Short) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class TypeSerializer : KSerializer<ShortBuf> {
        override val descriptor: SerialDescriptor = ListSerializer(Short.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: ShortBuf) {
            encoder.encodeSerializableValue(ListSerializer(Short.serializer()), value.toList())
        }

        override fun deserialize(decoder: Decoder): ShortBuf {
            return ShortBuf(decoder.decodeSerializableValue(ListSerializer(Short.serializer())).toShortArray())
        }
    }
}