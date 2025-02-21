package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = LongBuffer.TypeSerializer::class)
class LongBuffer(@JvmField internal var capacity: Int = 16) {

    ///////////////////////////////////////////////////////////////////////////
    // Constructors & Core Fields
    ///////////////////////////////////////////////////////////////////////////

    @JvmField internal var usedSize: Int = 0
    @JvmField internal var buffer = LongArray(capacity)

    constructor(values: LongArray) : this(values.size) {
        values.copyInto(buffer)
        usedSize = values.size
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper Functions for Python‐style indexing
    ///////////////////////////////////////////////////////////////////////////

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

    ///////////////////////////////////////////////////////////////////////////
    // Comparable & Hashable
    ///////////////////////////////////////////////////////////////////////////

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LongBuffer) return false
        if (usedSize != other.usedSize) return false
        for (i in 0 until usedSize) {
            if (buffer[i] != other.buffer[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until usedSize) {
            result = result * 31 + buffer[i].hashCode()
        }
        return result
    }

    class TypeSerializer : KSerializer<LongBuffer> {
        private val listSerializer = ListSerializer(Long.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: LongBuffer) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): LongBuffer {
            return LongBuffer(decoder.decodeSerializableValue(listSerializer).toLongArray())
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Showable T
    ///////////////////////////////////////////////////////////////////////////

    override fun toString(): String {
        return "LongBuffer(${buffer.copyOfRange(0, usedSize).joinToString(", ")})"
    }

    ///////////////////////////////////////////////////////////////////////////
    // Low-level Buffer Operations
    ///////////////////////////////////////////////////////////////////////////

    fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > this.capacity) {
            val newCapacity = maxOf(this.capacity * 3 / 2, requiredCapacity)
            val newBuffer = LongArray(newCapacity)
            buffer.copyInto(newBuffer, destinationOffset = 0, startIndex = 0, endIndex = usedSize)
            this.buffer = newBuffer
            this.capacity = newCapacity
        }
    }

    fun trimToSize() {
        if (usedSize < capacity) {
            buffer = buffer.copyOf(usedSize)
            capacity = size
        }
    }

    fun clear() {
        usedSize = 0
    }

    ///////////////////////////////////////////////////////////////////////////
    // Finite C
    ///////////////////////////////////////////////////////////////////////////

    val size: Int
        get() = usedSize

    fun isEmpty(): Boolean = usedSize == 0

    fun isNotEmpty(): Boolean = usedSize != 0

    fun contains(value: Long): Boolean = indexOf(value) != -1

    ///////////////////////////////////////////////////////////////////////////
    // Mutable C
    ///////////////////////////////////////////////////////////////////////////

    fun mapInPlace(transform: (Long) -> Long): LongBuffer {
        for (i in 0 until size) {
            buffer[i] = transform(buffer[i])
        }
        return this
    }

    fun filterInPlace(predicate: (Long) -> Boolean): LongBuffer {
        var writeIndex = 0
        for (readIndex in 0 until size) {
            if (predicate(buffer[readIndex])) {
                buffer[writeIndex++] = buffer[readIndex]
            }
        }
        usedSize = writeIndex
        return this
    }

    ///////////////////////////////////////////////////////////////////////////
    // Mutable C + Eq T
    ///////////////////////////////////////////////////////////////////////////

    fun removeIf(predicate: (Long) -> Boolean): Boolean {
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

    fun removeAll(value: Long): Boolean {
        var writeIndex = 0
        for (readIndex in 0 until usedSize) {
            if (buffer[readIndex] != value) {
                buffer[writeIndex++] = buffer[readIndex]
            }
        }
        val hadChanges = writeIndex != usedSize
        usedSize = writeIndex
        return hadChanges
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iterable C
    ///////////////////////////////////////////////////////////////////////////

    fun forEach(action: (Long) -> Unit) {
        for (i in 0 until size) action(buffer[i])
    }

    fun partition(predicate: (Long) -> Boolean): Pair<LongBuffer, LongBuffer> {
        val matching = LongBuffer()
        val nonMatching = LongBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) matching.add(buffer[i])
            else nonMatching.add(buffer[i])
        }
        return matching to nonMatching
    }

    fun reduce(operation: (Long, Long) -> Long): Long {
        require(usedSize > 0) { "Empty buffer cannot be reduced." }
        var accumulator = buffer[0]
        for (i in 1 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    fun any(predicate: (Long) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return true
        }
        return false
    }

    fun all(predicate: (Long) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (!predicate(buffer[i])) return false
        }
        return true
    }

    fun none(predicate: (Long) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return false
        }
        return true
    }

    fun count(predicate: (Long) -> Boolean): Int {
        var count = 0
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) count++
        }
        return count
    }

    fun <State> fold(initial: State, operation: (State, Long) -> State): State =
        foldLeft(initial, operation)

    fun toMutableList(): MutableList<Long> {
        val result = mutableListOf<Long>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    fun toList(): List<Long> = toMutableList()

    fun toMutableSet(): MutableSet<Long> {
        val result = mutableSetOf<Long>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    fun toLongArray(): LongArray {
        return buffer.copyOfRange(0, size)
    }

    fun toLongBuffer(): LongBuffer {
        val copy = LongBuffer(usedSize)
        buffer.copyInto(copy.buffer, 0, 0, usedSize)
        copy.usedSize = usedSize
        return copy
    }

    fun copy(): LongBuffer = toLongBuffer()

    class Iterator(private val buf: LongBuffer) : kotlin.collections.Iterator<Long> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.usedSize
        override fun next(): Long = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    ///////////////////////////////////////////////////////////////////////////
    // Mutable C + Iterable C
    ///////////////////////////////////////////////////////////////////////////

    class MutableIterator(private val buf: LongBuffer) : kotlin.collections.MutableIterator<Long> {
        private var index = 0
        private var lastReturned = -1

        override fun hasNext(): Boolean = index < buf.usedSize

        override fun next(): Long {
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

    fun mutableIterator(): MutableIterator = MutableIterator(this)

    ///////////////////////////////////////////////////////////////////////////
    // Iterable C + Eq T
    ///////////////////////////////////////////////////////////////////////////

    fun find(predicate: (Long) -> Boolean): Long? {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    fun distinct(): LongBuffer {
        val seen = mutableSetOf<Long>()
        val result = LongBuffer()
        for (i in 0 until usedSize) {
            if (seen.add(buffer[i])) {
                result.add(buffer[i])
            }
        }
        return result
    }

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C + Comparable T
    ///////////////////////////////////////////////////////////////////////////

    fun binarySearch(value: Long): Int {
        return buffer.binarySearch(value, 0, size)
    }

    fun sorted(): LongBuffer {
        val copy = this.copy()
        copy.sort()
        return copy
    }

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C
    ///////////////////////////////////////////////////////////////////////////

    operator fun get(index: Int): Long {
        return buffer[normalizeAccessIndex(index)]
    }

    fun getOrNull(index: Int): Long? {
        // Handle negative indices without throwing.
        val idx = if (index < 0) usedSize + index else index
        return if (idx in 0 until usedSize) buffer[idx] else null
    }

    inline fun getOrElse(index: Int, defaultValue: () -> Long): Long {
        val value = getOrNull(index)
        return value ?: defaultValue()
    }

    fun first(): Long =
        if (usedSize > 0) buffer[0] else throw NoSuchElementException("Buffer is empty")

    fun firstOrNull(): Long? = if (usedSize > 0) buffer[0] else null

    fun last(): Long =
        if (usedSize > 0) buffer[usedSize - 1] else throw NoSuchElementException("Buffer is empty")

    fun lastOrNull(): Long? = if (usedSize > 0) buffer[usedSize - 1] else null

    fun findFirst(predicate: (Long) -> Boolean): Long? =
        find(predicate)

    fun findLast(predicate: (Long) -> Boolean): Long? {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    fun indexOf(value: Long): Int {
        for (i in 0 until size) {
            if (buffer[i] == value) return i
        }
        return -1
    }

    fun indexOfLast(value: Long): Int {
        for (i in size - 1 downTo 0) {
            if (buffer[i] == value) return i
        }
        return -1
    }

    fun indexWhere(predicate: (Long) -> Boolean): Int {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    fun indexOfFirst(predicate: (Long) -> Boolean): Int = indexWhere(predicate)

    fun indexOfLast(predicate: (Long) -> Boolean): Int {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    fun indicesWhere(predicate: (Long) -> Boolean): IntBuffer {
        val indices = IntBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) indices.add(i)
        }
        return indices
    }

    fun forEachIndexed(action: (index: Int, value: Long) -> Unit) {
        for (i in 0 until size) action(i, buffer[i])
    }

    fun <State> foldLeft(initial: State, operation: (State, Long) -> State): State {
        var accumulator = initial
        for (i in 0 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    fun <State> foldRight(initial: State, operation: (Long, State) -> State): State {
        var accumulator = initial
        for (i in usedSize - 1 downTo 0) {
            accumulator = operation(buffer[i], accumulator)
        }
        return accumulator
    }

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C + Mutable C
    ///////////////////////////////////////////////////////////////////////////

    operator fun set(index: Int, value: Long) {
        buffer[normalizeAccessIndex(index)] = value
    }

    fun fill(value: Long, fromIndex: Int = 0, toIndex: Int = usedSize) {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    operator fun set(range: IntRange, value: Long) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    operator fun set(range: IntRange, values: LongArray) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        val rangeSize = end - start
        require(rangeSize == values.size) { "Array size ${values.size} must be equal to range size $rangeSize" }
        values.copyInto(buffer, start, 0, rangeSize)
    }

    fun setRange(fromIndex: Int, values: LongArray, startIndex: Int = 0, endIndex: Int = values.size) {
        val idx = if (fromIndex < 0) usedSize + fromIndex else fromIndex
        if (idx < 0 || idx > usedSize)
            throw IndexOutOfBoundsException("Index out of bounds: $fromIndex")
        val rangeSize = endIndex - startIndex
        if (idx + rangeSize > usedSize)
            throw IndexOutOfBoundsException("Range out of bounds: $fromIndex + $rangeSize > $usedSize")
        values.copyInto(buffer, idx, startIndex, endIndex)
    }

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

    fun shuffle(random: kotlin.random.Random) {
        for (i in size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = buffer[i]
            buffer[i] = buffer[j]
            buffer[j] = temp
        }
    }

    fun insertAt(index: Int, value: Long) {
        val idx = normalizeInsertIndex(index)
        ensureCapacity(usedSize + 1)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + 1, idx, usedSize)
        }
        buffer[idx] = value
        usedSize++
    }

    fun add(value: Long) {
        ensureCapacity(usedSize + 1)
        buffer[usedSize++] = value
    }

    fun insertAt(index: Int, values: LongArray, startIndex: Int = 0, endIndex: Int = values.size) {
        val idx = normalizeInsertIndex(index)
        require(startIndex in 0..values.size) { "Start index out of bounds: $startIndex" }
        require(endIndex in startIndex..values.size) { "End index out of bounds: $endIndex" }
        val addSize = endIndex - startIndex
        ensureCapacity(usedSize + addSize)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + addSize, idx, usedSize)
        }
        values.copyInto(buffer, idx, startIndex, endIndex)
        usedSize += addSize
    }

    fun insertAt(index: Int, values: List<Long>, startIndex: Int = 0, endIndex: Int = values.size) {
        var idx = normalizeInsertIndex(index)
        require(startIndex in 0..values.size) { "Start index out of bounds: $startIndex" }
        require(endIndex in startIndex..values.size) { "End index out of bounds: $endIndex" }
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

    fun insertAt(index: Int, values: Collection<Long>) {
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

    fun insertAt(index: Int, value: LongBuffer) {
        insertAt(index, value.buffer, 0, value.usedSize)
    }

    fun insertAt(index: Int, values: LongDeque) {
        // Assuming LongDeque has a toLongArray() method.
        insertAt(index, values.toLongArray())
    }

    fun removeAt(index: Int): Long {
        val idx = normalizeAccessIndex(index)
        val value = buffer[idx]
        if (idx < usedSize - 1) {
            buffer.copyInto(buffer, idx, idx + 1, usedSize)
        }
        usedSize--
        return value
    }

    fun removeFirst(): Long {
        require(usedSize > 0) { "Buffer is empty." }
        return removeAt(0)
    }

    fun removeLast(): Long {
        require(usedSize > 0) { "Buffer is empty." }
        return removeAt(usedSize - 1)
    }

    class ListIterator(private val buf: LongBuffer) : kotlin.collections.ListIterator<Long> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.usedSize
        override fun hasPrevious(): Boolean = index > 0
        override fun next(): Long = buf.buffer[index++]
        override fun nextIndex(): Int = index
        override fun previous(): Long = buf.buffer[--index]
        override fun previousIndex(): Int = index - 1
    }

    fun listIterator(): ListIterator = ListIterator(this)

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C + Mutable C + Comparable T
    ///////////////////////////////////////////////////////////////////////////

    fun sort() {
        buffer.sort(0, size)
    }

    fun sortDescending() {
        buffer.sortDescending(0, size)
    }

    fun sortedDescending(): LongBuffer {
        val copy = this.copy()
        copy.sort()
        copy.reverse()
        return copy
    }

    fun swap(index1: Int, index2: Int) {
        val idx1 = normalizeAccessIndex(index1)
        val idx2 = normalizeAccessIndex(index2)
        val temp = buffer[idx1]
        buffer[idx1] = buffer[idx2]
        buffer[idx2] = temp
    }

    fun extractSliceAsArray(from: Int, to: Int): LongArray {
        val (start, end) = normalizeRange(from, to)
        val result = buffer.copyOfRange(start, end)
        if (end < usedSize) {
            buffer.copyInto(buffer, start, end, usedSize)
        }
        usedSize -= (end - start)
        return result
    }

    fun copyRangeAsBuffer(fromIndex: Int, toIndex: Int): LongBuffer {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        val newBuffer = LongBuffer(end - start)
        buffer.copyInto(newBuffer.buffer, 0, start, end)
        newBuffer.usedSize = end - start
        return newBuffer
    }

    fun removeRange(fromIndex: Int, toIndex: Int) {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        val rangeSize = end - start
        if (rangeSize <= 0) return
        if (end < usedSize) {
            buffer.copyInto(buffer, start, end, usedSize)
        }
        usedSize -= rangeSize
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iterable C + Comparable T
    ///////////////////////////////////////////////////////////////////////////

    fun minOrNull(): Long? {
        if (usedSize == 0) return null
        var minValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] < minValue) minValue = buffer[i]
        }
        return minValue
    }

    fun min(): Long = minOrNull() ?: throw NoSuchElementException("Buffer is empty")

    fun maxOrNull(): Long? {
        if (usedSize == 0) return null
        var maxValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] > maxValue) maxValue = buffer[i]
        }
        return maxValue
    }

    fun max(): Long = maxOrNull() ?: throw NoSuchElementException("Buffer is empty")

    ///////////////////////////////////////////////////////////////////////////
    // Iterable C + Numeric T
    ///////////////////////////////////////////////////////////////////////////

    fun sum(): Long {
        var s = 0L
        for (i in 0 until usedSize) s += buffer[i]
        return s.toLong()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Companion Object
    ///////////////////////////////////////////////////////////////////////////

    companion object {
        fun empty(): LongBuffer = LongBuffer()

        fun withCapacity(capacity: Int): LongBuffer = LongBuffer(capacity)

        fun generate(size: Int, init: (Int) -> Long): LongBuffer {
            val buffer = LongBuffer(size)
            for (i in 0 until size) {
                buffer.add(init(i))
            }
            return buffer
        }

        fun concat(vararg buffers: LongBuffer): LongBuffer {
            // Pre-calculate the total number of elements
            val totalSize = buffers.sumOf { it.size }
            // Allocate the new buffer with the exact required capacity
            val result = LongBuffer(totalSize)
            var currentPos = 0
            // Copy each buffer's valid elements in one go
            for (buf in buffers) {
                buf.buffer.copyInto(result.buffer, destinationOffset = currentPos, startIndex = 0, endIndex = buf.size)
                currentPos += buf.size
            }
            // Set the usedSize directly
            result.usedSize = totalSize
            return result
        }

        fun from(values: Collection<Long>): LongBuffer {
            val buffer = LongBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }

        fun from(values: LongArray): LongBuffer {
            return LongBuffer(values)
        }

        fun from(values: LongBuffer): LongBuffer {
            return values.copy()
        }

        fun from(values: LongDeque): LongBuffer {
            return LongBuffer(values.toLongArray())
        }

        fun of(vararg values: Long): LongBuffer {
            val buffer = LongBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }
    }
}

fun longBufferOf(vararg values: Long): LongBuffer = LongBuffer.of(*values)