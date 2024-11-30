package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DoubleBuf.TypeSerializer::class)
class DoubleBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = DoubleArray(capacity)

    constructor(values: DoubleArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = DoubleArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Double> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Double) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: DoubleArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: DoubleBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Double) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: DoubleArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: DoubleBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Double {
        return buffer[--size]
    }

    fun popLast(count: Int): DoubleArray {
        val result = DoubleArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Double {
        return buffer[index]
    }

    operator fun set(index: Int, value: Double) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class Iterator(private val buf: DoubleBuf) : kotlin.collections.Iterator<Double> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.size
        override fun next(): Double = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    class TypeSerializer : KSerializer<DoubleBuf> {
        private val listSerializer = ListSerializer(Double.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: DoubleBuf) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): DoubleBuf {
            return DoubleBuf(decoder.decodeSerializableValue(listSerializer).toDoubleArray())
        }
    }
}