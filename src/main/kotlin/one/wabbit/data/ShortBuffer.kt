package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ShortBuffer.TypeSerializer::class)
class ShortBuffer(@JvmField internal var capacity: Int = 16) {

    ///////////////////////////////////////////////////////////////////////////
    // Constructors & Core Fields
    ///////////////////////////////////////////////////////////////////////////

    @JvmField internal var usedSize: Int = 0
    @JvmField internal var buffer = ShortArray(capacity)

    constructor(values: ShortArray) : this(values.size) {
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
        if (other !is ShortBuffer) return false
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

    class TypeSerializer : KSerializer<ShortBuffer> {
        private val listSerializer = ListSerializer(Short.serializer())
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: ShortBuffer) {
            encoder.encodeSerializableValue(listSerializer, value.toList())
        }

        override fun deserialize(decoder: Decoder): ShortBuffer {
            return ShortBuffer(decoder.decodeSerializableValue(listSerializer).toShortArray())
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Showable T
    ///////////////////////////////////////////////////////////////////////////

    override fun toString(): String {
        return "ShortBuffer(${buffer.copyOfRange(0, usedSize).joinToString(", ")})"
    }

    ///////////////////////////////////////////////////////////////////////////
    // Low-level Buffer Operations
    ///////////////////////////////////////////////////////////////////////////

    fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > this.capacity) {
            val newCapacity = maxOf(this.capacity * 3 / 2, requiredCapacity)
            val newBuffer = ShortArray(newCapacity)
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

    fun contains(value: Short): Boolean = indexOf(value) != -1

    ///////////////////////////////////////////////////////////////////////////
    // Mutable C
    ///////////////////////////////////////////////////////////////////////////

    fun mapInPlace(transform: (Short) -> Short): ShortBuffer {
        for (i in 0 until size) {
            buffer[i] = transform(buffer[i])
        }
        return this
    }

    fun filterInPlace(predicate: (Short) -> Boolean): ShortBuffer {
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

    fun removeIf(predicate: (Short) -> Boolean): Boolean {
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

    fun removeAll(value: Short): Boolean {
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

    fun forEach(action: (Short) -> Unit) {
        for (i in 0 until size) action(buffer[i])
    }

    fun partition(predicate: (Short) -> Boolean): Pair<ShortBuffer, ShortBuffer> {
        val matching = ShortBuffer()
        val nonMatching = ShortBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) matching.add(buffer[i])
            else nonMatching.add(buffer[i])
        }
        return matching to nonMatching
    }

    fun reduce(operation: (Short, Short) -> Short): Short {
        require(usedSize > 0) { "Empty buffer cannot be reduced." }
        var accumulator = buffer[0]
        for (i in 1 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    fun any(predicate: (Short) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return true
        }
        return false
    }

    fun all(predicate: (Short) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (!predicate(buffer[i])) return false
        }
        return true
    }

    fun none(predicate: (Short) -> Boolean): Boolean {
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) return false
        }
        return true
    }

    fun count(predicate: (Short) -> Boolean): Int {
        var count = 0
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) count++
        }
        return count
    }

    fun <State> fold(initial: State, operation: (State, Short) -> State): State =
        foldLeft(initial, operation)

    fun toMutableList(): MutableList<Short> {
        val result = mutableListOf<Short>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    fun toList(): List<Short> = toMutableList()

    fun toMutableSet(): MutableSet<Short> {
        val result = mutableSetOf<Short>()
        for (i in 0 until usedSize) {
            result.add(buffer[i])
        }
        return result
    }

    fun toShortArray(): ShortArray {
        return buffer.copyOfRange(0, size)
    }

    fun toShortBuffer(): ShortBuffer {
        val copy = ShortBuffer(usedSize)
        buffer.copyInto(copy.buffer, 0, 0, usedSize)
        copy.usedSize = usedSize
        return copy
    }

    fun copy(): ShortBuffer = toShortBuffer()

    class Iterator(private val buf: ShortBuffer) : kotlin.collections.Iterator<Short> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.usedSize
        override fun next(): Short = buf.buffer[index++]
    }

    operator fun iterator(): Iterator = Iterator(this)

    ///////////////////////////////////////////////////////////////////////////
    // Mutable C + Iterable C
    ///////////////////////////////////////////////////////////////////////////

    class MutableIterator(private val buf: ShortBuffer) : kotlin.collections.MutableIterator<Short> {
        private var index = 0
        private var lastReturned = -1

        override fun hasNext(): Boolean = index < buf.usedSize

        override fun next(): Short {
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

    fun find(predicate: (Short) -> Boolean): Short? {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    fun distinct(): ShortBuffer {
        val seen = mutableSetOf<Short>()
        val result = ShortBuffer()
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

    fun binarySearch(value: Short): Int {
        return buffer.binarySearch(value, 0, size)
    }

    fun sorted(): ShortBuffer {
        val copy = this.copy()
        copy.sort()
        return copy
    }

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C
    ///////////////////////////////////////////////////////////////////////////

    operator fun get(index: Int): Short {
        return buffer[normalizeAccessIndex(index)]
    }

    fun getOrNull(index: Int): Short? {
        // Handle negative indices without throwing.
        val idx = if (index < 0) usedSize + index else index
        return if (idx in 0 until usedSize) buffer[idx] else null
    }

    inline fun getOrElse(index: Int, defaultValue: () -> Short): Short {
        val value = getOrNull(index)
        return value ?: defaultValue()
    }

    fun first(): Short =
        if (usedSize > 0) buffer[0] else throw NoSuchElementException("Buffer is empty")

    fun firstOrNull(): Short? = if (usedSize > 0) buffer[0] else null

    fun last(): Short =
        if (usedSize > 0) buffer[usedSize - 1] else throw NoSuchElementException("Buffer is empty")

    fun lastOrNull(): Short? = if (usedSize > 0) buffer[usedSize - 1] else null

    fun findFirst(predicate: (Short) -> Boolean): Short? =
        find(predicate)

    fun findLast(predicate: (Short) -> Boolean): Short? {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return buffer[i]
        }
        return null
    }

    fun indexOf(value: Short): Int {
        for (i in 0 until size) {
            if (buffer[i] == value) return i
        }
        return -1
    }

    fun indexOfLast(value: Short): Int {
        for (i in size - 1 downTo 0) {
            if (buffer[i] == value) return i
        }
        return -1
    }

    fun indexWhere(predicate: (Short) -> Boolean): Int {
        for (i in 0 until size) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    fun indexOfFirst(predicate: (Short) -> Boolean): Int = indexWhere(predicate)

    fun indexOfLast(predicate: (Short) -> Boolean): Int {
        for (i in size - 1 downTo 0) {
            if (predicate(buffer[i])) return i
        }
        return -1
    }

    fun indicesWhere(predicate: (Short) -> Boolean): IntBuffer {
        val indices = IntBuffer()
        for (i in 0 until usedSize) {
            if (predicate(buffer[i])) indices.add(i)
        }
        return indices
    }

    fun forEachIndexed(action: (index: Int, value: Short) -> Unit) {
        for (i in 0 until size) action(i, buffer[i])
    }

    fun <State> foldLeft(initial: State, operation: (State, Short) -> State): State {
        var accumulator = initial
        for (i in 0 until usedSize) {
            accumulator = operation(accumulator, buffer[i])
        }
        return accumulator
    }

    fun <State> foldRight(initial: State, operation: (Short, State) -> State): State {
        var accumulator = initial
        for (i in usedSize - 1 downTo 0) {
            accumulator = operation(buffer[i], accumulator)
        }
        return accumulator
    }

    ///////////////////////////////////////////////////////////////////////////
    // Indexable C + Mutable C
    ///////////////////////////////////////////////////////////////////////////

    operator fun set(index: Int, value: Short) {
        buffer[normalizeAccessIndex(index)] = value
    }

    fun fill(value: Short, fromIndex: Int = 0, toIndex: Int = usedSize) {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    operator fun set(range: IntRange, value: Short) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        for (i in start until end) {
            buffer[i] = value
        }
    }

    operator fun set(range: IntRange, values: ShortArray) {
        val (start, end) = normalizeRange(range.first, range.last + 1)
        val rangeSize = end - start
        require(rangeSize == values.size) { "Array size ${values.size} must be equal to range size $rangeSize" }
        values.copyInto(buffer, start, 0, rangeSize)
    }

    fun setRange(fromIndex: Int, values: ShortArray, startIndex: Int = 0, endIndex: Int = values.size) {
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

    fun insertAt(index: Int, value: Short) {
        val idx = normalizeInsertIndex(index)
        ensureCapacity(usedSize + 1)
        if (idx < usedSize) {
            buffer.copyInto(buffer, idx + 1, idx, usedSize)
        }
        buffer[idx] = value
        usedSize++
    }

    fun add(value: Short) {
        ensureCapacity(usedSize + 1)
        buffer[usedSize++] = value
    }

    fun insertAt(index: Int, values: ShortArray, startIndex: Int = 0, endIndex: Int = values.size) {
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

    fun insertAt(index: Int, values: List<Short>, startIndex: Int = 0, endIndex: Int = values.size) {
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

    fun insertAt(index: Int, values: Collection<Short>) {
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

    fun insertAt(index: Int, value: ShortBuffer) {
        insertAt(index, value.buffer, 0, value.usedSize)
    }

    fun insertAt(index: Int, values: ShortDeque) {
        // Assuming ShortDeque has a toShortArray() method.
        insertAt(index, values.toShortArray())
    }

    fun removeAt(index: Int): Short {
        val idx = normalizeAccessIndex(index)
        val value = buffer[idx]
        if (idx < usedSize - 1) {
            buffer.copyInto(buffer, idx, idx + 1, usedSize)
        }
        usedSize--
        return value
    }

    fun removeFirst(): Short {
        require(usedSize > 0) { "Buffer is empty." }
        return removeAt(0)
    }

    fun removeLast(): Short {
        require(usedSize > 0) { "Buffer is empty." }
        return removeAt(usedSize - 1)
    }

    class ListIterator(private val buf: ShortBuffer) : kotlin.collections.ListIterator<Short> {
        private var index = 0
        override fun hasNext(): Boolean = index < buf.usedSize
        override fun hasPrevious(): Boolean = index > 0
        override fun next(): Short = buf.buffer[index++]
        override fun nextIndex(): Int = index
        override fun previous(): Short = buf.buffer[--index]
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

    fun sortedDescending(): ShortBuffer {
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

    fun extractSliceAsArray(from: Int, to: Int): ShortArray {
        val (start, end) = normalizeRange(from, to)
        val result = buffer.copyOfRange(start, end)
        if (end < usedSize) {
            buffer.copyInto(buffer, start, end, usedSize)
        }
        usedSize -= (end - start)
        return result
    }

    fun copyRangeAsBuffer(fromIndex: Int, toIndex: Int): ShortBuffer {
        val (start, end) = normalizeRange(fromIndex, toIndex)
        val newBuffer = ShortBuffer(end - start)
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

    fun minOrNull(): Short? {
        if (usedSize == 0) return null
        var minValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] < minValue) minValue = buffer[i]
        }
        return minValue
    }

    fun min(): Short = minOrNull() ?: throw NoSuchElementException("Buffer is empty")

    fun maxOrNull(): Short? {
        if (usedSize == 0) return null
        var maxValue = buffer[0]
        for (i in 1 until usedSize) {
            if (buffer[i] > maxValue) maxValue = buffer[i]
        }
        return maxValue
    }

    fun max(): Short = maxOrNull() ?: throw NoSuchElementException("Buffer is empty")

    ///////////////////////////////////////////////////////////////////////////
    // Iterable C + Numeric T
    ///////////////////////////////////////////////////////////////////////////

    fun sum(): Short {
        var s = 0
        for (i in 0 until usedSize) s += buffer[i]
        return s.toShort()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Companion Object
    ///////////////////////////////////////////////////////////////////////////

    companion object {
        fun empty(): ShortBuffer = ShortBuffer()

        fun withCapacity(capacity: Int): ShortBuffer = ShortBuffer(capacity)

        fun generate(size: Int, init: (Int) -> Short): ShortBuffer {
            val buffer = ShortBuffer(size)
            for (i in 0 until size) {
                buffer.add(init(i))
            }
            return buffer
        }

        fun concat(vararg buffers: ShortBuffer): ShortBuffer {
            // Pre-calculate the total number of elements
            val totalSize = buffers.sumOf { it.size }
            // Allocate the new buffer with the exact required capacity
            val result = ShortBuffer(totalSize)
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

        fun from(values: Collection<Short>): ShortBuffer {
            val buffer = ShortBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }

        fun from(values: ShortArray): ShortBuffer {
            return ShortBuffer(values)
        }

        fun from(values: ShortBuffer): ShortBuffer {
            return values.copy()
        }

        fun from(values: ShortDeque): ShortBuffer {
            return ShortBuffer(values.toShortArray())
        }

        fun of(vararg values: Short): ShortBuffer {
            val buffer = ShortBuffer(values.size)
            values.forEach { buffer.buffer[buffer.usedSize++] = it }
            return buffer
        }
    }
}

fun shortBufferOf(vararg values: Short): ShortBuffer = ShortBuffer.of(*values)