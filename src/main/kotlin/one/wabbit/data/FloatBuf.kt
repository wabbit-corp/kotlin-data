package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = FloatBuf.TypeSerializer::class)
class FloatBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = FloatArray(capacity)

    constructor(values: FloatArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = FloatArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Float> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Float) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: FloatArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: FloatBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Float) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: FloatArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: FloatBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Float {
        return buffer[--size]
    }

    fun popLast(count: Int): FloatArray {
        val result = FloatArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Float {
        return buffer[index]
    }

    operator fun set(index: Int, value: Float) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class TypeSerializer : KSerializer<FloatBuf> {
        override val descriptor: SerialDescriptor = ListSerializer(Float.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: FloatBuf) {
            encoder.encodeSerializableValue(ListSerializer(Float.serializer()), value.toList())
        }

        override fun deserialize(decoder: Decoder): FloatBuf {
            return FloatBuf(decoder.decodeSerializableValue(ListSerializer(Float.serializer())).toFloatArray())
        }
    }
}