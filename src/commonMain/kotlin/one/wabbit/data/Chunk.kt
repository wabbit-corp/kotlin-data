@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package one.wabbit.data

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs

sealed class Chunk<out A> {
    abstract val size: Int
    internal abstract val depth: Int
    internal open val concatDepth: Int
        get() = depth

    fun isEmpty(): Boolean = size == 0

    fun isNotEmpty(): Boolean = size != 0

    operator abstract fun get(index: Int): A

    operator fun plus(that: Chunk<@UnsafeVariance A>): Chunk<A> =
        when {
            isEmpty() -> that
            that.isEmpty() -> this
            that is Single<*> -> append(that.value as A)
            this is Single<*> -> that.prepend(this.value as A)
            abs(concatDepth - that.concatDepth) <= 1 -> Concat(arrayOf(this, that))
            else -> Concat(arrayOf(this, that)).rebalance()
        }

    fun append(element: @UnsafeVariance A): Chunk<A> =
        when (this) {
            Empty -> Single(element)
            is AppendN -> appendOptimized(element)
            else -> AppendN(this, arrayOfNulls(APPEND_PREPEND_BUFFER_CAPACITY), 1).also { it.buffer[0] = element }
        }

    fun prepend(element: @UnsafeVariance A): Chunk<A> =
        when (this) {
            Empty -> Single(element)
            is PrependN -> prependOptimized(element)
            else -> {
                val buffer = arrayOfNulls<Any?>(APPEND_PREPEND_BUFFER_CAPACITY)
                buffer[APPEND_PREPEND_BUFFER_CAPACITY - 1] = element
                PrependN(this, buffer, 1)
            }
        }

    fun update(index: Int, value: @UnsafeVariance A): Chunk<A> =
        when (this) {
            Empty -> throw IndexOutOfBoundsException("Update index=$index size=0")
            is Update -> updateOptimized(index, value)
            else -> Update(this, IntArray(UPDATE_BUFFER_CAPACITY), arrayOfNulls(UPDATE_BUFFER_CAPACITY), 0)
                .updateOptimized(index, value)
        }

    fun drop(n: Int): Chunk<A> =
        when {
            n <= 0 -> this
            n >= size -> Empty
            else -> Slice(this, n, size - n)
        }

    fun dropRight(n: Int): Chunk<A> =
        when {
            n <= 0 -> this
            n >= size -> Empty
            else -> Slice(this, 0, size - n)
        }

    fun dropUntil(predicate: (A) -> Boolean): Chunk<A> {
        val index = indexWhere(predicate)
        return if (index < 0) Empty else drop(index)
    }

    fun dropWhile(predicate: (A) -> Boolean): Chunk<A> {
        var i = 0
        while (i < size && predicate(this[i])) {
            i += 1
        }
        return drop(i)
    }

    fun take(n: Int): Chunk<A> =
        when {
            n <= 0 -> Empty
            n >= size -> this
            else -> Slice(this, 0, n)
        }

    fun takeRight(n: Int): Chunk<A> =
        when {
            n <= 0 -> Empty
            n >= size -> this
            else -> Slice(this, size - n, n)
        }

    fun takeWhile(predicate: (A) -> Boolean): Chunk<A> {
        var i = 0
        while (i < size && predicate(this[i])) {
            i += 1
        }
        return take(i)
    }

    fun exists(predicate: (A) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (predicate(this[i])) {
                return true
            }
        }
        return false
    }

    fun forall(predicate: (A) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (!predicate(this[i])) {
                return false
            }
        }
        return true
    }

    val head: @UnsafeVariance A
        get() {
            if (isEmpty()) {
                error("head of empty chunk")
            }
            return this[0]
        }

    fun headOption(): Option<@UnsafeVariance A> =
        if (isEmpty()) None else Some(this[0])

    fun lastOption(): Option<@UnsafeVariance A> =
        if (isEmpty()) None else Some(this[size - 1])

    fun indexWhere(predicate: (A) -> Boolean, from: Int = 0): Int {
        var i = from
        while (i < size) {
            if (predicate(this[i])) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun <B> corresponds(that: Chunk<B>, f: (A, B) -> Boolean): Boolean {
        if (size != that.size) {
            return false
        }
        for (i in 0 until size) {
            if (!f(this[i], that[i])) {
                return false
            }
        }
        return true
    }

    fun filter(predicate: (A) -> Boolean): Chunk<A> {
        val builder = ChunkBuilder<A>(size)
        for (i in 0 until size) {
            val value = this[i]
            if (predicate(value)) {
                builder += value
            }
        }
        return builder.result()
    }

    fun find(predicate: (A) -> Boolean): Option<@UnsafeVariance A> {
        for (i in 0 until size) {
            val value = this[i]
            if (predicate(value)) {
                return Some(value)
            }
        }
        return None
    }

    fun <S> foldLeft(s0: S, f: (S, A) -> S): S {
        var acc = s0
        for (i in 0 until size) {
            acc = f(acc, this[i])
        }
        return acc
    }

    fun <S> foldRight(s0: S, f: (A, S) -> S): S {
        var acc = s0
        for (i in size - 1 downTo 0) {
            acc = f(this[i], acc)
        }
        return acc
    }

    fun <S> foldWhile(s0: S, pred: (S) -> Boolean, f: (S, A) -> S): S {
        var acc = s0
        var i = 0
        while (i < size && pred(acc)) {
            acc = f(acc, this[i])
            i += 1
        }
        return acc
    }

    fun <X, Y> partitionMap(transform: (A) -> Either<X, Y>): Pair<Chunk<X>, Chunk<Y>> {
        val left = ChunkBuilder<X>(size)
        val right = ChunkBuilder<Y>(size)
        for (i in 0 until size) {
            when (val value = transform(this[i])) {
                is Left -> left += value.value
                is Right -> right += value.value
            }
        }
        return left.result() to right.result()
    }

    fun <B> map(transform: (A) -> B): Chunk<B> {
        val builder = ChunkBuilder<B>(size)
        for (i in 0 until size) {
            builder += transform(this[i])
        }
        return builder.result()
    }

    fun slice(from: Int, until: Int): Chunk<A> {
        val start = from.coerceIn(0, size)
        val end = until.coerceIn(start, size)
        val length = end - start
        return when {
            length <= 0 -> Empty
            start == 0 && length == size -> this
            else -> Slice(this, start, length)
        }
    }

    fun splitAt(n: Int): Pair<Chunk<A>, Chunk<A>> = take(n) to drop(n)

    fun splitWhere(predicate: (A) -> Boolean): Pair<Chunk<A>, Chunk<A>> {
        val index = indexWhere(predicate)
        return if (index < 0) this to Empty else take(index) to drop(index)
    }

    @Suppress("UNCHECKED_CAST")
    open fun toArray(): Array<@UnsafeVariance A> {
        val result = arrayOfNulls<Any?>(size) as Array<A>
        for (i in 0 until size) {
            result[i] = this[i]
        }
        return result
    }

    fun toList(): List<A> = toArray().toList()

    open fun toStringChunk(): String = buildString(size) {
        for (i in 0 until size) {
            append(this@Chunk[i].toString())
        }
    }

    override fun toString(): String = toList().joinToString(prefix = "Chunk(", postfix = ")")

    open fun materialize(): Chunk<A> =
        when (size) {
            0 -> Empty
            1 -> Single(this[0])
            else -> ArrayChunk(toArray())
        }

    internal open fun rebalance(): Chunk<A> = this

    abstract fun chunkIterator(): ChunkIterator<@UnsafeVariance A>

    sealed class NonEmpty<out A> : Chunk<A>()

    data object Empty : Chunk<Nothing>() {
        override val size: Int = 0
        override val depth: Int = 0

        override fun get(index: Int): Nothing =
            throw IndexOutOfBoundsException("Empty chunk access at $index")

        override fun chunkIterator(): ChunkIterator<Nothing> = EmptyChunkIterator
    }

    class ArrayChunk<A>(val array: Array<A>) : NonEmpty<A>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): A = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ArrayChunk index=$index size=${array.size}")
        }

        override fun toArray(): Array<A> = array.copyOf()

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is ArrayChunk<*> && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class ByteArrayChunk(val array: ByteArray) : NonEmpty<Byte>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Byte = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ByteArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Byte> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is ByteArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class BooleanArrayChunk(val array: BooleanArray) : NonEmpty<Boolean>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Boolean = array.getOrElse(index) {
            throw IndexOutOfBoundsException("BooleanArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Boolean> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is BooleanArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class IntArrayChunk(val array: IntArray) : NonEmpty<Int>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Int = array.getOrElse(index) {
            throw IndexOutOfBoundsException("IntArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Int> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is IntArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class ShortArrayChunk(val array: ShortArray) : NonEmpty<Short>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Short = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ShortArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Short> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is ShortArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class LongArrayChunk(val array: LongArray) : NonEmpty<Long>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Long = array.getOrElse(index) {
            throw IndexOutOfBoundsException("LongArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Long> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is LongArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class FloatArrayChunk(val array: FloatArray) : NonEmpty<Float>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Float = array.getOrElse(index) {
            throw IndexOutOfBoundsException("FloatArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Float> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is FloatArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class DoubleArrayChunk(val array: DoubleArray) : NonEmpty<Double>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Double = array.getOrElse(index) {
            throw IndexOutOfBoundsException("DoubleArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Double> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is DoubleArrayChunk && array.contentEquals(other.array)

        override fun hashCode(): Int = array.contentHashCode()
    }

    class StringChunk(val string: String) : NonEmpty<Char>() {
        override val size: Int
            get() = string.length
        override val depth: Int = 1

        override fun get(index: Int): Char = string.getOrElse(index) {
            throw IndexOutOfBoundsException("StringChunk index=$index size=${string.length}")
        }

        override fun toStringChunk(): String = string

        override fun chunkIterator(): ChunkIterator<Char> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is StringChunk && string == other.string

        override fun hashCode(): Int = string.hashCode()
    }

    class Single<A>(val value: A) : NonEmpty<A>() {
        override val size: Int = 1
        override val depth: Int = 1

        override fun get(index: Int): A {
            if (index != 0) {
                throw IndexOutOfBoundsException("Single chunk access at $index")
            }
            return value
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is Single<*> && value == other.value

        override fun hashCode(): Int = value?.hashCode() ?: 0
    }

    class Concat<A>(val chunks: Array<Chunk<A>>) : NonEmpty<A>() {
        override val depth: Int by lazy { 1 + (chunks.maxOfOrNull { it.depth } ?: 0) }
        override val size: Int by lazy { chunks.sumOf { it.size } }
        override val concatDepth: Int by lazy { 1 + (chunks.maxOfOrNull { it.concatDepth } ?: 0) }

        override fun get(index: Int): A {
            var remaining = index
            for (chunk in chunks) {
                if (remaining < chunk.size) {
                    return chunk[remaining]
                }
                remaining -= chunk.size
            }
            throw IndexOutOfBoundsException("Concat index=$index totalSize=$size")
        }

        override fun rebalance(): Chunk<A> {
            val flattened = ArrayList<Chunk<A>>(chunks.size)
            fun visit(chunk: Chunk<A>) {
                when (chunk) {
                    is Concat -> chunk.chunks.forEach(::visit)
                    else -> flattened += chunk
                }
            }
            chunks.forEach(::visit)
            return when (flattened.size) {
                0 -> Empty
                1 -> flattened[0]
                else -> Concat(flattened.toTypedArray())
            }
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)

        override fun equals(other: Any?): Boolean = other is Chunk<*> && corresponds(other) { a, b -> a == b }

        override fun hashCode(): Int = foldLeft(1) { acc, value -> 31 * acc + (value?.hashCode() ?: 0) }
    }

    class Slice<A>(val chunk: Chunk<A>, val offset: Int, val lengthSlice: Int) : NonEmpty<A>() {
        override val size: Int
            get() = lengthSlice
        override val depth: Int
            get() = chunk.depth + 1

        override fun get(index: Int): A {
            if (index !in 0 until lengthSlice) {
                throw IndexOutOfBoundsException("Slice index=$index offset=$offset length=$lengthSlice")
            }
            return chunk[offset + index]
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal class AppendN<A>(
        private val start: Chunk<A>,
        internal val buffer: Array<Any?>,
        initialUsed: Int,
        private val used: AtomicInt = AtomicInt(initialUsed),
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = start.depth + 1
        override val size: Int
            get() = start.size + used.load()

        override fun get(index: Int): A {
            val startSize = start.size
            val currentUsed = used.load()
            return when {
                index < startSize -> start[index]
                index - startSize < currentUsed -> buffer[index - startSize] as A
                else -> throw IndexOutOfBoundsException("AppendN index=$index size=${startSize + currentUsed}")
            }
        }

        override fun toArray(): Array<A> {
            val currentUsed = used.load()
            @Suppress("UNCHECKED_CAST")
            val result = arrayOfNulls<Any?>(start.size + currentUsed) as Array<A>
            start.toArray().copyInto(result, 0)
            for (i in 0 until currentUsed) {
                result[start.size + i] = buffer[i] as A
            }
            return result
        }

        fun appendOptimized(element: A): AppendN<A> {
            while (true) {
                val currentUsed = used.load()
                if (currentUsed >= buffer.size) {
                    return spillAndAppend(element, currentUsed)
                }
                if (used.compareAndSet(currentUsed, currentUsed + 1)) {
                    buffer[currentUsed] = element
                    return this
                }
            }
        }

        private fun spillAndAppend(element: A, currentUsed: Int): AppendN<A> {
            val chunked = arrayChunkFromBuffer<A>(buffer, currentUsed)
            val newStart = start + chunked
            val newBuffer = arrayOfNulls<Any?>(APPEND_PREPEND_BUFFER_CAPACITY)
            newBuffer[0] = element
            return AppendN(newStart, newBuffer, 1)
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal class PrependN<A>(
        private val end: Chunk<A>,
        private val buffer: Array<Any?>,
        initialUsed: Int,
        private val used: AtomicInt = AtomicInt(initialUsed),
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = end.depth + 1
        override val size: Int
            get() = end.size + used.load()

        override fun get(index: Int): A {
            val currentUsed = used.load()
            return when {
                index < currentUsed -> buffer[buffer.size - currentUsed + index] as A
                index < end.size + currentUsed -> end[index - currentUsed]
                else -> throw IndexOutOfBoundsException("PrependN index=$index size=${end.size + currentUsed}")
            }
        }

        override fun toArray(): Array<A> {
            val currentUsed = used.load()
            @Suppress("UNCHECKED_CAST")
            val result = arrayOfNulls<Any?>(end.size + currentUsed) as Array<A>
            val startIndex = buffer.size - currentUsed
            for (i in 0 until currentUsed) {
                result[i] = buffer[startIndex + i] as A
            }
            end.toArray().copyInto(result, currentUsed)
            return result
        }

        fun prependOptimized(element: A): PrependN<A> {
            while (true) {
                val currentUsed = used.load()
                if (currentUsed >= buffer.size) {
                    return spillAndPrepend(element, currentUsed)
                }
                if (used.compareAndSet(currentUsed, currentUsed + 1)) {
                    buffer[buffer.size - currentUsed - 1] = element
                    return this
                }
            }
        }

        private fun spillAndPrepend(element: A, currentUsed: Int): PrependN<A> {
            val chunked = arrayChunkFromTailBuffer<A>(buffer, currentUsed)
            val newEnd = chunked + end
            val newBuffer = arrayOfNulls<Any?>(APPEND_PREPEND_BUFFER_CAPACITY)
            newBuffer[APPEND_PREPEND_BUFFER_CAPACITY - 1] = element
            return PrependN(newEnd, newBuffer, 1)
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal class Update<A>(
        private val chunk: Chunk<A>,
        private val bufferIndices: IntArray,
        private val bufferValues: Array<Any?>,
        initialUsed: Int,
        private val used: AtomicInt = AtomicInt(initialUsed),
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = chunk.depth + 1
        override val size: Int
            get() = chunk.size

        override fun get(index: Int): A {
            if (index !in 0 until size) {
                throw IndexOutOfBoundsException("Update index=$index size=$size")
            }
            var i = used.load() - 1
            while (i >= 0) {
                if (bufferIndices[i] == index) {
                    return bufferValues[i] as A
                }
                i -= 1
            }
            return chunk[index]
        }

        override fun toArray(): Array<A> {
            val base = chunk.toArray()
            val currentUsed = used.load()
            for (i in 0 until currentUsed) {
                base[bufferIndices[i]] = bufferValues[i] as A
            }
            return base
        }

        fun updateOptimized(index: Int, value: A): Update<A> {
            if (index !in 0 until size) {
                throw IndexOutOfBoundsException("Update index=$index size=$size")
            }
            while (true) {
                val currentUsed = used.load()
                if (currentUsed >= bufferIndices.size) {
                    return spillAndUpdate(index, value)
                }
                if (used.compareAndSet(currentUsed, currentUsed + 1)) {
                    bufferIndices[currentUsed] = index
                    bufferValues[currentUsed] = value
                    return this
                }
            }
        }

        private fun spillAndUpdate(index: Int, value: A): Update<A> {
            val newBase = ArrayChunk(toArray())
            val newIndices = IntArray(UPDATE_BUFFER_CAPACITY)
            val newValues = arrayOfNulls<Any?>(UPDATE_BUFFER_CAPACITY)
            newIndices[0] = index
            newValues[0] = value
            return Update(newBase, newIndices, newValues, 1)
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <A> empty(): Chunk<A> = Empty as Chunk<A>

        fun <A> chunkOf(vararg elements: A): Chunk<A> = fromArray(elements)

        fun <A> fromArray(array: Array<out A>): Chunk<A> =
            when (array.size) {
                0 -> empty()
                1 -> Single(array[0])
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    ArrayChunk(array.copyOf() as Array<A>)
                }
            }

        fun fromByteArray(array: ByteArray): Chunk<Byte> =
            if (array.isEmpty()) empty() else ByteArrayChunk(array.copyOf())

        fun fromBooleanArray(array: BooleanArray): Chunk<Boolean> =
            if (array.isEmpty()) empty() else BooleanArrayChunk(array.copyOf())

        fun fromIntArray(array: IntArray): Chunk<Int> =
            if (array.isEmpty()) empty() else IntArrayChunk(array.copyOf())

        fun fromShortArray(array: ShortArray): Chunk<Short> =
            if (array.isEmpty()) empty() else ShortArrayChunk(array.copyOf())

        fun fromLongArray(array: LongArray): Chunk<Long> =
            if (array.isEmpty()) empty() else LongArrayChunk(array.copyOf())

        fun fromFloatArray(array: FloatArray): Chunk<Float> =
            if (array.isEmpty()) empty() else FloatArrayChunk(array.copyOf())

        fun fromDoubleArray(array: DoubleArray): Chunk<Double> =
            if (array.isEmpty()) empty() else DoubleArrayChunk(array.copyOf())

        fun fromString(string: String): Chunk<Char> =
            if (string.isEmpty()) empty() else StringChunk(string)

        fun <A> single(value: A): Chunk<A> = Single(value)

        fun <S, A> unfold(seed: S, generate: (S) -> Pair<A, S>?): Chunk<A> {
            val builder = ChunkBuilder<A>()
            var state = seed
            while (true) {
                val next = generate(state) ?: break
                builder += next.first
                state = next.second
            }
            return builder.result()
        }
    }
}

fun <A> chunkOf(vararg elements: A): Chunk<A> = Chunk.chunkOf(*elements)

class ChunkBuilder<A>(initialCapacity: Int = 16) {
    private val buffer = ArrayList<A>(initialCapacity)

    operator fun plusAssign(value: A) {
        buffer += value
    }

    fun add(value: A) {
        buffer += value
    }

    fun size(): Int = buffer.size

    fun result(): Chunk<A> = Chunk.fromArray(buffer.toChunkArray())
}

interface ChunkIterator<out A> {
    val length: Int

    fun hasNextAt(index: Int): Boolean = index in 0 until length

    fun nextAt(index: Int): A

    fun sliceIterator(offset: Int, length: Int): ChunkIterator<A>
}

private data object EmptyChunkIterator : ChunkIterator<Nothing> {
    override val length: Int = 0

    override fun nextAt(index: Int): Nothing =
        throw IndexOutOfBoundsException("Empty ChunkIterator nextAt($index)")

    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Nothing> = this
}

private class IndexedChunkIterator<A>(
    private val chunk: Chunk<A>,
    private val offset: Int = 0,
    override val length: Int = chunk.size,
) : ChunkIterator<A> {
    override fun nextAt(index: Int): A {
        if (index !in 0 until length) {
            throw IndexOutOfBoundsException("ChunkIterator nextAt($index) length=$length")
        }
        return chunk[offset + index]
    }

    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
        val start = offset.coerceIn(0, this.length)
        val end = (offset + length).coerceIn(start, this.length)
        val newLength = end - start
        return if (newLength == 0) EmptyChunkIterator else IndexedChunkIterator(chunk, this.offset + start, newLength)
    }
}

private fun <A> List<A>.toChunkArray(): Array<A> {
    @Suppress("UNCHECKED_CAST")
    val result = arrayOfNulls<Any?>(size) as Array<A>
    for (i in indices) {
        result[i] = this[i]
    }
    return result
}

private const val APPEND_PREPEND_BUFFER_CAPACITY = 64
private const val UPDATE_BUFFER_CAPACITY = 256

private fun <A> arrayChunkFromBuffer(buffer: Array<Any?>, used: Int): Chunk.ArrayChunk<A> {
    @Suppress("UNCHECKED_CAST")
    val chunkArray = arrayOfNulls<Any?>(used) as Array<A>
    for (i in 0 until used) {
        chunkArray[i] = buffer[i] as A
    }
    return Chunk.ArrayChunk(chunkArray)
}

private fun <A> arrayChunkFromTailBuffer(buffer: Array<Any?>, used: Int): Chunk.ArrayChunk<A> {
    @Suppress("UNCHECKED_CAST")
    val chunkArray = arrayOfNulls<Any?>(used) as Array<A>
    val startIndex = buffer.size - used
    for (i in 0 until used) {
        chunkArray[i] = buffer[startIndex + i] as A
    }
    return Chunk.ArrayChunk(chunkArray)
}
