package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ByteBuf.TypeSerializer::class)
class ByteBuf(var capacity: Int = 16) {
    private var size: Int = 0
    private var buffer = ByteArray(capacity)

    constructor(values: ByteArray) : this(values.size) {
        values.copyInto(buffer)
        size = values.size
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity > this.capacity) {
            val newCapacity = this.capacity * 3 / 2
            val newBuffer = ByteArray(newCapacity)
            buffer.copyInto(newBuffer)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun toList(): List<Byte> {
        return buffer.copyOf(size).toList()
    }

    fun pushLast(value: Byte) {
        ensureCapacity(size + 1)
        buffer[size++] = value
    }

    fun pushLast(values: ByteArray) {
        ensureCapacity(size + values.size)
        values.copyInto(buffer, size)
        size += values.size
    }

    fun pushLast(values: ByteBuf) {
        ensureCapacity(size + values.size)
        values.buffer.copyInto(buffer, size)
        size += values.size
    }

    fun pushFirst(value: Byte) {
        ensureCapacity(size + 1)
        buffer.copyInto(buffer, 1, 0, size)
        buffer[0] = value
        size++
    }

    fun pushFirst(values: ByteArray) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.copyInto(buffer, 0)
        size += values.size
    }

    fun pushFirst(values: ByteBuf) {
        ensureCapacity(size + values.size)
        buffer.copyInto(buffer, values.size, 0, size)
        values.buffer.copyInto(buffer, 0)
        size += values.size
    }

    fun popLast(): Byte {
        return buffer[--size]
    }

    fun popLast(count: Int): ByteArray {
        val result = ByteArray(count)
        buffer.copyInto(result, 0, size - count, size)
        size -= count
        return result
    }

    operator fun get(index: Int): Byte {
        return buffer[index]
    }

    operator fun set(index: Int, value: Byte) {
        require(index in 0..<size) { "Index out of bounds: $index" }
        buffer[index] = value
    }

    class TypeSerializer : KSerializer<ByteBuf> {
        override val descriptor: SerialDescriptor = ListSerializer(Byte.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: ByteBuf) {
            encoder.encodeSerializableValue(ListSerializer(Byte.serializer()), value.toList())
        }

        override fun deserialize(decoder: Decoder): ByteBuf {
            return ByteBuf(decoder.decodeSerializableValue(ListSerializer(Byte.serializer())).toByteArray())
        }
    }
}