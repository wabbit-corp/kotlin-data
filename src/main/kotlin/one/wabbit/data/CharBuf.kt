package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CharBuf.TypeSerializer::class)
class CharBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = CharArray(capacity)

    constructor(values: CharArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = CharArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Char> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Char) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: CharArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: CharBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Char) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: CharArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: CharBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Char {
        return buffer[--size]
    }

    fun popLast(count: Int): CharArray {
        val result = CharArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Char {
        return buffer[index]
    }

    operator fun set(index: Int, value: Char) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class Iterator(private val buf: CharBuf) : kotlin.collections.Iterator<Char> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.size
        override fun next(): Char = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    class TypeSerializer : KSerializer<CharBuf> {
        private val listSerializer = ListSerializer(Char.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: CharBuf) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): CharBuf {
            return CharBuf(decoder.decodeSerializableValue(listSerializer).toCharArray())
        }
    }
}