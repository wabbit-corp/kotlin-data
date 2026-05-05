@file:Suppress("ReplaceRangeToWithRangeUntil", "OVERRIDE_BY_INLINE")
@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Compact immutable array-backed sequence.
 *
 * This type owns its storage. The public constructor and [fromArray] always copy the caller's
 * array, so later external mutation cannot affect the resulting [Arr]. Internal update operations
 * return new [Arr] values and leave earlier instances unchanged.
 *
 * Complexity notes:
 * - indexed reads, [first], [last], [firstOrNull], and [lastOrNull] are O(1)
 * - [contains], [indexOf], and [lastIndexOf] are O(n)
 * - [map], [update], [plus], and [subList] allocate and copy, so they are O(n)
 *
 * Exception contracts:
 * - [first] and [last] throw [NoSuchElementException] on an empty array
 * - indexed operations such as [get], [update], [listIterator], and [subList] throw
 *   [IndexOutOfBoundsException] for out-of-range indices
 *
 * Negative indexing is never supported.
 */
@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
@Serializable(with = Arr.TypeSerializer::class)
class Arr<out T> private constructor(unsafe: Array<Any?>, @Suppress("UNUSED_PARAMETER") owned: UnsafeOwnership) :
    Iterable<T> {
    constructor(unsafe: Array<Any?>) : this(unsafe.copyOf(), UnsafeOwnership)

    private val unsafe: Array<Any?> = unsafe

    private fun requireElement(): Unit =
        if (unsafe.isEmpty()) {
            throw NoSuchElementException("Arr is empty")
        } else {
            Unit
        }

    private fun requireIndex(index: Int): Int {
        if (index !in unsafe.indices) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size ${unsafe.size}")
        }
        return index
    }

    /**
     * Number of elements in this array.
     */
    val size: Int
        get() = unsafe.size

    /**
     * Return whether this array has no elements.
     */
    fun isEmpty(): Boolean = unsafe.isEmpty()

    /**
     * Return whether this array has at least one element.
     */
    fun isNotEmpty(): Boolean = !unsafe.isEmpty()

    /**
     * Return the first element.
     *
     * @throws NoSuchElementException when this array is empty.
     */
    fun first(): T {
        requireElement()
        return unsafe[0] as T
    }

    /**
     * Return the first element, or null when this array is empty.
     */
    fun firstOrNull(): T? = if (unsafe.isEmpty()) null else unsafe[0] as T

    /**
     * Return the last element.
     *
     * @throws NoSuchElementException when this array is empty.
     */
    fun last(): T {
        requireElement()
        return unsafe[unsafe.size - 1] as T
    }

    /**
     * Return the last element, or null when this array is empty.
     */
    fun lastOrNull(): T? = if (unsafe.isEmpty()) null else unsafe[unsafe.size - 1] as T

    /**
     * Transform every element with [f].
     */
    fun <U> map(f: (T) -> U): Arr<U> {
        val unsafe = unsafe
        val size = unsafe.size
        val newArr = arrayOfNulls<Any?>(size)
        for (i in 0..size - 1) {
            newArr[i] = f(unsafe[i] as T)
        }
        return unsafeWrapOwned(newArr)
    }

    /**
     * Transform every element with [f], or return null if any transform returns null.
     */
    fun <U : Any> mapOrNull(f: (T) -> U?): Arr<U>? {
        val unsafe = unsafe
        val size = unsafe.size
        val newArr = arrayOfNulls<Any?>(size)
        for (i in 0..size - 1) {
            val r = f(unsafe[i] as T)
            if (r == null) return null
            newArr[i] = r
        }
        return unsafeWrapOwned(newArr)
    }

    /**
     * Return whether every element satisfies [predicate].
     */
    fun all(predicate: (T) -> Boolean): Boolean {
        val unsafe = unsafe
        val size = unsafe.size
        for (i in 0..size - 1) {
            if (!predicate(unsafe[i] as T)) {
                return false
            }
        }
        return true
    }

    /**
     * Return whether at least one element satisfies [predicate].
     */
    fun any(predicate: (T) -> Boolean): Boolean {
        val unsafe = unsafe
        val size = unsafe.size
        for (i in 0..size - 1) {
            if (predicate(unsafe[i] as T)) {
                return true
            }
        }
        return false
    }

    /**
     * Count elements that satisfy [predicate].
     */
    fun count(predicate: (T) -> Boolean): Int {
        val unsafe = unsafe
        val size = unsafe.size
        var count = 0
        for (i in 0..size - 1) {
            if (predicate(unsafe[i] as T)) {
                count++
            }
        }
        return count
    }

    /**
     * Return the element at [index].
     *
     * Negative indices are not supported.
     */
    operator fun get(index: Int): T = unsafe[requireIndex(index)] as T

    /**
     * Valid element index range.
     */
    val indices: IntRange
        get() = unsafe.indices

    /**
     * Iterator over raw [Arr] storage.
     */
    class ArrIterator(private val unsafe: Array<Any?>) : Iterator<Any?> {
        private var index = 0

        override fun hasNext(): Boolean = index < unsafe.size

        override fun next(): Any? {
            if (!hasNext()) throw NoSuchElementException()
            return unsafe[index++]
        }
    }

    override operator fun iterator(): Iterator<T> = ArrIterator(unsafe) as Iterator<T>

    /**
     * Materialize this array as a Kotlin [List].
     */
    fun toList(): List<T> = unsafe.toList() as List<T>

    /**
     * Return whether [element] is present.
     */
    fun contains(element: @UnsafeVariance T): Boolean = indexOf(element) >= 0

    /**
     * Return whether every value in [elements] is present.
     */
    fun containsAll(elements: Collection<@UnsafeVariance T>): Boolean = elements.all(::contains)

    /**
     * Return a bidirectional iterator starting at index `0`.
     */
    fun listIterator(): ListIterator<T> = listIterator(0)

    /**
     * Returns a bidirectional iterator starting at [index].
     *
     * Valid start positions are in `0..size`, inclusive of `size`.
     */
    fun listIterator(index: Int): ListIterator<T> {
        if (index !in 0..size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        }
        return object : ListIterator<T> {
            private var position = index

            override fun hasNext(): Boolean = position < size

            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                return get(position++)
            }

            override fun hasPrevious(): Boolean = position > 0

            override fun previous(): T {
                if (!hasPrevious()) throw NoSuchElementException()
                return get(--position)
            }

            override fun nextIndex(): Int = position

            override fun previousIndex(): Int = position - 1
        }
    }

    /**
     * Returns a copied slice in the half-open range `[fromIndex, toIndex)`.
     *
     * Unlike [Chunk.slice], this does not clamp. Both bounds must already lie in `0..size` and
     * satisfy `fromIndex <= toIndex`.
     */
    fun subList(fromIndex: Int, toIndex: Int): Arr<T> {
        if (fromIndex !in 0..size) {
            throw IndexOutOfBoundsException("fromIndex $fromIndex out of bounds for size $size")
        }
        if (toIndex !in fromIndex..size) {
            throw IndexOutOfBoundsException("toIndex $toIndex out of bounds for size $size")
        }
        return unsafeWrapOwned(unsafe.copyOfRange(fromIndex, toIndex))
    }

    /**
     * Return the last index containing [element], or `-1` when absent.
     */
    fun lastIndexOf(element: @UnsafeVariance T): Int {
        for (index in unsafe.lastIndex downTo 0) {
            if (unsafe[index] == element) {
                return index
            }
        }
        return -1
    }

    /**
     * Return the first index containing [element], or `-1` when absent.
     */
    fun indexOf(element: @UnsafeVariance T): Int {
        for (index in unsafe.indices) {
            if (unsafe[index] == element) {
                return index
            }
        }
        return -1
    }

    /**
     * Return a copy with [value] stored at [index].
     */
    fun update(index: Int, value: @UnsafeVariance T): Arr<T> {
        val idx = requireIndex(index)
        return unsafeWrapOwned(unsafe.copyOf().apply { this[idx] = value })
    }

    /**
     * Concatenate this array with [other].
     */
    operator fun plus(other: Arr<@UnsafeVariance T>): Arr<T> {
        val newArr = arrayOfNulls<Any?>(unsafe.size + other.unsafe.size)
        unsafe.copyInto(newArr, endIndex = unsafe.size)
        other.unsafe.copyInto(newArr, destinationOffset = unsafe.size, endIndex = other.unsafe.size)
        return unsafeWrapOwned(newArr)
    }

    private var _hashCode: Long = UNCACHED_HASH

    override fun hashCode(): Int {
        val h = _hashCode
        if (h != UNCACHED_HASH) {
            return h.toInt()
        }
        val result = hashCodeImpl()
        _hashCode = result.toLong()
        return result
    }

    private fun hashCodeImpl(): Int {
        var result = 1
        val size = unsafe.size
        for (i in 0..size - 1) {
            result = result * 31 + (unsafe[i]?.hashCode() ?: 0)
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val unsafe = this.unsafe
        val size = unsafe.size
        if (other is Arr<*>) {
            val otherUnsafe = other.unsafe
            if (size != otherUnsafe.size) return false
            val thisHash = _hashCode
            val otherHash = other._hashCode
            if (thisHash != UNCACHED_HASH && otherHash != UNCACHED_HASH && thisHash != otherHash) return false
            for (i in 0..size - 1) {
                if (unsafe[i] === otherUnsafe[i]) continue
                if (unsafe[i] != otherUnsafe[i]) return false
            }
            return true
        } else {
            return false
        }
    }

    override fun toString(): String = "Arr(${unsafe.joinToString(", ")})"

    /**
     * Serializer that encodes arrays as lists.
     */
    class TypeSerializer<A>(val valueSerializer: KSerializer<A>) : KSerializer<Arr<A>> {
        override val descriptor = ListSerializer(valueSerializer).descriptor

        override fun serialize(encoder: Encoder, value: Arr<A>) {
            encoder.encodeSerializableValue(ListSerializer(valueSerializer), value.toList())
        }

        override fun deserialize(decoder: Decoder): Arr<A> =
            fromList(decoder.decodeSerializableValue(ListSerializer(valueSerializer)))
    }

    /**
     * Factories for immutable arrays.
     */
    companion object {
        private object UnsafeOwnership
        private const val UNCACHED_HASH: Long = 0x100000000L

        @InternalDataApi
        internal fun <T> unsafeWrapOwned(unsafe: Array<Any?>): Arr<T> = Arr(unsafe, UnsafeOwnership)

        private val EMPTY = unsafeWrapOwned<Nothing>(emptyArray<Any?>())

        /**
         * Return an empty array.
         */
        fun <T> empty(): Arr<T> = EMPTY as Arr<T>

        /**
         * Create an array with one element.
         */
        fun <T> of(t: T): Arr<T> = unsafeWrapOwned(arrayOf(t))

        /**
         * Create an array with two elements.
         */
        fun <T> of(t1: T, t2: T): Arr<T> = unsafeWrapOwned(arrayOf(t1, t2))

        /**
         * Create an array with three elements.
         */
        fun <T> of(t1: T, t2: T, t3: T): Arr<T> = unsafeWrapOwned(arrayOf(t1, t2, t3))

        /**
         * Create an array from [ts].
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> of(vararg ts: T): Arr<T> = unsafeWrapOwned(ts as Array<Any?>)

        /**
         * Creates an immutable array value by copying [array].
         */
        fun <T> fromArray(array: Array<out T>): Arr<T> {
            @Suppress("UNCHECKED_CAST")
            return unsafeWrapOwned(array.copyOf() as Array<Any?>)
        }

        /**
         * Create an immutable array by copying [list].
         */
        fun <T> fromList(list: List<T>): Arr<T> {
            val owned = arrayOfNulls<Any?>(list.size)
            for (i in list.indices) {
                owned[i] = list[i]
            }
            return unsafeWrapOwned(owned)
        }
    }
}

/**
 * Return an empty [Arr].
 */
fun arrOf(): Arr<Nothing> = Arr.empty()

/**
 * Return an [Arr] with one element.
 */
fun <T> arrOf(t: T): Arr<T> = Arr.of(t)

/**
 * Return an [Arr] with two elements.
 */
fun <T> arrOf(t1: T, t2: T): Arr<T> = Arr.of(t1, t2)

/**
 * Return an [Arr] with three elements.
 */
fun <T> arrOf(t1: T, t2: T, t3: T): Arr<T> = Arr.of(t1, t2, t3)

/**
 * Return an [Arr] containing [ts].
 */
fun <T> arrOf(vararg ts: T): Arr<T> = Arr.of(*ts)
