@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import kotlin.math.abs

sealed class Chunk<out A> : Iterable<A> {
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
            else -> {
                val buffer = arrayOfNulls<Any?>(APPEND_PREPEND_BUFFER_CAPACITY)
                buffer[0] = element
                AppendN(this, buffer, 1)
            }
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
            else -> {
                if (index !in 0 until size) {
                    throw IndexOutOfBoundsException("Update index=$index size=$size")
                }
                val indices = IntArray(UPDATE_BUFFER_CAPACITY)
                val values = arrayOfNulls<Any?>(UPDATE_BUFFER_CAPACITY)
                indices[0] = index
                values[0] = value
                Update(this, indices, values, 1)
            }
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
        val iterator = chunkIterator()
        var i = 0
        while (i < iterator.length && predicate(iterator.nextAt(i))) {
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
        val iterator = chunkIterator()
        var i = 0
        while (i < iterator.length && predicate(iterator.nextAt(i))) {
            i += 1
        }
        return take(i)
    }

    fun exists(predicate: (A) -> Boolean): Boolean {
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            if (predicate(iterator.nextAt(i))) {
                return true
            }
        }
        return false
    }

    fun forall(predicate: (A) -> Boolean): Boolean {
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            if (!predicate(iterator.nextAt(i))) {
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
        val leftIterator = chunkIterator()
        val rightIterator = that.chunkIterator()
        for (i in 0 until leftIterator.length) {
            if (!f(leftIterator.nextAt(i), rightIterator.nextAt(i))) {
                return false
            }
        }
        return true
    }

    fun filter(predicate: (A) -> Boolean): Chunk<A> {
        val builder = ChunkBuilder<A>(size)
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            val value = iterator.nextAt(i)
            if (predicate(value)) {
                builder += value
            }
        }
        return builder.result()
    }

    fun find(predicate: (A) -> Boolean): Option<@UnsafeVariance A> {
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            val value = iterator.nextAt(i)
            if (predicate(value)) {
                return Some(value)
            }
        }
        return None
    }

    fun <S> foldLeft(s0: S, f: (S, A) -> S): S {
        var acc = s0
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            acc = f(acc, iterator.nextAt(i))
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
        val iterator = chunkIterator()
        var i = 0
        while (i < iterator.length && pred(acc)) {
            acc = f(acc, iterator.nextAt(i))
            i += 1
        }
        return acc
    }

    fun <X, Y> partitionMap(transform: (A) -> Either<X, Y>): Pair<Chunk<X>, Chunk<Y>> {
        val left = ChunkBuilder<X>(size)
        val right = ChunkBuilder<Y>(size)
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            when (val value = transform(iterator.nextAt(i))) {
                is Left -> left += value.value
                is Right -> right += value.value
            }
        }
        return left.result() to right.result()
    }

    fun <B> map(transform: (A) -> B): Chunk<B> {
        val builder = ChunkBuilder<B>(size)
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            builder += transform(iterator.nextAt(i))
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
        val iterator = chunkIterator()
        for (i in 0 until iterator.length) {
            result[i] = iterator.nextAt(i)
        }
        return result
    }

    fun toList(): List<A> = toArray().toList()

    override operator fun iterator(): Iterator<A> {
        val iterator = chunkIterator()
        var index = 0
        return object : Iterator<A> {
            override fun hasNext(): Boolean = index < iterator.length

            override fun next(): A {
                if (!hasNext()) throw NoSuchElementException()
                return iterator.nextAt(index++)
            }
        }
    }

    open fun toStringChunk(): String = buildString(size) {
        val iterator = this@Chunk.chunkIterator()
        for (i in 0 until iterator.length) {
            append(iterator.nextAt(i).toString())
        }
    }

    final override fun equals(other: Any?): Boolean = other is Chunk<*> && corresponds(other) { a, b -> a == b }

    final override fun hashCode(): Int = foldLeft(1) { acc, value -> 31 * acc + (value?.hashCode() ?: 0) }

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

    @InternalDataApi
    class ArrayChunk<A> @InternalDataApi constructor(val array: Array<A>) : NonEmpty<A>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): A = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ArrayChunk index=$index size=${array.size}")
        }

        override fun toArray(): Array<A> = array.copyOf()

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class ByteArrayChunk @InternalDataApi constructor(val array: ByteArray) : NonEmpty<Byte>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Byte = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ByteArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Byte> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class BooleanArrayChunk @InternalDataApi constructor(val array: BooleanArray) : NonEmpty<Boolean>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Boolean = array.getOrElse(index) {
            throw IndexOutOfBoundsException("BooleanArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Boolean> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class IntArrayChunk @InternalDataApi constructor(val array: IntArray) : NonEmpty<Int>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Int = array.getOrElse(index) {
            throw IndexOutOfBoundsException("IntArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Int> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class ShortArrayChunk @InternalDataApi constructor(val array: ShortArray) : NonEmpty<Short>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Short = array.getOrElse(index) {
            throw IndexOutOfBoundsException("ShortArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Short> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class LongArrayChunk @InternalDataApi constructor(val array: LongArray) : NonEmpty<Long>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Long = array.getOrElse(index) {
            throw IndexOutOfBoundsException("LongArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Long> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class FloatArrayChunk @InternalDataApi constructor(val array: FloatArray) : NonEmpty<Float>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Float = array.getOrElse(index) {
            throw IndexOutOfBoundsException("FloatArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Float> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class DoubleArrayChunk @InternalDataApi constructor(val array: DoubleArray) : NonEmpty<Double>() {
        override val size: Int
            get() = array.size
        override val depth: Int = 1

        override fun get(index: Int): Double = array.getOrElse(index) {
            throw IndexOutOfBoundsException("DoubleArrayChunk index=$index size=${array.size}")
        }

        override fun chunkIterator(): ChunkIterator<Double> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class StringChunk @InternalDataApi constructor(val string: String) : NonEmpty<Char>() {
        override val size: Int
            get() = string.length
        override val depth: Int = 1

        override fun get(index: Int): Char = string.getOrElse(index) {
            throw IndexOutOfBoundsException("StringChunk index=$index size=${string.length}")
        }

        override fun toStringChunk(): String = string

        override fun chunkIterator(): ChunkIterator<Char> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class Single<A> @InternalDataApi constructor(val value: A) : NonEmpty<A>() {
        override val size: Int = 1
        override val depth: Int = 1

        override fun get(index: Int): A {
            if (index != 0) {
                throw IndexOutOfBoundsException("Single chunk access at $index")
            }
            return value
        }

        override fun chunkIterator(): ChunkIterator<A> = IndexedChunkIterator(this)
    }

    @InternalDataApi
    class Concat<A> @InternalDataApi constructor(val chunks: Array<Chunk<A>>) : NonEmpty<A>() {
        override val depth: Int by lazy { 1 + (chunks.maxOfOrNull { it.depth } ?: 0) }
        override val size: Int by lazy { chunks.sumOf { it.size } }
        override val concatDepth: Int by lazy { 1 + (chunks.maxOfOrNull { it.concatDepth } ?: 0) }
        private val chunkEnds: IntArray by lazy {
            val result = IntArray(chunks.size)
            var total = 0
            for (index in chunks.indices) {
                total += chunks[index].size
                result[index] = total
            }
            result
        }

        override fun get(index: Int): A {
            if (index !in 0 until size) {
                throw IndexOutOfBoundsException("Concat index=$index totalSize=$size")
            }
            val lookup = chunkEnds.binarySearchInt(index + 1)
            val chunkIndex = if (lookup >= 0) lookup else -lookup - 1
            val chunkStart = if (chunkIndex == 0) 0 else chunkEnds[chunkIndex - 1]
            return chunks[chunkIndex][index - chunkStart]
        }

        override fun rebalance(): Chunk<A> {
            val flattened = ArrayList<Chunk<A>>(chunks.size)
            fun visit(chunk: Chunk<A>) {
                when (chunk) {
                    is Concat -> chunk.chunks.forEach(::visit)
                    Empty -> Unit
                    else -> flattened += chunk
                }
            }
            chunks.forEach(::visit)
            return buildBalancedConcat(flattened, 0, flattened.size)
        }

        override fun chunkIterator(): ChunkIterator<A> = concatIteratorFor(chunks)
    }

    @InternalDataApi
    class Slice<A> @InternalDataApi constructor(val chunk: Chunk<A>, val offset: Int, val lengthSlice: Int) :
        NonEmpty<A>() {
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

        override fun chunkIterator(): ChunkIterator<A> = chunk.chunkIterator().sliceIterator(offset, lengthSlice)
    }

    internal class AppendN<A>(
        private val start: Chunk<A>,
        private val buffer: Array<Any?>,
        private val used: Int,
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = start.depth + 1
        override val size: Int
            get() = start.size + used

        override fun get(index: Int): A {
            val startSize = start.size
            return when {
                index < startSize -> start[index]
                index - startSize < used -> buffer[index - startSize] as A
                else -> throw IndexOutOfBoundsException("AppendN index=$index size=${startSize + used}")
            }
        }

        override fun toArray(): Array<A> {
            @Suppress("UNCHECKED_CAST")
            val result = arrayOfNulls<Any?>(start.size + used) as Array<A>
            start.toArray().copyInto(result, 0)
            for (i in 0 until used) {
                result[start.size + i] = buffer[i] as A
            }
            return result
        }

        fun appendOptimized(element: A): AppendN<A> {
            if (used >= buffer.size) {
                return spillAndAppend(element, used)
            }
            val newBuffer = buffer.copyOf()
            newBuffer[used] = element
            return AppendN(start, newBuffer, used + 1)
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

    internal class PrependN<A>(
        private val end: Chunk<A>,
        private val buffer: Array<Any?>,
        private val used: Int,
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = end.depth + 1
        override val size: Int
            get() = end.size + used

        override fun get(index: Int): A {
            return when {
                index < used -> buffer[buffer.size - used + index] as A
                index < end.size + used -> end[index - used]
                else -> throw IndexOutOfBoundsException("PrependN index=$index size=${end.size + used}")
            }
        }

        override fun toArray(): Array<A> {
            @Suppress("UNCHECKED_CAST")
            val result = arrayOfNulls<Any?>(end.size + used) as Array<A>
            val startIndex = buffer.size - used
            for (i in 0 until used) {
                result[i] = buffer[startIndex + i] as A
            }
            end.toArray().copyInto(result, used)
            return result
        }

        fun prependOptimized(element: A): PrependN<A> {
            if (used >= buffer.size) {
                return spillAndPrepend(element, used)
            }
            val newBuffer = buffer.copyOf()
            newBuffer[buffer.size - used - 1] = element
            return PrependN(end, newBuffer, used + 1)
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

    internal class Update<A>(
        private val chunk: Chunk<A>,
        private val bufferIndices: IntArray,
        private val bufferValues: Array<Any?>,
        private val used: Int,
    ) : NonEmpty<A>() {
        override val depth: Int
            get() = chunk.depth + 1
        override val size: Int
            get() = chunk.size

        override fun get(index: Int): A {
            if (index !in 0 until size) {
                throw IndexOutOfBoundsException("Update index=$index size=$size")
            }
            var i = used - 1
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
            for (i in 0 until used) {
                base[bufferIndices[i]] = bufferValues[i] as A
            }
            return base
        }

        fun updateOptimized(index: Int, value: A): Update<A> {
            if (index !in 0 until size) {
                throw IndexOutOfBoundsException("Update index=$index size=$size")
            }
            if (used >= bufferIndices.size) {
                return spillAndUpdate(index, value)
            }
            val newIndices = bufferIndices.copyOf()
            val newValues = bufferValues.copyOf()
            newIndices[used] = index
            newValues[used] = value
            return Update(chunk, newIndices, newValues, used + 1)
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

private class SlicedChunkIterator<A>(
    private val delegate: ChunkIterator<A>,
    private val offset: Int,
    override val length: Int,
) : ChunkIterator<A> {
    override fun nextAt(index: Int): A {
        if (index !in 0 until length) {
            throw IndexOutOfBoundsException("ChunkIterator nextAt($index) length=$length")
        }
        return delegate.nextAt(offset + index)
    }

    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
        val start = offset.coerceIn(0, this.length)
        val end = (offset + length).coerceIn(start, this.length)
        val newLength = end - start
        return if (newLength == 0) EmptyChunkIterator else SlicedChunkIterator(delegate, this.offset + start, newLength)
    }
}

private class ConcatChunkIterator<A>(
    private val children: Array<ChunkIterator<A>>,
) : ChunkIterator<A> {
    private val childOffsets = IntArray(children.size)
    override val length: Int
    private var cursorIndex: Int = 0

    init {
        var total = 0
        for (index in children.indices) {
            childOffsets[index] = total
            total += children[index].length
        }
        length = total
    }

    override fun nextAt(index: Int): A {
        if (index !in 0 until length) {
            throw IndexOutOfBoundsException("ChunkIterator nextAt($index) length=$length")
        }
        val childIndex = childIndexFor(index)
        return children[childIndex].nextAt(index - childOffsets[childIndex])
    }

    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
        val start = offset.coerceIn(0, this.length)
        val end = (offset + length).coerceIn(start, this.length)
        val newLength = end - start
        return if (newLength == 0) EmptyChunkIterator else SlicedChunkIterator(this, start, newLength)
    }

    private fun childIndexFor(index: Int): Int {
        if (children.size == 1) {
            return 0
        }

        val currentStart = childOffsets[cursorIndex]
        val currentEnd = if (cursorIndex == children.lastIndex) length else childOffsets[cursorIndex + 1]
        if (index in currentStart until currentEnd) {
            return cursorIndex
        }

        if (index > currentStart) {
            var candidate = cursorIndex + 1
            while (candidate < children.size) {
                val candidateEnd = if (candidate == children.lastIndex) length else childOffsets[candidate + 1]
                if (index < candidateEnd) {
                    cursorIndex = candidate
                    return candidate
                }
                candidate += 1
            }
        }

        val lookup = childOffsets.binarySearchInt(index)
        val resolved = if (lookup >= 0) lookup else (-lookup - 2).coerceAtLeast(0)
        cursorIndex = resolved
        return resolved
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

private fun IntArray.binarySearchInt(value: Int): Int {
    var low = 0
    var high = size - 1

    while (low <= high) {
        val middle = (low + high).ushr(1)
        val middleValue = this[middle]
        when {
            middleValue < value -> low = middle + 1
            middleValue > value -> high = middle - 1
            else -> return middle
        }
    }

    return -(low + 1)
}

private fun <A> buildBalancedConcat(chunks: List<Chunk<A>>, start: Int, end: Int): Chunk<A> =
    when (end - start) {
        0 -> Chunk.empty()
        1 -> chunks[start]
        2 -> Chunk.Concat(arrayOf(chunks[start], chunks[start + 1]))
        else -> {
            val middle = start + (end - start) / 2
            Chunk.Concat(arrayOf(buildBalancedConcat(chunks, start, middle), buildBalancedConcat(chunks, middle, end)))
        }
    }

private fun <A> concatIteratorFor(chunks: Array<Chunk<A>>): ChunkIterator<A> {
    val iterators = ArrayList<ChunkIterator<A>>(chunks.size)

    fun visit(chunk: Chunk<A>) {
        when (chunk) {
            is Chunk.Concat -> chunk.chunks.forEach(::visit)
            Chunk.Empty -> Unit
            else -> iterators += chunk.chunkIterator()
        }
    }

    chunks.forEach(::visit)

    return when (iterators.size) {
        0 -> Chunk.empty<A>().chunkIterator()
        1 -> iterators[0]
        else -> ConcatChunkIterator(iterators.toTypedArray())
    }
}

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
