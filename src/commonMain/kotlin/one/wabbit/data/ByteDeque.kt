package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Mutable ring-buffer deque for primitive bytes.
 *
 * The deque owns its internal buffer. Constructors and bulk push operations copy values into that
 * buffer, so the deque never aliases caller-provided arrays or other deque storage. This type is
 * mutable; methods such as `push*`, `pop*`, `set`, and [clear] update the receiver in place.
 *
 * Complexity notes:
 * - [peekFirst], [peekLast], [get], and [set] are O(1)
 * - single-element push/pop operations are amortized O(1)
 * - bulk push/pop operations are O(n) in the number of affected elements
 * - resizing copies all elements and is therefore O(size)
 *
 * Exception contracts:
 * - [peekFirst], [peekLast], [popFirst], and [popLast] throw [NoSuchElementException] on an empty
 *   deque
 * - [get], [set], [popFirst(count)], and [popLast(count)] throw [IndexOutOfBoundsException] for
 *   invalid indices or counts
 *
 * Negative indexing is never supported. Negative counts are rejected rather than clamped.
 */
@Serializable(with = ByteDeque.TypeSerializer::class)
class ByteDeque(initialCapacity: Int = 16) {
    private var capacity: Int = initialCapacity
    private var head: Int = 0
    private var tail: Int = 0
    private var usedSize: Int = 0
    private var buffer = ByteArray(capacity)

    private fun sameValue(left: Byte, right: Byte): Boolean = left == right

    private fun hashValue(value: Byte): Int = value.hashCode()

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

    private fun advance(index: Int, amount: Int): Int {
        if (capacity == 0 || amount == 0) return index
        return (index + (amount % capacity)) % capacity
    }

    private fun retreat(index: Int, amount: Int): Int {
        if (capacity == 0 || amount == 0) return index
        return (index - (amount % capacity) + capacity) % capacity
    }

    private fun copyLinearIntoRing(
        source: ByteArray,
        sourceStart: Int,
        count: Int,
        destinationIndex: Int,
    ) {
        if (count == 0) return
        val start = if (destinationIndex >= capacity) destinationIndex % capacity else destinationIndex
        val firstPart = minOf(count, capacity - start)
        source.copyInto(buffer, start, sourceStart, sourceStart + firstPart)
        val remaining = count - firstPart
        if (remaining > 0) {
            source.copyInto(buffer, 0, sourceStart + firstPart, sourceStart + count)
        }
    }

    private fun copyRingIntoLinear(
        source: ByteArray,
        sourceCapacity: Int,
        sourceIndex: Int,
        count: Int,
        destination: ByteArray,
        destinationOffset: Int = 0,
    ) {
        if (count == 0) return
        val firstPart = minOf(count, sourceCapacity - sourceIndex)
        source.copyInto(destination, destinationOffset, sourceIndex, sourceIndex + firstPart)
        val remaining = count - firstPart
        if (remaining > 0) {
            source.copyInto(destination, destinationOffset + firstPart, 0, remaining)
        }
    }

    private fun copyRingIntoRing(
        source: ByteArray,
        sourceCapacity: Int,
        sourceIndex: Int,
        count: Int,
        destinationIndex: Int,
    ) {
        if (count == 0) return
        val firstPart = minOf(count, sourceCapacity - sourceIndex)
        copyLinearIntoRing(source, sourceIndex, firstPart, destinationIndex)
        val remaining = count - firstPart
        if (remaining > 0) {
            copyLinearIntoRing(source, 0, remaining, destinationIndex + firstPart)
        }
    }

    constructor(values: ByteArray) : this(values.size) {
        pushLast(values)
    }

    /**
     * Ensure the underlying buffer can hold at least [needed] elements. If not, increase capacity
     * and re-map all elements so that [head] becomes 0.
     */
    private fun ensureCapacity(needed: Int) {
        if (needed <= capacity) return
        val newCapacity = maxOf(capacity * 3 / 2, needed)

        val newBuffer = ByteArray(newCapacity)
        copyRingIntoLinear(buffer, capacity, head, usedSize, newBuffer)
        buffer = newBuffer
        capacity = newCapacity
        head = 0
        tail = usedSize
    }

    fun toList(): List<Byte> {
        val result = ArrayList<Byte>(usedSize)
        for (i in 0 until usedSize) {
            result.add(buffer[(head + i) % capacity])
        }
        return result
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(usedSize)
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

    fun peekFirst(): Byte {
        requireElement()
        return buffer[head]
    }

    fun peekLast(): Byte {
        requireElement()
        return buffer[(tail - 1 + capacity) % capacity]
    }

    /** Push single element to the 'end' (tail). */
    fun pushLast(value: Byte) {
        ensureCapacity(usedSize + 1)
        buffer[tail] = value
        tail = (tail + 1) % capacity
        usedSize++
    }

    /** Push an array of elements to the 'end' (tail). */
    fun pushLast(values: ByteArray) {
        ensureCapacity(usedSize + values.size)
        copyLinearIntoRing(values, 0, values.size, tail)
        tail = advance(tail, values.size)
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'end' (tail). */
    fun pushLast(values: ByteDeque) {
        ensureCapacity(usedSize + values.size)
        val sourceSize = values.usedSize
        copyRingIntoRing(values.buffer, values.capacity, values.head, sourceSize, tail)
        tail = advance(tail, sourceSize)
        usedSize += sourceSize
    }

    /** Push single element to the 'front' (head). */
    fun pushFirst(value: Byte) {
        ensureCapacity(usedSize + 1)
        head = (head - 1 + capacity) % capacity
        buffer[head] = value
        usedSize++
    }

    /**
     * Push an array of elements to the 'front' (head), preserving the order so that the first item
     * in [values] ends up at the front.
     */
    fun pushFirst(values: ByteArray) {
        ensureCapacity(usedSize + values.size)
        head = retreat(head, values.size)
        copyLinearIntoRing(values, 0, values.size, head)
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'front' (head), preserving order. */
    fun pushFirst(values: ByteDeque) {
        ensureCapacity(usedSize + values.size)
        val sourceSize = values.usedSize
        head = retreat(head, sourceSize)
        copyRingIntoRing(values.buffer, values.capacity, values.head, sourceSize, head)
        usedSize += sourceSize
    }

    /** Pop one element from the 'end' (tail). */
    fun popLast(): Byte {
        requireElement()
        tail = (tail - 1 + capacity) % capacity
        val value = buffer[tail]
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'end' (tail). */
    fun popLast(count: Int): ByteArray {
        requirePopCount(count)
        val result = ByteArray(count)
        val newTail = retreat(tail, count)
        copyRingIntoLinear(buffer, capacity, newTail, count, result)
        tail = newTail
        usedSize -= count
        return result
    }

    /** Pop one element from the 'front' (head). */
    fun popFirst(): Byte {
        requireElement()
        val value = buffer[head]
        head = (head + 1) % capacity
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'front' (head). */
    fun popFirst(count: Int): ByteArray {
        requirePopCount(count)
        val result = ByteArray(count)
        copyRingIntoLinear(buffer, capacity, head, count, result)
        head = advance(head, count)
        usedSize -= count
        return result
    }

    /** Get the element at [index], 0-based from the 'front'. */
    operator fun get(index: Int): Byte {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        return buffer[(head + index) % capacity]
    }

    /** Set the element at [index]. */
    operator fun set(index: Int, value: Byte) {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        buffer[(head + index) % capacity] = value
    }

    /** Simple iterator that walks from the front to the back of the deque. */
    class Iterator(private val deque: ByteDeque) : kotlin.collections.Iterator<Byte> {
        private var index: Int = 0

        override fun hasNext(): Boolean = index < deque.usedSize

        override fun next(): Byte {
            if (!hasNext()) throw NoSuchElementException()
            return deque[index++]
        }
    }

    operator fun iterator(): Iterator = Iterator(this)

    val size: Int
        get() = usedSize

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteDeque) return false
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

    /** Serializer: store it using the primitive array serializer to avoid boxing every element. */
    class TypeSerializer : KSerializer<ByteDeque> {
        private val arraySerializer = ByteArraySerializer()
        override val descriptor: SerialDescriptor = arraySerializer.descriptor

        override fun serialize(encoder: Encoder, value: ByteDeque) {
            encoder.encodeSerializableValue(arraySerializer, value.toByteArray())
        }

        override fun deserialize(decoder: Decoder): ByteDeque {
            val array = decoder.decodeSerializableValue(arraySerializer)
            return ByteDeque(array)
        }
    }

    companion object {
        fun empty(): ByteDeque = ByteDeque(0)

        fun of(value: Byte): ByteDeque = ByteDeque(byteArrayOf(value))

        fun of(vararg values: Byte): ByteDeque = ByteDeque(values)
    }
}

fun ByteArray.toDeque(): ByteDeque = ByteDeque(this)
