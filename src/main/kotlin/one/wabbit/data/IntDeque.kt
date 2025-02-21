package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = IntDeque.TypeSerializer::class)
class IntDeque(
    initialCapacity: Int = 16
) {
    private var capacity: Int = initialCapacity
    private var head: Int = 0
    private var tail: Int = 0
    private var size: Int = 0
    private var buffer = IntArray(capacity)

    constructor(values: IntArray) : this(values.size) {
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

        val newBuffer = IntArray(newCapacity)
        // copy old elements into new buffer starting at index 0
        for (i in 0 until size) {
            newBuffer[i] = buffer[(head + i) % capacity]
        }
        buffer = newBuffer
        capacity = newCapacity
        head = 0
        tail = size
    }

    fun toList(): List<Int> {
        val result = ArrayList<Int>(size)
        for (i in 0 until size) {
            result.add(buffer[(head + i) % capacity])
        }
        return result
    }

    fun toIntArray(): IntArray {
        val result = IntArray(size)
        for (i in 0 until size) {
            result[i] = buffer[(head + i) % capacity]
        }
        return result
    }

    /**
     * Push single element to the 'end' (tail).
     */
    fun pushLast(value: Int) {
        ensureCapacity(size + 1)
        buffer[tail] = value
        tail = (tail + 1) % capacity
        size++
    }

    /**
     * Push an array of elements to the 'end' (tail).
     */
    fun pushLast(values: IntArray) {
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
    fun pushLast(values: IntDeque) {
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
    fun pushFirst(value: Int) {
        ensureCapacity(size + 1)
        head = (head - 1 + capacity) % capacity
        buffer[head] = value
        size++
    }

    /**
     * Push an array of elements to the 'front' (head), preserving the order
     * so that the first item in [values] ends up at the front.
     */
    fun pushFirst(values: IntArray) {
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
    fun pushFirst(values: IntDeque) {
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
    fun popLast(): Int {
        require(size > 0) { "Cannot pop from empty deque" }
        tail = (tail - 1 + capacity) % capacity
        val value = buffer[tail]
        size--
        return value
    }

    /**
     * Pop [count] elements from the 'end' (tail).
     */
    fun popLast(count: Int): IntArray {
        require(count <= size) { "Cannot pop more elements than the deque holds" }
        val result = IntArray(count)
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
    fun popFirst(): Int {
        require(size > 0) { "Cannot pop from empty deque" }
        val value = buffer[head]
        head = (head + 1) % capacity
        size--
        return value
    }

    /**
     * Pop [count] elements from the 'front' (head).
     */
    fun popFirst(count: Int): IntArray {
        require(count <= size) { "Cannot pop more elements than the deque holds" }
        val result = IntArray(count)
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
    operator fun get(index: Int): Int {
        require(index in 0 until size) { "Index out of bounds: $index" }
        return buffer[(head + index) % capacity]
    }

    /**
     * Set the element at [index].
     */
    operator fun set(index: Int, value: Int) {
        require(index in 0 until size) { "Index out of bounds: $index" }
        buffer[(head + index) % capacity] = value
    }

    /**
     * Simple iterator that walks from the front to the back of the deque.
     */
    class Iterator(private val deque: IntDeque) : kotlin.collections.Iterator<Int> {
        private var index: Int = 0
        override fun hasNext(): Boolean = index < deque.size
        override fun next(): Int = deque[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    /**
     * Expose the current number of elements.
     */
    val length: Int get() = size

    /**
     * Serializer: just store it as a List<Int> internally.
     */
    class TypeSerializer : KSerializer<IntDeque> {
        private val listSerializer = ListSerializer(Int.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: IntDeque) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): IntDeque {
            val list = decoder.decodeSerializableValue(listSerializer)
            return IntDeque(list.toIntArray())
        }
    }

    companion object {
        val empty: IntDeque = IntDeque(0)

        fun of(value: Int): IntDeque = IntDeque(intArrayOf(value))

        fun of(vararg values: Int): IntDeque = IntDeque(values)
    }
}

fun IntArray.toDeque(): IntDeque = IntDeque(this)
