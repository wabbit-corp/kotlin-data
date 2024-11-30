package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BooleanBuf.TypeSerializer::class)
class BooleanBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = BooleanArray(capacity)

    constructor(values: BooleanArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = BooleanArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Boolean> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Boolean) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: BooleanArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: BooleanBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Boolean) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: BooleanArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: BooleanBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Boolean {
        return buffer[--size]
    }

    fun popLast(count: Int): BooleanArray {
        val result = BooleanArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Boolean {
        return buffer[index]
    }

    operator fun set(index: Int, value: Boolean) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class Iterator(private val buf: BooleanBuf) : kotlin.collections.Iterator<Boolean> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.size
        override fun next(): Boolean = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    class TypeSerializer : KSerializer<BooleanBuf> {
        private val listSerializer = ListSerializer(Boolean.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: BooleanBuf) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): BooleanBuf {
            return BooleanBuf(decoder.decodeSerializableValue(listSerializer).toBooleanArray())
        }
    }
}