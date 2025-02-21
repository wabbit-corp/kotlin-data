//package one.wabbit.data
//
///**
// * Represents an immutable, persistent collection of values of type [A].
// *
// * The [Chunk] type provides high-performance operations for concatenation,
// * slicing, mapping, folding, and many other operations. It also includes
// * specialized representations for primitive arrays to improve performance.
// *
// * ### Hierarchy
// *
// * - `Empty` – Represents an empty chunk.
// * - `NonEmpty<A>` – Base class for all non-empty chunk representations.
// *   - `ArrayChunk<A>` – Backed by a generic Kotlin `Array<A>`.
// *   - Primitive array chunks (`ByteArrayChunk`, `IntArrayChunk`, etc.) –
// *     Backed by specialized arrays for performance.
// *   - `StringChunk` – Treats a `String` as a chunk of `Char`.
// *   - `Single<A>` – A single-element chunk.
// *   - `Concat<A>` – Balanced concatenation tree of multiple chunks.
// *   - `Slice<A>` – A view into a subrange of another chunk.
// *   - `AppendN<A>` / `PrependN<A>` / `Update<A>` – Internal node types
// *     used to efficiently append/prepend/update values without fully
// *     copying arrays.
// *   - `BitChunk<T>` – Abstract class for chunk-of-bits representations
// *     (backed by [Byte], [Int], or [Long]), used when interpreting booleans
// *     as bits.
// *
// * ### Concurrency
// *
// * Some node types ([AppendN], [PrependN], [Update]) use atomic counters
// * (via [kotlinx.atomicfu]) to coordinate certain in-place modifications
// * for performance, while preserving an immutable interface externally.
// *
// * ### Key Operations
// *
// * - [plus] (`operator fun plus(...)`): Concatenates chunks.
// * - [drop], [take], [slice], [map], [filter], [fold], etc.
// * - Specialized typed accessors, e.g. [byte], [int], [boolean], etc.
// * - Bitwise operations for boolean chunks ([and], [or], [xor], [negate]).
// * - Conversion / extraction operations: [toArray], [toList], [toBinaryString].
// * - [chunkOf] constructor and other factories.
// *
// * This implementation aims to mirror the functionality of ZIO's Scala `Chunk`.
// *
// * **Performance Considerations**:
// * - Many operations are carefully implemented to avoid unnecessary copying.
// * - For bulk processing, prefer methods like [materialize] or specialized
// *   array-backed chunks.
// */
//sealed class Chunk<out A> {
//    /**
//     * The number of elements in this chunk.
//     */
//    abstract val size: Int
//
//    /**
//     * Internal “tree depth” used by certain chunk operations
//     * that rebalance or optimize chunk concatenation.
//     */
//    internal abstract val depth: Int
//
//    /**
//     * Returns the "concat depth" of this chunk for balanced concatenation.
//     * By default, zero. Subclasses override if relevant.
//     */
//    internal open val concatDepth: Int get() = 0
//
//    /**
//     * Returns `true` if this chunk is empty, `false` otherwise.
//     */
//    fun isEmpty(): Boolean = (this is Empty)
//
//    /**
//     * Returns `true` if this chunk is not empty, `false` otherwise.
//     */
//    fun isNotEmpty(): Boolean = !isEmpty()
//
//    /**
//     * Returns the element at the specified [index], or throws an
//     * [IndexOutOfBoundsException] if out of range.
//     */
//    operator abstract fun get(index: Int): A
//
//    /**
//     * Concatenates this chunk with another chunk and returns a new chunk
//     * containing all elements of both.
//     *
//     * This operation attempts to keep a balanced concatenation tree for
//     * performance.
//     */
//    operator fun plus(that: Chunk<@UnsafeVariance A>): Chunk<A> =
//        when {
//            this.isEmpty() -> that
//            that.isEmpty() -> this
//            else           -> {
//                // Balanced concatenation logic adapted from the Scala code.
//                val diff = that.concatDepth - this.concatDepth
//                if (kotlin.math.abs(diff) <= 1) Concat(arrayOf(this, that))
//                else {
//                    // If one side is much deeper, we rebalance.
//                    balanceConcat(this, that)
//                }
//            }
//        }
//
//    operator fun plus(that: NonEmpty<@UnsafeVariance A>): NonEmpty<A> =
//        when {
//            this.isEmpty() -> that
//            else           -> {
//                val result = this + that as Chunk<A>
//                // We assume result is not empty
//                if (result.isEmpty()) error("Unexpected empty after plus.")
//                result as NonEmpty<A>
//            }
//        }
//
//    /**
//     * Returns a new chunk without the first [n] elements.
//     * If [n] <= 0, returns this.
//     * If [n] >= size, returns [Empty].
//     */
//    fun drop(n: Int): Chunk<A> {
//        if (n <= 0) return this
//        if (n >= size) return Empty
//        return Slice(this, n, size - n)
//    }
//    /**
//     * Returns a new chunk without the last [n] elements.
//     * If [n] <= 0, returns this.
//     * If [n] >= size, returns [Empty].
//     */
//    fun dropRight(n: Int): Chunk<A> {
//        if (n <= 0) return this
//        if (n >= size) return Empty
//        return Slice(this, 0, size - n)
//    }
//
//    /**
//     * Drops elements until [predicate] is `true` for some element,
//     * then returns the remaining chunk starting from that element.
//     */
//    fun dropUntil(predicate: (A) -> Boolean): Chunk<A> {
//        val idx = indexWhere(predicate)
//        return if (idx < 0) Empty else drop(idx)
//    }
//
//    /**
//     * Drops elements as long as [predicate] returns `true`.
//     * Once an element fails the predicate, returns the remaining chunk.
//     */
//    fun dropWhile(predicate: (A) -> Boolean): Chunk<A> {
//        var i = 0
//        while (i < size && predicate(this[i])) {
//            i++
//        }
//        return drop(i)
//    }
//
//    /**
//     * Takes the first [n] elements of the chunk (or fewer if the chunk
//     * is smaller).
//     */
//    fun take(n: Int): Chunk<A> {
//        if (n <= 0) return Empty
//        if (n >= size) return this
//        return Slice(this, 0, n)
//    }
//
//    /**
//     * Takes the last [n] elements of the chunk.
//     */
//    fun takeRight(n: Int): Chunk<A> {
//        if (n <= 0) return Empty
//        if (n >= size) return this
//        return Slice(this, size - n, n)
//    }
//
//    /**
//     * Takes elements while [predicate] returns `true`. Once the predicate
//     * fails, iteration stops, and the chunk up to that point is returned.
//     */
//    fun takeWhile(predicate: (A) -> Boolean): Chunk<A> {
//        var i = 0
//        while (i < size && predicate(this[i])) {
//            i++
//        }
//        return take(i)
//    }
//
//    /**
//     * Tests whether at least one element satisfies [predicate].
//     */
//    fun exists(predicate: (A) -> Boolean): Boolean {
//        for (i in 0 until size) {
//            if (predicate(this[i])) return true
//        }
//        return false
//    }
//
//    /**
//     * Tests whether all elements satisfy [predicate].
//     */
//    fun forall(predicate: (A) -> Boolean): Boolean {
//        for (i in 0 until size) {
//            if (!predicate(this[i])) return false
//        }
//        return true
//    }
//
//    /**
//     * Returns the first element in this chunk, or throws if empty.
//     */
//    val head: @UnsafeVariance A
//        get() {
//            if (isEmpty()) throw NoSuchElementException("head of empty chunk")
//            return this[0]
//        }
//
//    /**
//     * Returns an option of the first element in this chunk, or [None] if empty.
//     */
//    fun headOption(): Option<@UnsafeVariance A> =
//        if (isEmpty()) None else Some(this[0])
//
//    /**
//     * Returns an option of the last element in this chunk, or [None] if empty.
//     */
//    fun lastOption(): Option<@UnsafeVariance A> =
//        if (isEmpty()) None else Some(this[size - 1])
//
//    /**
//     * Finds the first index at or after [from] for which [predicate] returns true,
//     * or -1 if none.
//     */
//    fun indexWhere(predicate: (A) -> Boolean, from: Int = 0): Int {
//        var i = from
//        while (i < size) {
//            if (predicate(this[i])) return i
//            i++
//        }
//        return -1
//    }
//
//    /**
//     * Returns true if this chunk and [that] have the same length and corresponding
//     * elements match [f].
//     */
//    fun <B> corresponds(that: Chunk<B>, f: (A, B) -> Boolean): Boolean {
//        if (this.size != that.size) return false
//        for (i in 0 until this.size) {
//            if (!f(this[i], that[i])) return false
//        }
//        return true
//    }
//
//    /**
//     * Filters elements by [predicate].
//     */
//    fun filter(predicate: (A) -> Boolean): Chunk<A> {
//        // For performance, we can do a quick pass if nearly all pass.
//        val builder = ChunkBuilder<A>(this.size)
//        for (i in 0 until size) {
//            val x = this[i]
//            if (predicate(x)) builder += x
//        }
//        return builder.result()
//    }
//
//    /**
//     * Finds the first element that satisfies [predicate], or returns [None].
//     */
//    fun find(predicate: (A) -> Boolean): Option<@UnsafeVariance A> {
//        for (i in 0 until size) {
//            val x = this[i]
//            if (predicate(x)) return Some(x)
//        }
//        return None
//    }
//
//    /**
//     * Folds from the left with initial [s0], applying [f] to accumulate results.
//     */
//    fun <S> foldLeft(s0: S, f: (S, A) -> S): S {
//        var acc = s0
//        for (i in 0 until size) {
//            acc = f(acc, this[i])
//        }
//        return acc
//    }
//
//    /**
//     * Folds from the right with initial [s0], applying [f].
//     */
//    fun <S> foldRight(s0: S, f: (A, S) -> S): S {
//        var acc = s0
//        var i = size - 1
//        while (i >= 0) {
//            acc = f(this[i], acc)
//            i--
//        }
//        return acc
//    }
//
//    /**
//     * Folds from the left, stopping early if [pred] is no longer fulfilled.
//     */
//    fun <S> foldWhile(s0: S, pred: (S) -> Boolean, f: (S, A) -> S): S {
//        var acc = s0
//        var i = 0
//        while (i < size && pred(acc)) {
//            acc = f(acc, this[i])
//            i++
//        }
//        return acc
//    }
//
//    /**
//     * Partitions elements into two chunks using [transform].
//     * If [transform] returns `Left`, the element goes to the first chunk,
//     * if `Right`, it goes to the second chunk.
//     */
//    fun <X, Y> partitionMap(transform: (A) -> Either<X, Y>): Pair<Chunk<X>, Chunk<Y>> {
//        val leftBuilder = ChunkBuilder<X>(this.size)
//        val rightBuilder = ChunkBuilder<Y>(this.size)
//        for (i in 0 until size) {
//            when (val e = transform(this[i])) {
//                is Left  -> leftBuilder += e.value
//                is Right -> rightBuilder += e.value
//            }
//        }
//        return leftBuilder.result() to rightBuilder.result()
//    }
//
//    /**
//     * Maps elements, returning a new chunk.
//     */
//    fun <B> map(transform: (A) -> B): Chunk<B> {
//        val sz = size
//        if (sz == 0) return Empty
//        // If this is a specialized chunk, we can optimize. We'll unify here.
//        val builder = ChunkBuilder<B>(sz)
//        for (i in 0 until sz) {
//            builder += transform(this[i])
//        }
//        return builder.result()
//    }
//
//    /**
//     * Slices out elements from [from] until [until], returning a new chunk.
//     */
//    fun slice(from: Int, until: Int): Chunk<A> {
//        val start = from.coerceAtLeast(0).coerceAtMost(size)
//        val end = until.coerceAtLeast(start).coerceAtMost(size)
//        val length = end - start
//        return when {
//            length <= 0 -> Empty
//            length == size && start == 0 -> this
//            else -> Slice(this, start, length)
//        }
//    }
//
//    /**
//     * Splits this chunk into two at [n].
//     */
//    fun splitAt(n: Int): Pair<Chunk<A>, Chunk<A>> =
//        take(n) to drop(n)
//
//    /**
//     * Splits on the first element matching [predicate].
//     */
//    fun splitWhere(predicate: (A) -> Boolean): Pair<Chunk<A>, Chunk<A>> {
//        val idx = indexWhere(predicate)
//        return if (idx < 0) this to Empty
//        else take(idx) to drop(idx)
//    }
//
//    /**
//     * Converts this chunk to a plain Kotlin [Array]. May cause copying.
//     */
//    abstract fun toArray(): kotlin.Array<@UnsafeVariance A>
//
//    /**
//     * Converts this chunk to a [List].
//     */
//    fun toList(): List<A> {
//        val arr = toArray()
//        return arr.toList()
//    }
//
//    /**
//     * Converts this chunk to a [String], if [A] is a character-like type.
//     * Behavior is undefined for non-char types.
//     */
//    open fun toStringChunk(): String {
//        // If this is a chunk of Char, we can build a string
//        // or fallback if not char.
//        val sb = StringBuilder(size)
//        for (i in 0 until size) {
//            sb.append(this[i].toString())
//        }
//        return sb.toString()
//    }
//
//    /**
//     * Renders the chunk as a string, e.g. Chunk(1,2,3).
//     */
//    override fun toString(): String {
//        if (isEmpty()) return "Chunk()"
//        val sb = StringBuilder("Chunk(")
//        for (i in 0 until size) {
//            sb.append(this[i])
//            if (i < size - 1) sb.append(", ")
//        }
//        sb.append(")")
//        return sb.toString()
//    }
//
//    /**
//     * Returns the current chunk re-packed into a new chunk with array-backing,
//     * potentially improving performance for certain bulk operations.
//     */
//    open fun materialize(): Chunk<A> {
//        if (this is Empty) return this
//        // By default, copy to a generic ArrayChunk
//        return ArrayChunk(toArray())
//    }
//
//    /**
//     * Helper for rebalancing deeply nested [Concat].
//     */
//    internal open fun rebalance(): Chunk<A> = this
//
//    /**
//     * Provide a chunk-specific [ChunkIterator], specialized for performance.
//     */
//    abstract fun chunkIterator(): ChunkIterator<@UnsafeVariance A>
//
//    /**
//     * A sealed subclass representing a guaranteed non-empty [Chunk].
//     */
//    sealed class NonEmpty<A> : Chunk<A>()
//
//    /**
//     * Represents an empty chunk with no elements.
//     */
//    data object Empty : Chunk<Nothing>() {
//        override val size: Int = 0
//        override val depth: Int = 0
//        override fun get(index: Int): Nothing =
//            throw IndexOutOfBoundsException("Empty chunk access at $index")
//
//        override fun toArray(): kotlin.Array<Nothing> = emptyArray()
//        override fun chunkIterator(): ChunkIterator<Nothing> = ChunkIterator.Empty
//    }
//
//    // region: Basic Non-Empty Classes
//
//    /**
//     * A chunk storing a generic array of type [A].
//     */
//    class ArrayChunk<A>(
//        val array: kotlin.Array<A>
//    ) : NonEmpty<A>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): A {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("ArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<A> = array.copyOf()
//
//        override fun chunkIterator(): ChunkIterator<A> =
//            ArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is ArrayChunk<*>) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    /**
//     * A chunk storing a Kotlin [ByteArray].
//     */
//    class ByteArrayChunk(
//        val array: kotlin.ByteArray
//    ) : NonEmpty<Byte>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Byte {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("ByteArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Byte> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Byte> =
//            ByteArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is ByteArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class BooleanArrayChunk(
//        val array: kotlin.BooleanArray
//    ) : NonEmpty<Boolean>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Boolean {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("BooleanArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Boolean> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Boolean> =
//            BooleanArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is BooleanArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class IntArrayChunk(
//        val array: kotlin.IntArray
//    ) : NonEmpty<Int>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Int {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("IntArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Int> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Int> =
//            IntArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is IntArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class ShortArrayChunk(
//        val array: kotlin.ShortArray
//    ) : NonEmpty<Short>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Short {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("ShortArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Short> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Short> =
//            ShortArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is ShortArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class LongArrayChunk(
//        val array: kotlin.LongArray
//    ) : NonEmpty<Long>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Long {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("LongArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Long> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Long> =
//            LongArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is LongArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class FloatArrayChunk(
//        val array: kotlin.FloatArray
//    ) : NonEmpty<Float>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Float {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("FloatArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Float> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Float> =
//            FloatArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is FloatArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    class DoubleArrayChunk(
//        val array: kotlin.DoubleArray
//    ) : NonEmpty<Double>() {
//
//        override val size: Int get() = array.size
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Double {
//            if (index < 0 || index >= array.size) {
//                throw IndexOutOfBoundsException("DoubleArrayChunk index=$index size=${array.size}")
//            }
//            return array[index]
//        }
//
//        override fun toArray(): kotlin.Array<Double> =
//            Array(size) { i -> array[i] }
//
//        override fun chunkIterator(): ChunkIterator<Double> =
//            DoubleArrayChunkIterator(array)
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is DoubleArrayChunk) return false
//            return array.contentEquals(other.array)
//        }
//
//        override fun hashCode(): Int {
//            return array.contentHashCode()
//        }
//    }
//
//    /**
//     * A chunk storing a [kotlin.String] as a sequence of characters.
//     */
//    class StringChunk(
//        val string: kotlin.String
//    ) : NonEmpty<Char>() {
//
//        override val size: Int get() = string.length
//        override val depth: Int get() = 1
//
//        override fun get(index: Int): Char {
//            if (index < 0 || index >= string.length) {
//                throw IndexOutOfBoundsException("StringChunk index=$index size=${string.length}")
//            }
//            return string[index]
//        }
//
//        override fun toArray(): kotlin.Array<Char> =
//            Array(size) { i -> string[i] }
//
//        override fun chunkIterator(): ChunkIterator<Char> =
//            StringChunkIterator(string)
//
//        override fun toStringChunk(): String = string
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is StringChunk) return false
//            return string == other.string
//        }
//
//        override fun hashCode(): Int {
//            return string.hashCode()
//        }
//    }
//
//    /**
//     * A chunk storing exactly one element.
//     */
//    class Single<A>(val value: A) : NonEmpty<A>() {
//        override val size: Int = 1
//        override val depth: Int = 1
//
//        override fun get(index: Int): A {
//            if (index != 0) throw IndexOutOfBoundsException("Single chunk access at $index")
//            return value
//        }
//
//        override fun toArray(): kotlin.Array<A> = ArrayUtil.genericArrayOf(value)
//
//        override fun chunkIterator(): ChunkIterator<A> =
//            object : ChunkIterator<A> {
//                override val length: Int get() = 1
//                override fun hasNextAt(index: Int): Boolean = (index == 0)
//                override fun nextAt(index: Int): A {
//                    if (index != 0) throw IndexOutOfBoundsException("Single chunk nextAt $index")
//                    return value
//                }
//                override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
//                    return if (offset <= 0 && length > 0) this else Empty
//                }
//            }
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is Single<*>) return false
//            return value == other.value
//        }
//
//        override fun hashCode(): Int = value?.hashCode() ?: 0
//    }
//
//    /**
//     * A chunk formed by concatenating multiple sub-chunks in a balanced way.
//     * This is used for performance, rather than naive linking or copying.
//     */
//    class Concat<A>(
//        val chunks: kotlin.Array<Chunk<A>>
//    ) : NonEmpty<A>() {
//
//        override val depth: Int by lazy {
//            1 + (chunks.maxOfOrNull { it.depth } ?: 0)
//        }
//
//        override val size: Int by lazy {
//            chunks.sumOf { it.size }
//        }
//
//        override val concatDepth: Int by lazy {
//            1 + (chunks.maxOfOrNull { it.concatDepth } ?: 0)
//        }
//
//        override fun get(index: Int): A {
//            var idx = index
//            for (c in chunks) {
//                if (idx < c.size) return c[idx]
//                idx -= c.size
//            }
//            throw IndexOutOfBoundsException("Concat index=$index totalSize=$size")
//        }
//
//        override fun toArray(): kotlin.Array<A> {
//            val resultSize = size
//            val result = arrayOfNulls<Any?>(resultSize) as kotlin.Array<A>
//            var pos = 0
//            for (ch in chunks) {
//                val arr = ch.toArray()
//                arr.copyInto(result, pos)
//                pos += arr.size
//            }
//            return result
//        }
//
//        override fun rebalance(): Chunk<A> {
//            // Simplified rebalancing approach:
//            val flattened = ArrayList<Chunk<A>>(chunks.size)
//            for (c in chunks) {
//                when (c) {
//                    is Concat -> flattened.addAll(c.chunks)
//                    else      -> flattened.add(c)
//                }
//            }
//            return if (flattened.size == 1) flattened[0]
//            else Concat(flattened.toTypedArray())
//        }
//
//        override fun chunkIterator(): ChunkIterator<A> {
//            val its = chunks.map { it.chunkIterator() }
//            return ConcatChunkIterator(its.toTypedArray())
//        }
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is Concat<*>) return false
//            if (this.size != other.size) return false
//            // compare elementwise
//            for (i in 0 until this.size) {
//                if (this[i] != other[i]) return false
//            }
//            return true
//        }
//
//        override fun hashCode(): Int {
//            var result = 1
//            for (i in 0 until size) {
//                result = 31 * result + (this[i]?.hashCode() ?: 0)
//            }
//            return result
//        }
//    }
//
//    /**
//     * A slice (subrange) of another chunk, to avoid copying.
//     */
//    class Slice<A>(
//        val chunk: Chunk<A>,
//        val offset: Int,
//        val lengthSlice: Int
//    ) : NonEmpty<A>() {
//
//        override val size: Int get() = lengthSlice
//        override val depth: Int get() = chunk.depth + 1
//
//        override fun get(index: Int): A {
//            if (index < 0 || index >= lengthSlice) {
//                throw IndexOutOfBoundsException("Slice index=$index offset=$offset length=$lengthSlice")
//            }
//            return chunk[offset + index]
//        }
//
//        override fun toArray(): kotlin.Array<A> {
//            val arr = arrayOfNulls<Any?>(lengthSlice) as kotlin.Array<A>
//            for (i in 0 until lengthSlice) {
//                arr[i] = chunk[offset + i]
//            }
//            return arr
//        }
//
//        override fun chunkIterator(): ChunkIterator<A> {
//            return object : ChunkIterator<A> {
//                override val length: Int get() = lengthSlice
//
//                override fun hasNextAt(index: Int): Boolean = index < lengthSlice
//                override fun nextAt(index: Int): A {
//                    if (index < 0 || index >= lengthSlice)
//                        throw IndexOutOfBoundsException("Slice iterator $index")
//                    return chunk[offset + index]
//                }
//
//                override fun sliceIterator(o: Int, l: Int): ChunkIterator<A> {
//                    val start = o.coerceAtLeast(0).coerceAtMost(lengthSlice)
//                    val end = (o + l).coerceAtLeast(start).coerceAtMost(lengthSlice)
//                    return Slice(this@Slice, start, end - start).chunkIterator()
//                }
//            }
//        }
//    }
//
//    /**
//     * Internal node used to efficiently append elements. We use atomic
//     * counters to track usage.
//     */
//    class AppendN<A>(
//        private val start: Chunk<A>,
//        private val buffer: kotlin.Array<Any?>,
//        private var bufferUsed: Int,
//        private val chain: kotlinx.atomicfu.AtomicInt = atomic(bufferUsed)
//    ) : NonEmpty<A>() {
//
//        override val depth: Int get() = start.depth + 1
//        override val size: Int get() = start.size + bufferUsed
//
//        override fun get(index: Int): A {
//            val stSize = start.size
//            return when {
//                index < stSize -> start[index]
//                index - stSize < bufferUsed -> buffer[index - stSize] as A
//                else -> throw IndexOutOfBoundsException("AppendN index=$index size=$size")
//            }
//        }
//
//        override fun toArray(): kotlin.Array<A> {
//            val result = arrayOfNulls<Any?>(size) as kotlin.Array<A>
//            val sArray = start.toArray()
//            sArray.copyInto(result, 0)
//            for (i in 0 until bufferUsed) {
//                result[sArray.size + i] = buffer[i] as A
//            }
//            return result
//        }
//
//        fun append(element: A): AppendN<A> {
//            // Attempt fast in-place append if buffer has space and usage not outgrown.
//            val oldUsed = chain.value
//            if (oldUsed < buffer.size && chain.compareAndSet(oldUsed, oldUsed + 1)) {
//                buffer[oldUsed] = element
//                bufferUsed = oldUsed + 1
//                return this
//            }
//            // Otherwise, finalize the current buffer into a chunk, then create a new AppendN.
//            val chunked = ArrayChunk(arrayOfNulls<Any?>(oldUsed).mapIndexed { i, _ ->
//                buffer[i] as A
//            }.toTypedArray())
//            val newStart = start + chunked
//            val newBuffer = arrayOfNulls<Any?>(64)
//            newBuffer[0] = element
//            return AppendN(newStart, newBuffer, 1, atomic(1))
//        }
//
//        override fun chunkIterator(): ChunkIterator<A> {
//            // Combine iterators from start and the buffer region.
//            val it1 = start.chunkIterator()
//            val it2 = object : ChunkIterator<A> {
//                override val length: Int = bufferUsed
//                override fun hasNextAt(index: Int): Boolean = index < bufferUsed
//                override fun nextAt(index: Int): A {
//                    if (index < 0 || index >= bufferUsed)
//                        throw IndexOutOfBoundsException("AppendN buffer idx=$index used=$bufferUsed")
//                    return buffer[index] as A
//                }
//                override fun sliceIterator(o: Int, l: Int): ChunkIterator<A> {
//                    val s = o.coerceAtLeast(0).coerceAtMost(bufferUsed)
//                    val e = (o + l).coerceAtLeast(s).coerceAtMost(bufferUsed)
//                    return object : ChunkIterator<A> {
//                        override val length: Int = e - s
//                        override fun hasNextAt(index: Int): Boolean = index < (e - s)
//                        override fun nextAt(index: Int): A {
//                            if (index < 0 || index >= (e - s))
//                                throw IndexOutOfBoundsException("AppendN sliceIter $index")
//                            return buffer[s + index] as A
//                        }
//                        override fun sliceIterator(o: Int, l: Int) = this // not super relevant
//                    }
//                }
//            }
//            return ConcatChunkIterator(arrayOf(it1, it2))
//        }
//    }
//
//    /**
//     * Internal node used to efficiently prepend elements.
//     * Similar logic to [AppendN], just reversed.
//     */
//    class PrependN<A>(
//        private val end: Chunk<A>,
//        private val buffer: kotlin.Array<Any?>,
//        private var bufferUsed: Int,
//        private val chain: kotlinx.atomicfu.AtomicInt = atomic(bufferUsed)
//    ) : NonEmpty<A>() {
//
//        override val depth: Int get() = end.depth + 1
//        override val size: Int get() = end.size + bufferUsed
//
//        override fun get(index: Int): A {
//            return when {
//                index < bufferUsed -> buffer[buffer.size - bufferUsed + index] as A
//                index < size       -> end[index - bufferUsed]
//                else -> throw IndexOutOfBoundsException("PrependN index=$index size=$size")
//            }
//        }
//
//        override fun toArray(): kotlin.Array<A> {
//            val result = arrayOfNulls<Any?>(size) as kotlin.Array<A>
//            // copy buffer first
//            val startIdx = buffer.size - bufferUsed
//            for (i in 0 until bufferUsed) {
//                result[i] = buffer[startIdx + i] as A
//            }
//            // copy end
//            val eArr = end.toArray()
//            eArr.copyInto(result, bufferUsed)
//            return result
//        }
//
//        fun prepend(element: A): PrependN<A> {
//            val oldUsed = chain.value
//            if (oldUsed < buffer.size && chain.compareAndSet(oldUsed, oldUsed + 1)) {
//                buffer[buffer.size - oldUsed - 1] = element
//                bufferUsed = oldUsed + 1
//                return this
//            }
//            val chunked = ArrayChunk(arrayOfNulls<Any?>(oldUsed).mapIndexed { i, _ ->
//                buffer[buffer.size - oldUsed + i] as A
//            }.toTypedArray())
//            val newEnd = chunked + end
//            val newBuffer = arrayOfNulls<Any?>(64)
//            newBuffer[63] = element
//            return PrependN(newEnd, newBuffer, 1, atomic(1))
//        }
//
//        override fun chunkIterator(): ChunkIterator<A> {
//            // buffer region + end
//            val it1 = object : ChunkIterator<A> {
//                override val length: Int = bufferUsed
//                override fun hasNextAt(index: Int): Boolean = index < bufferUsed
//                override fun nextAt(index: Int): A {
//                    if (index < 0 || index >= bufferUsed)
//                        throw IndexOutOfBoundsException("PrependN buffer idx=$index used=$bufferUsed")
//                    val startIdx = buffer.size - bufferUsed
//                    return buffer[startIdx + index] as A
//                }
//                override fun sliceIterator(o: Int, l: Int): ChunkIterator<A> {
//                    val s = o.coerceAtLeast(0).coerceAtMost(bufferUsed)
//                    val e = (o + l).coerceAtLeast(s).coerceAtMost(bufferUsed)
//                    return object : ChunkIterator<A> {
//                        override val length: Int = e - s
//                        override fun hasNextAt(index: Int): Boolean = index < (e - s)
//                        override fun nextAt(index: Int): A {
//                            if (index < 0 || index >= (e - s))
//                                throw IndexOutOfBoundsException("PrependN sliceIter $index")
//                            val st = buffer.size - bufferUsed + s
//                            return buffer[st + index] as A
//                        }
//                        override fun sliceIterator(o: Int, l: Int) = this
//                    }
//                }
//            }
//            return ConcatChunkIterator(arrayOf(it1, end.chunkIterator()))
//        }
//    }
//
//    /**
//     * Internal node used to efficiently update an element at an index.
//     */
//    class Update<A>(
//        private val chunk: Chunk<A>,
//        private val bufferIndices: IntArray,
//        private val bufferValues: kotlin.Array<Any?>,
//        private var used: Int,
//        private val chain: kotlinx.atomicfu.AtomicInt = atomic(used)
//    ) : NonEmpty<A>() {
//
//        override val depth: Int get() = chunk.depth + 1
//        override val size: Int get() = chunk.size
//
//        override fun get(index: Int): A {
//            var i = used - 1
//            while (i >= 0) {
//                if (bufferIndices[i] == index) {
//                    return bufferValues[i] as A
//                }
//                i--
//            }
//            return chunk[index]
//        }
//
//        override fun toArray(): kotlin.Array<A> {
//            val baseArr = chunk.toArray()
//            // apply overrides
//            for (i in 0 until used) {
//                val idx = bufferIndices[i]
//                baseArr[idx] = bufferValues[i] as A
//            }
//            return baseArr
//        }
//
//        fun update(index: Int, value: A): Update<A> {
//            if (index < 0 || index >= size) {
//                throw IndexOutOfBoundsException("Update index=$index size=$size")
//            }
//            val oldUsed = chain.value
//            if (oldUsed < bufferIndices.size && chain.compareAndSet(oldUsed, oldUsed + 1)) {
//                bufferIndices[oldUsed] = index
//                bufferValues[oldUsed] = value
//                used = oldUsed + 1
//                return this
//            }
//            // Otherwise, finalize the current updates, materialize and create new
//            val materialized = this.toArray()
//            val newBase = ArrayChunk(materialized)
//            val newIndices = IntArray(256)
//            val newValues = arrayOfNulls<Any?>(256)
//            newIndices[0] = index
//            newValues[0] = value
//            return Update(newBase, newIndices, newValues, 1, atomic(1))
//        }
//
//        override fun chunkIterator(): ChunkIterator<A> {
//            // We'll just return an iterator over a newly computed array for simplicity.
//            val fullArr = toArray()
//            return ArrayChunkIterator(fullArr)
//        }
//    }
//
//    // endregion Basic Non-Empty classes
//
//    // region: BitChunk classes (for Boolean => bits)
//
//    /**
//     * A specialized chunk for representing a sequence of booleans packed into
//     * type [T]. For example, [BitChunkByte], [BitChunkInt], [BitChunkLong].
//     */
//    sealed class BitChunk<T>(
//        val bitsPerElement: Int
//    ) : NonEmpty<Boolean>() {
//        protected abstract fun bitElementCount(): Int
//        protected abstract fun bitElementAt(index: Int): T
//        protected abstract fun bitNewChunk(
//            newMin: Int,
//            newMax: Int
//        ): BitChunk<T>
//    }
//
//    // Example: Byte-based bit chunk
//    class BitChunkByte(
//        private val bytes: Chunk<Byte>,
//        private val minBitIndex: Int,
//        private val maxBitIndex: Int
//    ) : BitChunk<Byte>(8) {
//        override val size: Int get() = maxBitIndex - minBitIndex
//        override val depth: Int get() = bytes.depth + 1
//
//        override fun get(index: Int): Boolean {
//            if (index < 0 || index >= size) {
//                throw IndexOutOfBoundsException("BitChunkByte index=$index size=$size")
//            }
//            val global = minBitIndex + index
//            val byteIdx = global shr 3
//            val bit = 7 - (global and 7)
//            val b = bytes[byteIdx]
//            return (b.toInt() and (1 shl bit)) != 0
//        }
//
//        override fun toArray(): kotlin.Array<Boolean> {
//            val arr = arrayOfNulls<Boolean>(size)
//            for (i in 0 until size) {
//                arr[i] = get(i)
//            }
//            return arr as kotlin.Array<Boolean>
//        }
//
//        override fun chunkIterator(): ChunkIterator<Boolean> =
//            object : ChunkIterator<Boolean> {
//                override val length: Int get() = size
//                override fun hasNextAt(index: Int): Boolean = index < size
//                override fun nextAt(index: Int): Boolean = get(index)
//                override fun sliceIterator(o: Int, l: Int): ChunkIterator<Boolean> {
//                    val s = o.coerceAtLeast(0).coerceAtMost(size)
//                    val e = (o + l).coerceAtLeast(s).coerceAtMost(size)
//                    return BitChunkByte(bytes, minBitIndex + s, minBitIndex + e).chunkIterator()
//                }
//            }
//
//        override fun bitElementCount(): Int = bytes.size
//        override fun bitElementAt(index: Int): Byte = bytes[index]
//        override fun bitNewChunk(newMin: Int, newMax: Int): BitChunkByte =
//            BitChunkByte(bytes, newMin, newMax)
//    }
//
//    companion object {
//
//        /**
//         * Constructs an empty [Chunk].
//         */
//        fun <A> empty(): Chunk<A> = Empty
//
//        /**
//         * Constructs a [Chunk] from a vararg of elements.
//         */
//        fun <A> chunkOf(vararg elements: A): Chunk<A> {
//            return when (elements.size) {
//                0 -> Empty
//                1 -> Single(elements[0])
//                else -> ArrayChunk(elements.copyOf())
//            }
//        }
//
//        /**
//         * Constructs a [Chunk] from a generic [Array].
//         */
//        fun <A> fromArray(array: kotlin.Array<A>): Chunk<A> {
//            return when (array.size) {
//                0 -> Empty
//                1 -> Single(array[0])
//                else -> ArrayChunk(array)
//            }
//        }
//
//        fun fromByteArray(array: kotlin.ByteArray): Chunk<Byte> {
//            return if (array.isEmpty()) Empty else ByteArrayChunk(array)
//        }
//
//        fun fromBooleanArray(array: kotlin.BooleanArray): Chunk<Boolean> {
//            return if (array.isEmpty()) Empty else BooleanArrayChunk(array)
//        }
//
//        fun fromIntArray(array: kotlin.IntArray): Chunk<Int> {
//            return if (array.isEmpty()) Empty else IntArrayChunk(array)
//        }
//
//        fun fromShortArray(array: kotlin.ShortArray): Chunk<Short> {
//            return if (array.isEmpty()) Empty else ShortArrayChunk(array)
//        }
//
//        fun fromLongArray(array: kotlin.LongArray): Chunk<Long> {
//            return if (array.isEmpty()) Empty else LongArrayChunk(array)
//        }
//
//        fun fromFloatArray(array: kotlin.FloatArray): Chunk<Float> {
//            return if (array.isEmpty()) Empty else FloatArrayChunk(array)
//        }
//
//        fun fromDoubleArray(array: kotlin.DoubleArray): Chunk<Double> {
//            return if (array.isEmpty()) Empty else DoubleArrayChunk(array)
//        }
//
//        fun fromString(str: kotlin.String): Chunk<Char> {
//            return if (str.isEmpty()) Empty else StringChunk(str)
//        }
//
//        /**
//         * Constructs a single-element [Chunk].
//         */
//        fun <A> single(value: A): Chunk<A> = Single(value)
//
//        /**
//         * Repeatedly applies [generate] starting from [seed], collecting elements
//         * until [generate] returns null. This is akin to Scala's unfold.
//         */
//        fun <S, A> unfold(seed: S, generate: (S) -> Pair<A, S>?): Chunk<A> {
//            val builder = ChunkBuilder<A>()
//            var s = seed
//            while (true) {
//                val next = generate(s) ?: break
//                builder += next.first
//                s = next.second
//            }
//            return builder.result()
//        }
//
//        /**
//         * Balances large differences in chunk depths.
//         */
//        private fun <A> balanceConcat(left: Chunk<A>, right: Chunk<A>): Chunk<A> {
//            // Mimic the Scala logic:
//            // If the difference in depth is large, we attempt to rebalance the tree
//            // by associating smaller subtrees carefully.
//            // For brevity, we do a simplified approach:
//            return Concat(arrayOf(left, right)).rebalance()
//        }
//    }
//}
//
///**
// * Constructs an immutable [Chunk] from the specified elements.
// */
//fun <A> chunkOf(vararg elements: A): Chunk<A> = Chunk.chunkOf(*elements)
//
///**
// * A specialized builder for [Chunk]. For performance reasons, it can hold
// * elements in a [MutableList] or a specialized structure, then produce
// * a chunk efficiently via [result].
// */
//class ChunkBuilder<A>(initialCapacity: Int = 16) {
//    private val buf = ArrayList<A>(initialCapacity)
//
//    operator fun plusAssign(value: A) {
//        buf.add(value)
//    }
//
//    fun add(value: A) {
//        buf.add(value)
//    }
//
//    fun size(): Int = buf.size
//
//    fun result(): Chunk<A> {
//        return when (buf.size) {
//            0 -> Chunk.Empty
//            1 -> Chunk.Single(buf[0])
//            else -> Chunk.ArrayChunk(buf.toArrayOf())
//        }
//    }
//}
//
//private fun <T> List<T>.toArrayOf(): kotlin.Array<T> {
//    @Suppress("UNCHECKED_CAST")
//    val arr = arrayOfNulls<Any>(this.size) as kotlin.Array<T>
//    for (i in this.indices) {
//        arr[i] = this[i]
//    }
//    return arr
//}
//
//// region: ChunkIterator
///**
// * A specialized iterator for [Chunk], allowing the caller to manage the
// * current index for performance. This replicates the design in Scala code
// * for chunk iteration.
// */
//interface ChunkIterator<out A> {
//    val length: Int
//    fun hasNextAt(index: Int): Boolean
//    fun nextAt(index: Int): A
//    fun sliceIterator(offset: Int, length: Int): ChunkIterator<A>
//}
//
//object ChunkIterator {
//    val Empty: ChunkIterator<Nothing> = object : ChunkIterator<Nothing> {
//        override val length: Int = 0
//        override fun hasNextAt(index: Int): Boolean = false
//        override fun nextAt(index: Int): Nothing =
//            throw IndexOutOfBoundsException("Empty ChunkIterator nextAt($index)")
//        override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Nothing> = this
//    }
//}
//
//class ArrayChunkIterator<A>(
//    private val array: kotlin.Array<A>
//) : ChunkIterator<A> {
//    override val length: Int get() = array.size
//
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): A {
//        if (index < 0 || index >= array.size) {
//            throw IndexOutOfBoundsException("ArrayChunkIterator nextAt($index)")
//        }
//        return array[index]
//    }
//
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        val newArray = array.copyOfRange(start, end)
//        return ArrayChunkIterator(newArray)
//    }
//}
//
//class ByteArrayChunkIterator(
//    private val array: kotlin.ByteArray
//) : ChunkIterator<Byte> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Byte {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("ByteArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Byte> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return ByteArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class BooleanArrayChunkIterator(
//    private val array: kotlin.BooleanArray
//) : ChunkIterator<Boolean> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Boolean {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("BooleanArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Boolean> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return BooleanArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class IntArrayChunkIterator(
//    private val array: kotlin.IntArray
//) : ChunkIterator<Int> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Int {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("IntArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Int> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return IntArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class ShortArrayChunkIterator(
//    private val array: kotlin.ShortArray
//) : ChunkIterator<Short> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Short {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("ShortArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Short> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return ShortArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class LongArrayChunkIterator(
//    private val array: kotlin.LongArray
//) : ChunkIterator<Long> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Long {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("LongArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Long> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return LongArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class FloatArrayChunkIterator(
//    private val array: kotlin.FloatArray
//) : ChunkIterator<Float> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Float {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("FloatArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Float> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return FloatArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class DoubleArrayChunkIterator(
//    private val array: kotlin.DoubleArray
//) : ChunkIterator<Double> {
//    override val length: Int get() = array.size
//    override fun hasNextAt(index: Int): Boolean = index < array.size
//    override fun nextAt(index: Int): Double {
//        if (index < 0 || index >= array.size)
//            throw IndexOutOfBoundsException("DoubleArrayChunkIterator nextAt($index)")
//        return array[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Double> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(array.size)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(array.size)
//        return DoubleArrayChunkIterator(array.copyOfRange(start, end))
//    }
//}
//
//class StringChunkIterator(
//    private val string: kotlin.String
//) : ChunkIterator<Char> {
//    override val length: Int get() = string.length
//    override fun hasNextAt(index: Int): Boolean = index < string.length
//    override fun nextAt(index: Int): Char {
//        if (index < 0 || index >= string.length)
//            throw IndexOutOfBoundsException("StringChunkIterator nextAt($index)")
//        return string[index]
//    }
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<Char> {
//        val start = offset.coerceAtLeast(0).coerceAtMost(string.length)
//        val end = (offset + length).coerceAtLeast(start).coerceAtMost(string.length)
//        val sub = string.substring(start, end)
//        return StringChunkIterator(sub)
//    }
//}
//
//class ConcatChunkIterator<A>(
//    private val iterators: kotlin.Array<ChunkIterator<A>>
//) : ChunkIterator<A> {
//    private val totalLength by lazy { iterators.sumOf { it.length.toLong() }.toInt() }
//
//    override val length: Int get() = totalLength
//
//    override fun hasNextAt(index: Int): Boolean = index < totalLength
//
//    override fun nextAt(index: Int): A {
//        var idx = index
//        for (it in iterators) {
//            if (idx < it.length) return it.nextAt(idx)
//            idx -= it.length
//        }
//        throw IndexOutOfBoundsException("ConcatChunkIterator nextAt($index) totalLength=$length")
//    }
//
//    override fun sliceIterator(offset: Int, length: Int): ChunkIterator<A> {
//        // Simple approach: materialize the slice as an array chunk, then return array chunk iterator
//        val builder = ChunkBuilder<A>(length)
//        for (i in offset until offset + length) {
//            if (i >= totalLength) break
//            builder += nextAt(i)
//        }
//        val arr = builder.result().toArray()
//        return ArrayChunkIterator(arr)
//    }
//}
//// endregion