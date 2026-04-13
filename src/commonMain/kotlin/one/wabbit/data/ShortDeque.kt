package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ShortDeque.TypeSerializer::class)
class ShortDeque(initialCapacity: Int = 16) {
    private var capacity: Int = initialCapacity
    private var head: Int = 0
    private var tail: Int = 0
    private var usedSize: Int = 0
    private var buffer = ShortArray(capacity)

    private fun sameValue(left: Short, right: Short): Boolean = left == right

    private fun hashValue(value: Short): Int = value.hashCode()

    private fun requireElement() {
        if (usedSize == 0) {
            throw NoSuchElementException("Deque is empty")
        }
    }

    private fun requirePopCount(count: Int) {
        if (count !in 0..usedSize) {
            throw IndexOutOfBoundsException("Count out of bounds: $count")
        }
    }

    constructor(values: ShortArray) : this(values.size) {
        pushLast(values)
    }

    /**
     * Ensure the underlying buffer can hold at least [needed] elements. If not, increase capacity
     * and re-map all elements so that [head] becomes 0.
     */
    private fun ensureCapacity(needed: Int) {
        if (needed <= capacity) return
        val newCapacity = maxOf(capacity * 3 / 2, needed)

        val newBuffer = ShortArray(newCapacity)
        // copy old elements into new buffer starting at index 0
        for (i in 0 until usedSize) {
            newBuffer[i] = buffer[(head + i) % capacity]
        }
        buffer = newBuffer
        capacity = newCapacity
        head = 0
        tail = usedSize
    }

    fun toList(): List<Short> {
        val result = ArrayList<Short>(usedSize)
        for (i in 0 until usedSize) {
            result.add(buffer[(head + i) % capacity])
        }
        return result
    }

    fun toShortArray(): ShortArray {
        val result = ShortArray(usedSize)
        for (i in 0 until usedSize) {
            result[i] = buffer[(head + i) % capacity]
        }
        return result
    }

    fun isEmpty(): Boolean = usedSize == 0

    fun isNotEmpty(): Boolean = usedSize != 0

    fun clear() {
        head = 0
        tail = 0
        usedSize = 0
    }

    fun peekFirst(): Short {
        requireElement()
        return buffer[head]
    }

    fun peekLast(): Short {
        requireElement()
        return buffer[(tail - 1 + capacity) % capacity]
    }

    /** Push single element to the 'end' (tail). */
    fun pushLast(value: Short) {
        ensureCapacity(usedSize + 1)
        buffer[tail] = value
        tail = (tail + 1) % capacity
        usedSize++
    }

    /** Push an array of elements to the 'end' (tail). */
    fun pushLast(values: ShortArray) {
        ensureCapacity(usedSize + values.size)
        for (v in values) {
            buffer[tail] = v
            tail = (tail + 1) % capacity
        }
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'end' (tail). */
    fun pushLast(values: ShortDeque) {
        ensureCapacity(usedSize + values.size)
        for (i in 0 until values.size) {
            buffer[tail] = values.buffer[(values.head + i) % values.capacity]
            tail = (tail + 1) % capacity
        }
        usedSize += values.size
    }

    /** Push single element to the 'front' (head). */
    fun pushFirst(value: Short) {
        ensureCapacity(usedSize + 1)
        head = (head - 1 + capacity) % capacity
        buffer[head] = value
        usedSize++
    }

    /**
     * Push an array of elements to the 'front' (head), preserving the order so that the first item
     * in [values] ends up at the front.
     */
    fun pushFirst(values: ShortArray) {
        ensureCapacity(usedSize + values.size)
        // Push in reverse order so that the final array
        // has the same element ordering as [values].
        for (i in values.lastIndex downTo 0) {
            head = (head - 1 + capacity) % capacity
            buffer[head] = values[i]
        }
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'front' (head), preserving order. */
    fun pushFirst(values: ShortDeque) {
        ensureCapacity(usedSize + values.size)
        // Similarly, we traverse from the last element to the first in 'values'
        for (i in values.size - 1 downTo 0) {
            head = (head - 1 + capacity) % capacity
            buffer[head] = values.buffer[(values.head + i) % values.capacity]
        }
        usedSize += values.size
    }

    /** Pop one element from the 'end' (tail). */
    fun popLast(): Short {
        requireElement()
        tail = (tail - 1 + capacity) % capacity
        val value = buffer[tail]
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'end' (tail). */
    fun popLast(count: Int): ShortArray {
        requirePopCount(count)
        val result = ShortArray(count)
        for (i in 0 until count) {
            tail = (tail - 1 + capacity) % capacity
            result[count - 1 - i] = buffer[tail]
        }
        usedSize -= count
        return result
    }

    /** Pop one element from the 'front' (head). */
    fun popFirst(): Short {
        requireElement()
        val value = buffer[head]
        head = (head + 1) % capacity
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'front' (head). */
    fun popFirst(count: Int): ShortArray {
        requirePopCount(count)
        val result = ShortArray(count)
        for (i in 0 until count) {
            result[i] = buffer[(head + i) % capacity]
        }
        head = (head + count) % capacity
        usedSize -= count
        return result
    }

    /** Get the element at [index], 0-based from the 'front'. */
    operator fun get(index: Int): Short {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        return buffer[(head + index) % capacity]
    }

    /** Set the element at [index]. */
    operator fun set(index: Int, value: Short) {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        buffer[(head + index) % capacity] = value
    }

    /** Simple iterator that walks from the front to the back of the deque. */
    class Iterator(private val deque: ShortDeque) : kotlin.collections.Iterator<Short> {
        private var index: Int = 0

        override fun hasNext(): Boolean = index < deque.usedSize

        override fun next(): Short {
            if (!hasNext()) throw NoSuchElementException()
            return deque[index++]
        }
    }

    operator fun iterator(): Iterator = Iterator(this)

    val size: Int
        get() = usedSize

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShortDeque) return false
        if (usedSize != other.usedSize) return false
        for (i in 0 until usedSize) {
            val left = buffer[(head + i) % capacity]
            val right = other.buffer[(other.head + i) % other.capacity]
            if (!sameValue(left, right)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until usedSize) {
            result = 31 * result + hashValue(buffer[(head + i) % capacity])
        }
        return result
    }

    /** Expose the current number of elements. */
    val length: Int
        get() = usedSize

    /** Serializer: just store it as a List<Short> internally. */
    class TypeSerializer : KSerializer<ShortDeque> {
        private val listSerializer = ListSerializer(Short.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: ShortDeque) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): ShortDeque {
            val list = decoder.decodeSerializableValue(listSerializer)
            return ShortDeque(list.toShortArray())
        }
    }

    companion object {
        fun empty(): ShortDeque = ShortDeque(0)

        fun of(value: Short): ShortDeque = ShortDeque(shortArrayOf(value))

        fun of(vararg values: Short): ShortDeque = ShortDeque(values)
    }
}

fun ShortArray.toDeque(): ShortDeque = ShortDeque(this)
