package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ByteDeque.TypeSerializer::class)
class ByteDeque(
    initialCapacity: Int = 16
) {
    private var capacity: Int = initialCapacity
    private var head: Int = 0
    private var tail: Int = 0
    private var size: Int = 0
    private var buffer = ByteArray(capacity)

    constructor(values: ByteArray) : this(values.size) {
        pushLast(values)
    }

    /**
     * Ensure the underlying buffer can hold at least [needed] elements.
     * If not, increase capacity and re-map all elements so that [head] becomes 0.
     */
    private fun ensureCapacity(needed: Int) {
        if (needed <= capacity) return
        val newCapacity = capacity * 3 / 2
            .coerceAtLeast(needed)

        val newBuffer = ByteArray(newCapacity)
        // copy old elements into new buffer starting at index 0
        for (i in 0 until size) {
            newBuffer[i] = buffer[(head + i) % capacity]
        }
        buffer = newBuffer
        capacity = newCapacity
        head = 0
        tail = size
    }

    fun toList(): List<Byte> {
        val result = ArrayList<Byte>(size)
        for (i in 0 until size) {
            result.add(buffer[(head + i) % capacity])
        }
        return result
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = buffer[(head + i) % capacity]
        }
        return result
    }

    /**
     * Push single element to the 'end' (tail).
     */
    fun pushLast(value: Byte) {
        ensureCapacity(size + 1)
        buffer[tail] = value
        tail = (tail + 1) % capacity
        size++
    }

    /**
     * Push an array of elements to the 'end' (tail).
     */
    fun pushLast(values: ByteArray) {
        ensureCapacity(size + values.size)
        for (v in values) {
            buffer[tail] = v
            tail = (tail + 1) % capacity
        }
        size += values.size
    }

    /**
     * Push all elements of another Deque to the 'end' (tail).
     */
    fun pushLast(values: ByteDeque) {
        ensureCapacity(size + values.size)
        for (i in 0 until values.size) {
            buffer[tail] = values.buffer[(values.head + i) % values.capacity]
            tail = (tail + 1) % capacity
        }
        size += values.size
    }

    /**
     * Push single element to the 'front' (head).
     */
    fun pushFirst(value: Byte) {
        ensureCapacity(size + 1)
        head = (head - 1 + capacity) % capacity
        buffer[head] = value
        size++
    }

    /**
     * Push an array of elements to the 'front' (head), preserving the order
     * so that the first item in [values] ends up at the front.
     */
    fun pushFirst(values: ByteArray) {
        ensureCapacity(size + values.size)
        // Push in reverse order so that the final array
        // has the same element ordering as [values].
        for (i in values.lastIndex downTo 0) {
            head = (head - 1 + capacity) % capacity
            buffer[head] = values[i]
        }
        size += values.size
    }

    /**
     * Push all elements of another Deque to the 'front' (head), preserving order.
     */
    fun pushFirst(values: ByteDeque) {
        ensureCapacity(size + values.size)
        // Similarly, we traverse from the last element to the first in 'values'
        for (i in values.size - 1 downTo 0) {
            head = (head - 1 + capacity) % capacity
            buffer[head] = values.buffer[(values.head + i) % values.capacity]
        }
        size += values.size
    }

    /**
     * Pop one element from the 'end' (tail).
     */
    fun popLast(): Byte {
        require(size > 0) { "Cannot pop from empty deque" }
        tail = (tail - 1 + capacity) % capacity
        val value = buffer[tail]
        size--
        return value
    }

    /**
     * Pop [count] elements from the 'end' (tail).
     */
    fun popLast(count: Int): ByteArray {
        require(count <= size) { "Cannot pop more elements than the deque holds" }
        val result = ByteArray(count)
        for (i in 0 until count) {
            tail = (tail - 1 + capacity) % capacity
            result[count - 1 - i] = buffer[tail]
        }
        size -= count
        return result
    }

    /**
     * Pop one element from the 'front' (head).
     */
    fun popFirst(): Byte {
        require(size > 0) { "Cannot pop from empty deque" }
        val value = buffer[head]
        head = (head + 1) % capacity
        size--
        return value
    }

    /**
     * Pop [count] elements from the 'front' (head).
     */
    fun popFirst(count: Int): ByteArray {
        require(count <= size) { "Cannot pop more elements than the deque holds" }
        val result = ByteArray(count)
        for (i in 0 until count) {
            result[i] = buffer[(head + i) % capacity]
        }
        head = (head + count) % capacity
        size -= count
        return result
    }

    /**
     * Get the element at [index], 0-based from the 'front'.
     */
    operator fun get(index: Int): Byte {
        require(index in 0 until size) { "Index out of bounds: $index" }
        return buffer[(head + index) % capacity]
    }

    /**
     * Set the element at [index].
     */
    operator fun set(index: Int, value: Byte) {
        require(index in 0 until size) { "Index out of bounds: $index" }
        buffer[(head + index) % capacity] = value
    }

    /**
     * Simple iterator that walks from the front to the back of the deque.
     */
    class Iterator(private val deque: ByteDeque) : kotlin.collections.Iterator<Byte> {
        private var index: Int = 0
        override fun hasNext(): Boolean = index < deque.size
        override fun next(): Byte = deque[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    /**
     * Expose the current number of elements.
     */
    val length: Int get() = size

    /**
     * Serializer: just store it as a List<Byte> internally.
     */
    class TypeSerializer : KSerializer<ByteDeque> {
        private val listSerializer = ListSerializer(Byte.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: ByteDeque) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): ByteDeque {
            val list = decoder.decodeSerializableValue(listSerializer)
            return ByteDeque(list.toByteArray())
        }
    }

    companion object {
        val empty: ByteDeque = ByteDeque(0)

        fun of(value: Byte): ByteDeque = ByteDeque(byteArrayOf(value))

        fun of(vararg values: Byte): ByteDeque = ByteDeque(values)
    }
}

fun ByteArray.toDeque(): ByteDeque = ByteDeque(this)
