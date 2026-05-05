// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import kotlin.jvm.JvmField
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Mutable contiguous buffer for primitive doubles.
 *
 * The buffer owns its internal array. Constructors copy caller-provided arrays, and mutating
 * operations update the receiver in place.
 *
 * Complexity notes:
 * - [size], [isEmpty], [isNotEmpty], [first], [last], [firstOrNull], [lastOrNull], [get], and [set]
 *   are O(1)
 * - appends are amortized O(1)
 * - indexed insertions, removals, copies, sorting, and searches are O(n)
 *
 * Exception contracts:
 * - [first], [last], [removeFirst], and [removeLast] throw [NoSuchElementException] on an empty
 *   buffer
 * - indexed access and update operations throw [IndexOutOfBoundsException] for invalid indices
 *
 * Negative indexing is supported for element access and updates where documented. Methods named
 * [binarySearch] require sorted contents; they do not sort implicitly.
 */
@Serializable(with = DoubleBuffer.TypeSerializer::class)
class DoubleBuffer(@JvmField internal var capacity: Int = 16) {
    // /////////////////////////////////////////////////////////////////////////
    // Constructors & Core Fields
    // /////////////////////////////////////////////////////////////////////////

    @JvmField internal var usedSize: Int = 0

    @JvmField internal var buffer = DoubleArray(capacity)

    constructor(values: DoubleArray) : this(values.size) {
        values.copyInto(buffer)
        usedSize = values.size
    }

    // /////////////////////////////////////////////////////////////////////////
    // Helper Functions for Python‐style indexing
    // /////////////////////////////////////////////////////////////////////////

    // For accessing elements (get, set, remove, swap, etc.)
    private fun normalizeAccessIndex(index: Int): Int {
        val idx = if (index < 0) usedSize + index else index
        if (idx !in 0 until usedSize) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $usedSize")
        }
        return idx
    }

    // For insertions: valid indices run from 0 to usedSize (inclusive)
    private fun normalizeInsertIndex(index: Int): Int {
        val idx = if (index < 0) usedSize + index else index
        if (idx !in 0..usedSize) {
            throw IndexOutOfBoundsException("Insert index $index out of bounds for size $usedSize")
        }
        return idx
    }

    // For slicing‐style operations (fill, copyRange, removeRange, extractSlice)
    // Mimics Python’s slice.indices(…) behavior (without a step)
    private fun normalizeRange(fromIndex: Int, toIndex: Int): Pair<Int, Int> {
        // Adjust for negative indices.
        val rawStart = if (fromIndex < 0) fromIndex + usedSize else fromIndex
        val rawEnd = if (toIndex < 0) toIndex + usedSize else toIndex

        // Clamp the indices to the valid range [0, usedSize].
        val start = rawStart.coerceIn(0, usedSize)
        val end = rawEnd.coerceIn(0, usedSize)

        // If start > end, return an empty range.
        return if (start > end) start to start else start to end
    }

    private fun requireElement(): Unit =
        if (usedSize == 0) {
            throw NoSuchElementException("Buffer is empty")
        } else {
            Unit
        }

    private fun requireSliceIndexRange(startIndex: Int, endIndex: Int, size: Int) {
        if (startIndex !in 0..size) {
            throw IndexOutOfBoundsException("Start index out of bounds: $startIndex")
        }
        if (endIndex !in startIndex..size) {
            throw IndexOutOfBoundsException("End index out of bounds: $endIndex")
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Comparable & Hashable
    // /////////////////////////////////////////////////////////////////////////

    private fun sameValue(left: Double, right: Double): Boolean = left.equals(right)

    private fun hashValue(value: Double): Int = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DoubleBuffer) return false
        if (usedSize != other.usedSize) return false
        for (i in 0 until usedSize) {
            if (!sameValue(buffer[i], other.buffer[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until usedSize) {
            result = result * 31 + hashValue(buffer[i])
        }
        return result
    }

    class TypeSerializer : KSerializer<DoubleBuffer> {
        private val arraySerializer = DoubleArraySerializer()
        override val descriptor: SerialDescriptor = arraySerializer.descriptor

        override fun serialize(encoder: Encoder, value: DoubleBuffer) {
            encoder.encodeSerializableValue(arraySerializer, value.toDoubleArray())
        }

        override fun deserialize(decoder: Decoder): DoubleBuffer =
            DoubleBuffer(decoder.decodeSerializableValue(arraySerializer))
    }

    // /////////////////////////////////////////////////////////////////////////
    // Showable T
    // /////////////////////////////////////////////////////////////////////////

    override fun toString(): String =
        "DoubleBuffer(${buffer.copyOfRange(0, usedSize).joinToString(", ")})"

    // /////////////////////////////////////////////////////////////////////////
    // Low-level Buffer Operations
    // /////////////////////////////////////////////////////////////////////////

    /** Ensure this buffer can store at least [requiredCapacity] elements without reallocating. */
    fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > this.capacity) {
            val newCapacity = maxOf(this.capacity * 3 / 2, requiredCapacity)
            val newBuffer = DoubleArray(newCapacity)
            buffer.copyInto(newBuffer, destinationOffset = 0, startIndex = 0, endIndex = usedSize)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    /** Shrink internal capacity to the current [size]. */
    fun trimToSize() {
        if (usedSize < capacity) {
            buffer = buffer.copyOf(usedSize)
            capacity = size
        }
    }

    /** Remove all elements while retaining current capacity. */
    fun clear() {
        usedSize = 0
    }

    // /////////////////////////////////////////////////////////////////////////
    // Finite C
    // /////////////////////////////////////////////////////////////////////////

    /** Number of elements currently stored. */
    val size: Int
        get() = usedSize

    /** Return whether this buffer contains no elements. */
    fun isEmpty(): Boolean = usedSize == 0

    /** Return whether this buffer contains at least one element. */
    fun isNotEmpty(): Boolean = usedSize != 0

    /** Return whether [value] is present. */
    fun contains(value: Double): Boolean = indexOf(value) != -1

    // /////////////////////////////////////////////////////////////////////////
    // Mutable C
    // /////////////////////////////////////////////////////////////////////////

    /** Replace every element with the result of [transform] and return this buffer. */
    fun mapInPlace(transform: (Double) -> Double): DoubleBuffer {
        for (i in 0 until size) {
            buffer[i] = transform(buffer[i])
        }
        return this
    }

    /** Remove elements rejected by [predicate] and return this buffer. */
    fun filterInPlace(predicate: (Double) -> Boolean): DoubleBuffer {
        var writeIndex = 0
        for (readIndex in 0 until size) {
            if (predicate(buffer[readIndex])) {
                buffer[writeIndex++] = buffer[readIndex]
            }
        }
        usedSize = writeIndex
        return this
    }

    // /////////////////////////////////////////////////////////////////////////
    // Mutable C + Eq T
    // /////////////////////////////////////////////////////////////////////////

    /** Remove elements accepted by [predicate] and return whether anything changed. */
    fun removeIf(predicate: (Double) -> Boolean): Boolean {
        var writeIndex = 0
        for (readIndex in 0 until usedSize) {
            if (!predicate(buffer[readIndex])) {
                buffer[writeIndex++] = buffer[readIndex]
            }
        }
        val hadChanges = writeIndex != usedSize
        usedSize = writeIndex
        return hadChanges
    }

    /** Remove every occurrence of [value] and return whether anything changed. */
    fun removeAll(value: Double): Boolean {
        var writeIndex = 0
        for (readIndex in 0 until usedSize) {
            if (!sameValue(buffer[readIndex], value)) {
                buffer[writeIndex++] = buffer[readIndex]
            }
        }
        val hadChanges = writeIndex != usedSize
        usedSize = writeIndex
        return hadChanges
    }

    // /////////////////////////////////////////////////////////////////////////
    // Iterable C
    // /////////////////////////////////////////////////////////////////////////

    /** Run [action] for each element in index order. */
    fun forEach(action: (Double) -> Unit) {
        for (i in 0 until size) action(buffer[i])
    }

    /** Split elements into buffers that match and do not match [predicate]. */
    fun partition(predicate: (Double) -> Boolean): Pair<DoubleBuffer, DoubleBuffer> {
        val matching = DoubleBuffer()
        val nonMatching = DoubleBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) {
                matching.add(buffer[i])
            } else {
                nonMatching.add(buffer[i])
            }
        }
        return matching to nonMatching
    }

    /**
     * Reduce this non-empty buffer from left to right.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun reduce(operation: (Double, Double) -> Double): Double {
        requireElement()
        var accumulator = buffer[0]
        for (i in 1 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    /** Return whether any element satisfies [predicate]. */
    fun any(predicate: (Double) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return true
        }
        return false
    }

    /** Return whether all elements satisfy [predicate]. */
    fun all(predicate: (Double) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (!predicate(buffer[i])) return false
        }
        return true
    }

    /** Return whether no elements satisfy [predicate]. */
    fun none(predicate: (Double) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return false
        }
        return true
    }

    /** Count elements that satisfy [predicate]. */
    fun count(predicate: (Double) -> Boolean): Int {
        var count = 0
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) count++
        }
        return count
    }

    /** Fold elements from left to right starting with [initial]. */
    fun <State> fold(initial: State, operation: (State, Double) -> State): State =
        foldLeft(initial, operation)

    /** Materialize this buffer as a mutable list. */
    fun toMutableList(): MutableList<Double> {
        val result = mutableListOf<Double>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    /** Materialize this buffer as a read-only list. */
    fun toList(): List<Double> = toMutableList()

    /** Materialize distinct elements as a mutable set. */
    fun toMutableSet(): MutableSet<Double> {
        val result = mutableSetOf<Double>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    /** Copy the active contents into a primitive array. */
    fun toDoubleArray(): DoubleArray = buffer.copyOfRange(0, size)

    /** Return a mutable copy of this buffer. */
    fun toDoubleBuffer(): DoubleBuffer {
        val copy = DoubleBuffer(usedSize)
        buffer.copyInto(copy.buffer, 0, 0, usedSize)
        copy.usedSize = usedSize
        return copy
    }

    /** Return a mutable copy of this buffer. */
    fun copy(): DoubleBuffer = toDoubleBuffer()

    /** Iterator over buffer contents in index order. */
    class Iterator(private val buf: DoubleBuffer) : kotlin.collections.Iterator<Double> {
        private var index = 0

        override fun hasNext(): Boolean = index < buf.usedSize

        override fun next(): Double {
            if (!hasNext()) throw NoSuchElementException()
            return buf.buffer[index++]
        }
    }

    /** Return an iterator over buffer contents in index order. */
    operator fun iterator(): Iterator = Iterator(this)

    // /////////////////////////////////////////////////////////////////////////
    // Mutable C + Iterable C
    // /////////////////////////////////////////////////////////////////////////

    /** Mutable iterator that can remove the last returned element. */
    class MutableIterator(private val buf: DoubleBuffer) :
        kotlin.collections.MutableIterator<Double> {
        private var index = 0
        private var lastReturned = -1

        override fun hasNext(): Boolean = index < buf.usedSize

        override fun next(): Double {
            if (index >= buf.usedSize) throw NoSuchElementException()
            lastReturned = index
            return buf.buffer[index++]
        }

        override fun remove() {
            if (lastReturned == -1) throw IllegalStateException()
            buf.removeAt(lastReturned)
            index = lastReturned
            lastReturned = -1
        }
    }

    /** Return a mutable iterator over this buffer. */
    fun mutableIterator(): MutableIterator = MutableIterator(this)

    // /////////////////////////////////////////////////////////////////////////
    // Iterable C + Eq T
    // /////////////////////////////////////////////////////////////////////////

    /** Return the first element accepted by [predicate], or null. */
    fun find(predicate: (Double) -> Boolean): Double? {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    /** Return a new buffer containing the first occurrence of each distinct element. */
    fun distinct(): DoubleBuffer {
        val seen = mutableSetOf<Double>()
        val result = DoubleBuffer()
        for (i in 0 until usedSize) {
            if (seen.add(buffer[i])) {
                result.add(buffer[i])
            }
        }
        return result
    }

    // /////////////////////////////////////////////////////////////////////////
    // Indexable C + Comparable T
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Searches for [value] using binary search.
     *
     * Requires the current contents to already be sorted in ascending order according to compareTo.
     * If the buffer is not sorted, the result is undefined.
     */
    fun binarySearch(value: Double): Int =
        binarySearchIndex(0, size) { buffer[it].compareTo(value) }

    /** Return a sorted copy of this buffer. */
    fun sorted(): DoubleBuffer {
        val copy = this.copy()
        copy.sort()
        return copy
    }

    // /////////////////////////////////////////////////////////////////////////
    // Indexable C
    // /////////////////////////////////////////////////////////////////////////

    /** Return the element at [index]. Negative indices address from the end. */
    operator fun get(index: Int): Double = buffer[normalizeAccessIndex(index)]

    /** Return the element at [index], or null when out of bounds. */
    fun getOrNull(index: Int): Double? {
        // Handle negative indices without throwing.
        val idx = if (index < 0) usedSize + index else index
        return if (idx in 0 until usedSize) buffer[idx] else null
    }

    /** Return the element at [index], or [defaultValue] when out of bounds. */
    inline fun getOrElse(index: Int, defaultValue: () -> Double): Double {
        val value = getOrNull(index)
        return value ?: defaultValue()
    }

    /**
     * Return the first element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun first(): Double =
        if (usedSize > 0) buffer[0] else throw NoSuchElementException("Buffer is empty")

    /** Return the first element, or null when empty. */
    fun firstOrNull(): Double? = if (usedSize > 0) buffer[0] else null

    /**
     * Return the last element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun last(): Double =
        if (usedSize > 0) buffer[usedSize - 1] else throw NoSuchElementException("Buffer is empty")

    /** Return the last element, or null when empty. */
    fun lastOrNull(): Double? = if (usedSize > 0) buffer[usedSize - 1] else null

    /** Return the first element accepted by [predicate], or null. */
    fun findFirst(predicate: (Double) -> Boolean): Double? = find(predicate)

    /** Return the last element accepted by [predicate], or null. */
    fun findLast(predicate: (Double) -> Boolean): Double? {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    /** Return the first index of [value], or `-1` when absent. */
    fun indexOf(value: Double): Int {
        for (i in 0 until size) {
            if (sameValue(buffer[i], value)) return i
        }
        return -1
    }

    /** Return the last index of [value], or `-1` when absent. */
    fun indexOfLast(value: Double): Int {
        for (i in size - 1 downTo 0) {
            if (sameValue(buffer[i], value)) return i
        }
        return -1
    }

    /** Return the first index accepted by [predicate], or `-1`. */
    fun indexWhere(predicate: (Double) -> Boolean): Int {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    /** Return the first index accepted by [predicate], or `-1`. */
    fun indexOfFirst(predicate: (Double) -> Boolean): Int = indexWhere(predicate)

    fun indexOfLast(predicate: (Double) -> Boolean): Int {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    /** Return all indices accepted by [predicate]. */
    fun indicesWhere(predicate: (Double) -> Boolean): IntBuffer {
        val indices = IntBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) indices.add(i)
        }
        return indices
    }

    /** Run [action] with each index and value. */
    fun forEachIndexed(action: (index: Int, value: Double) -> Unit) {
        for (i in 0 until size) action(i, buffer[i])
    }

    /** Fold elements from left to right starting with [initial]. */
    fun <State> foldLeft(initial: State, operation: (State, Double) -> State): State {
        var accumulator = initial
        for (i in 0 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    /** Fold elements from right to left starting with [initial]. */
    fun <State> foldRight(initial: State, operation: (Double, State) -> State): State {
        var accumulator = initial
        for (i in usedSize - 1 downTo 0) {
            accumulator = operation(buffer[i], accumulator)
        }
        return accumulator
    }

    // /////////////////////////////////////////////////////////////////////////
    // Indexable C + Mutable C
    // /////////////////////////////////////////////////////////////////////////

    /** Replace the element at [index]. Negative indices address from the end. */
    operator fun set(index: Int, value: Double) {
        buffer[normalizeAccessIndex(index)] = value
    }

    /** Fill the normalized range `[fromIndex, toIndex)` with [value]. */
    fun fill(value: Double, fromIndex: Int = 0, toIndex: Int = usedSize) {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    /** Fill [range] with [value] after normalizing slice bounds. */
    operator fun set(range: IntRange, value: Double) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    /** Replace [range] with [values]. The normalized range length must match [values]. */
    operator fun set(range: IntRange, values: DoubleArray) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        val rangeSize = end - start
        require(rangeSize == values.size) {
            "Array size ${values.size} must be equal to range size $rangeSize"
        }
        values.copyInto(buffer, start, 0, rangeSize)
    }

    /** Replace a range starting at [fromIndex] with values from [values]. */
    fun setRange(
        fromIndex: Int,
        values: DoubleArray,
        startIndex: Int = 0,
        endIndex: Int = values.size,
    ) {
        val idx = if (fromIndex < 0) usedSize + fromIndex else fromIndex
        if (idx < 0 || idx > usedSize) {
            throw IndexOutOfBoundsException("Index out of bounds: $fromIndex")
        }
        requireSliceIndexRange(startIndex, endIndex, values.size)
        val rangeSize = endIndex - startIndex
        if (idx + rangeSize > usedSize) {
            throw IndexOutOfBoundsException(
                "Range out of bounds: $fromIndex + $rangeSize > $usedSize"
            )
        }
        values.copyInto(buffer, idx, startIndex, endIndex)
    }

    /** Reverse this buffer in place. */
    fun reverse() {
        var left = 0
        var right = size - 1
        while (left < right) {
            val temp = buffer[left]
            buffer[left] = buffer[right]
            buffer[right] = temp
            left++
            right--
        }
    }

    /** Shuffle this buffer in place using [random]. */
    fun shuffle(random: kotlin.random.Random) {
        for (i in size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = buffer[i]
            buffer[i] = buffer[j]
            buffer[j] = temp
        }
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(index: Int, value: Double) {
        val idx = normalizeInsertIndex(index)
        ensureCapacity(usedSize + 1)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + 1, idx, usedSize)
        }
        buffer[idx] = value
        usedSize++
    }

    /** Append [value] to the end of this buffer. */
    fun add(value: Double) {
        ensureCapacity(usedSize + 1)
        buffer[usedSize++] = value
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(
        index: Int,
        values: DoubleArray,
        startIndex: Int = 0,
        endIndex: Int = values.size,
    ) {
        val idx = normalizeInsertIndex(index)
        requireSliceIndexRange(startIndex, endIndex, values.size)
        val addSize = endIndex - startIndex
        ensureCapacity(usedSize + addSize)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + addSize, idx, usedSize)
        }
        values.copyInto(buffer, idx, startIndex, endIndex)
        usedSize += addSize
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(
        index: Int,
        values: List<Double>,
        startIndex: Int = 0,
        endIndex: Int = values.size,
    ) {
        var idx = normalizeInsertIndex(index)
        requireSliceIndexRange(startIndex, endIndex, values.size)
        val addSize = endIndex - startIndex
        ensureCapacity(usedSize + addSize)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + addSize, idx, usedSize)
        }
        for (i in startIndex until endIndex) {
            buffer[idx++] = values[i]
        }
        usedSize += addSize
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(index: Int, values: Collection<Double>) {
        var idx = normalizeInsertIndex(index)
        val addSize = values.size
        ensureCapacity(usedSize + addSize)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + addSize, idx, usedSize)
        }
        for (value in values) {
            buffer[idx++] = value
        }
        usedSize += addSize
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(index: Int, value: DoubleBuffer) {
        insertAt(index, value.buffer, 0, value.usedSize)
    }

    /** Insert value(s) at [index], shifting existing elements to the right. */
    fun insertAt(index: Int, values: DoubleDeque) {
        // Assuming DoubleDeque has a toDoubleArray() method.
        insertAt(index, values.toDoubleArray())
    }

    /** Remove and return the element at [index]. Negative indices address from the end. */
    fun removeAt(index: Int): Double {
        val idx = normalizeAccessIndex(index)
        val value = buffer[idx]
        if (idx < usedSize - 1) {
            buffer.copyInto(buffer, idx, idx + 1, usedSize)
        }
        usedSize--
        return value
    }

    /**
     * Remove and return the first element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun removeFirst(): Double {
        requireElement()
        return removeAt(0)
    }

    /**
     * Remove and return the last element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun removeLast(): Double {
        requireElement()
        return removeAt(usedSize - 1)
    }

    /** Bidirectional iterator over buffer contents. */
    class ListIterator(private val buf: DoubleBuffer) : kotlin.collections.ListIterator<Double> {
        private var index = 0

        override fun hasNext(): Boolean = index < buf.usedSize

        override fun hasPrevious(): Boolean = index > 0

        override fun next(): Double {
            if (!hasNext()) throw NoSuchElementException()
            return buf.buffer[index++]
        }

        override fun nextIndex(): Int = index

        override fun previous(): Double {
            if (!hasPrevious()) throw NoSuchElementException()
            return buf.buffer[--index]
        }

        override fun previousIndex(): Int = index - 1
    }

    /** Return a bidirectional iterator over this buffer. */
    fun listIterator(): ListIterator = ListIterator(this)

    // /////////////////////////////////////////////////////////////////////////
    // Indexable C + Mutable C + Comparable T
    // /////////////////////////////////////////////////////////////////////////

    /** Sort this buffer in ascending natural order. */
    fun sort() {
        buffer.sort(0, size)
    }

    /** Sort this buffer in descending natural order. */
    fun sortDescending() {
        buffer.sortDescending(0, size)
    }

    /** Return a descending-sorted copy of this buffer. */
    fun sortedDescending(): DoubleBuffer {
        val copy = this.copy()
        copy.sort()
        copy.reverse()
        return copy
    }

    /** Swap elements at [index1] and [index2]. Negative indices address from the end. */
    fun swap(index1: Int, index2: Int) {
        val idx1 = normalizeAccessIndex(index1)
        val idx2 = normalizeAccessIndex(index2)
        val temp = buffer[idx1]
        buffer[idx1] = buffer[idx2]
        buffer[idx2] = temp
    }

    /** Remove the normalized range `[from, to)` and return it as an array. */
    fun extractSliceAsArray(from: Int, to: Int): DoubleArray {
        val (start, end) = normalizeRange(from, to)
        val result = buffer.copyOfRange(start, end)
        if (end < usedSize) {
            buffer.copyInto(buffer, start, end, usedSize)
        }
        usedSize -= (end - start)
        return result
    }

    /** Copy the normalized range `[fromIndex, toIndex)` into a new buffer. */
    fun copyRangeAsBuffer(fromIndex: Int, toIndex: Int): DoubleBuffer {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        val newBuffer = DoubleBuffer(end - start)
        buffer.copyInto(newBuffer.buffer, 0, start, end)
        newBuffer.usedSize = end - start
        return newBuffer
    }

    /** Remove the normalized range `[fromIndex, toIndex)` from this buffer. */
    fun removeRange(fromIndex: Int, toIndex: Int) {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        val rangeSize = end - start
        if (rangeSize <= 0) return
        if (end < usedSize) {
            buffer.copyInto(buffer, start, end, usedSize)
        }
        usedSize -= rangeSize
    }

    // /////////////////////////////////////////////////////////////////////////
    // Iterable C + Comparable T
    // /////////////////////////////////////////////////////////////////////////

    /** Return the minimum element, or null when empty. */
    fun minOrNull(): Double? {
        if (usedSize == 0) return null
        var minValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] < minValue) minValue = buffer[i]
        }
        return minValue
    }

    /**
     * Return the minimum element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun min(): Double = minOrNull() ?: throw NoSuchElementException("Buffer is empty")

    /** Return the maximum element, or null when empty. */
    fun maxOrNull(): Double? {
        if (usedSize == 0) return null
        var maxValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] > maxValue) maxValue = buffer[i]
        }
        return maxValue
    }

    /**
     * Return the maximum element.
     *
     * @throws NoSuchElementException when this buffer is empty.
     */
    fun max(): Double = maxOrNull() ?: throw NoSuchElementException("Buffer is empty")

    // /////////////////////////////////////////////////////////////////////////
    // Iterable C + Numeric T
    // /////////////////////////////////////////////////////////////////////////

    /** Return the sum of all elements. */
    fun sum(): Double {
        var s = 0.0
        for (i in 0 until usedSize) s += buffer[i]
        return s.toDouble()
    }

    // /////////////////////////////////////////////////////////////////////////
    // Companion Object
    // /////////////////////////////////////////////////////////////////////////

    /** Factories for primitive buffers. */
    companion object {
        /** Return an empty buffer. */
        fun empty(): DoubleBuffer = DoubleBuffer()

        /** Return an empty buffer with [capacity]. */
        fun withCapacity(capacity: Int): DoubleBuffer = DoubleBuffer(capacity)

        /** Build a buffer of [size] values by calling [init] for each index. */
        fun generate(size: Int, init: (Int) -> Double): DoubleBuffer {
            val buffer = DoubleBuffer(size)
            for (i in 0 until size) {
                buffer.add(init(i))
            }
            return buffer
        }

        /** Concatenate [buffers] into a new buffer. */
        fun concat(vararg buffers: DoubleBuffer): DoubleBuffer {
            // Pre-calculate the total number of elements
            val totalSize = buffers.sumOf { it.size }
            // Allocate the new buffer with the exact required capacity
            val result = DoubleBuffer(totalSize)
            var currentPos = 0
            // Copy each buffer's valid elements in one go
            for (buf in buffers) {
                buf.buffer.copyInto(
                    result.buffer,
                    destinationOffset = currentPos,
                    startIndex = 0,
                    endIndex = buf.size,
                )
                currentPos += buf.size
            }
            // Set the usedSize directly
            result.usedSize = totalSize
            return result
        }

        /** Copy [values] into a new buffer. */
        fun from(values: Collection<Double>): DoubleBuffer {
            val buffer = DoubleBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }

        /** Copy [values] into a new buffer. */
        fun from(values: DoubleArray): DoubleBuffer = DoubleBuffer(values)

        /** Copy [values] into a new buffer. */
        fun from(values: DoubleBuffer): DoubleBuffer = values.copy()

        /** Copy [values] into a new buffer. */
        fun from(values: DoubleDeque): DoubleBuffer = DoubleBuffer(values.toDoubleArray())

        /** Build a buffer containing [values]. */
        fun of(vararg values: Double): DoubleBuffer {
            val buffer = DoubleBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }
    }
}

/** Build a [DoubleBuffer] containing [values]. */
fun doubleBufferOf(vararg values: Double): DoubleBuffer = DoubleBuffer.of(*values)
