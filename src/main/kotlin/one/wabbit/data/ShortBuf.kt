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

    class Iterator(private val buf: ShortBuf) : kotlin.collections.Iterator<Short> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.size
        override fun next(): Short = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    class TypeSerializer : KSerializer<ShortBuf> {
        private val listSerializer = ListSerializer(Short.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: ShortBuf) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): ShortBuf {
            return ShortBuf(decoder.decodeSerializableValue(listSerializer).toShortArray())
        }
    }
}