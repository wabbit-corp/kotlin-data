package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Mutable ring-buffer deque for primitive booleans.
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
@Serializable(with = BooleanDeque.TypeSerializer::class)
class BooleanDeque(initialCapacity: Int = 16) {
    private var capacity: Int = initialCapacity
    private var head: Int = 0
    private var tail: Int = 0
    private var usedSize: Int = 0
    private var buffer = BooleanArray(capacity)

    private fun sameValue(left: Boolean, right: Boolean): Boolean = left == right

    private fun hashValue(value: Boolean): Int = value.hashCode()

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
        source: BooleanArray,
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
        source: BooleanArray,
        sourceCapacity: Int,
        sourceIndex: Int,
        count: Int,
        destination: BooleanArray,
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
        source: BooleanArray,
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

    constructor(values: BooleanArray) : this(values.size) {
        pushLast(values)
    }

    /**
     * Ensure the underlying buffer can hold at least [needed] elements. If not, increase capacity
     * and re-map all elements so that [head] becomes 0.
     */
    private fun ensureCapacity(needed: Int) {
        if (needed <= capacity) return
        val newCapacity = maxOf(capacity * 3 / 2, needed)

        val newBuffer = BooleanArray(newCapacity)
        copyRingIntoLinear(buffer, capacity, head, usedSize, newBuffer)
        buffer = newBuffer
        capacity = newCapacity
        head = 0
        tail = usedSize
    }

    /**
     * Materialize this deque from front to back as a list.
     */
    fun toList(): List<Boolean> {
        val result = ArrayList<Boolean>(usedSize)
        for (i in 0 until usedSize) {
            result.add(buffer[(head + i) % capacity])
        }
        return result
    }

    /**
     * Copy deque contents from front to back into a primitive array.
     */
    fun toBooleanArray(): BooleanArray {
        val result = BooleanArray(usedSize)
        for (i in 0 until usedSize) {
            result[i] = buffer[(head + i) % capacity]
        }
        return result
    }

    /**
     * Return whether this deque has no elements.
     */
    fun isEmpty(): Boolean = usedSize == 0

    /**
     * Return whether this deque has at least one element.
     */
    fun isNotEmpty(): Boolean = usedSize != 0

    /**
     * Remove all elements while retaining current capacity.
     */
    fun clear() {
        head = 0
        tail = 0
        usedSize = 0
    }

    /**
     * Return the front element.
     * @throws NoSuchElementException when this deque is empty.
     */
    fun peekFirst(): Boolean {
        requireElement()
        return buffer[head]
    }

    /**
     * Return the back element.
     * @throws NoSuchElementException when this deque is empty.
     */
    fun peekLast(): Boolean {
        requireElement()
        return buffer[(tail - 1 + capacity) % capacity]
    }

    /** Push single element to the 'end' (tail). */
    fun pushLast(value: Boolean) {
        ensureCapacity(usedSize + 1)
        buffer[tail] = value
        tail = (tail + 1) % capacity
        usedSize++
    }

    /** Push an array of elements to the 'end' (tail). */
    fun pushLast(values: BooleanArray) {
        ensureCapacity(usedSize + values.size)
        copyLinearIntoRing(values, 0, values.size, tail)
        tail = advance(tail, values.size)
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'end' (tail). */
    fun pushLast(values: BooleanDeque) {
        ensureCapacity(usedSize + values.size)
        val sourceSize = values.usedSize
        copyRingIntoRing(values.buffer, values.capacity, values.head, sourceSize, tail)
        tail = advance(tail, sourceSize)
        usedSize += sourceSize
    }

    /** Push single element to the 'front' (head). */
    fun pushFirst(value: Boolean) {
        ensureCapacity(usedSize + 1)
        head = (head - 1 + capacity) % capacity
        buffer[head] = value
        usedSize++
    }

    /**
     * Push an array of elements to the 'front' (head), preserving the order so that the first item
     * in [values] ends up at the front.
     */
    fun pushFirst(values: BooleanArray) {
        ensureCapacity(usedSize + values.size)
        head = retreat(head, values.size)
        copyLinearIntoRing(values, 0, values.size, head)
        usedSize += values.size
    }

    /** Push all elements of another Deque to the 'front' (head), preserving order. */
    fun pushFirst(values: BooleanDeque) {
        ensureCapacity(usedSize + values.size)
        val sourceSize = values.usedSize
        head = retreat(head, sourceSize)
        copyRingIntoRing(values.buffer, values.capacity, values.head, sourceSize, head)
        usedSize += sourceSize
    }

    /** Pop one element from the 'end' (tail). */
    fun popLast(): Boolean {
        requireElement()
        tail = (tail - 1 + capacity) % capacity
        val value = buffer[tail]
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'end' (tail). */
    fun popLast(count: Int): BooleanArray {
        requirePopCount(count)
        val result = BooleanArray(count)
        val newTail = retreat(tail, count)
        copyRingIntoLinear(buffer, capacity, newTail, count, result)
        tail = newTail
        usedSize -= count
        return result
    }

    /** Pop one element from the 'front' (head). */
    fun popFirst(): Boolean {
        requireElement()
        val value = buffer[head]
        head = (head + 1) % capacity
        usedSize--
        return value
    }

    /** Pop [count] elements from the 'front' (head). */
    fun popFirst(count: Int): BooleanArray {
        requirePopCount(count)
        val result = BooleanArray(count)
        copyRingIntoLinear(buffer, capacity, head, count, result)
        head = advance(head, count)
        usedSize -= count
        return result
    }

    /** Get the element at [index], 0-based from the 'front'. */
    operator fun get(index: Int): Boolean {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        return buffer[(head + index) % capacity]
    }

    /** Set the element at [index]. */
    operator fun set(index: Int, value: Boolean) {
        if (index !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        buffer[(head + index) % capacity] = value
    }

    /** Simple iterator that walks from the front to the back of the deque. */
    class Iterator(private val deque: BooleanDeque) : kotlin.collections.Iterator<Boolean> {
        private var index: Int = 0

        override fun hasNext(): Boolean = index < deque.usedSize

        override fun next(): Boolean {
            if (!hasNext()) throw NoSuchElementException()
            return deque[index++]
        }
    }

    /**
     * Return an iterator from front to back.
     */
    operator fun iterator(): Iterator = Iterator(this)

    /**
     * Number of elements in this deque.
     */
    val size: Int
        get() = usedSize

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BooleanDeque) return false
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

    /**
     * Compatibility alias for [size].
     */
    val length: Int
        get() = usedSize

    /** Serializer: store it using the primitive array serializer to avoid boxing every element. */
    class TypeSerializer : KSerializer<BooleanDeque> {
        private val arraySerializer = BooleanArraySerializer()
        override val descriptor: SerialDescriptor = arraySerializer.descriptor

        override fun serialize(encoder: Encoder, value: BooleanDeque) {
            encoder.encodeSerializableValue(arraySerializer, value.toBooleanArray())
        }

        override fun deserialize(decoder: Decoder): BooleanDeque {
            val array = decoder.decodeSerializableValue(arraySerializer)
            return BooleanDeque(array)
        }
    }

    /**
     * Factories for primitive deques.
     */
    companion object {
        /**
         * Return an empty deque.
         */
        fun empty(): BooleanDeque = BooleanDeque(0)

        /**
         * Return a deque containing one [value].
         */
        fun of(value: Boolean): BooleanDeque = BooleanDeque(booleanArrayOf(value))

        /**
         * Return a deque containing [values].
         */
        fun of(vararg values: Boolean): BooleanDeque = BooleanDeque(values)
    }
}

/**
 * Copy this primitive array into a new [BooleanDeque].
 */
fun BooleanArray.toDeque(): BooleanDeque = BooleanDeque(this)
