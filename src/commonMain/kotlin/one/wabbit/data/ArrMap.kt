@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Compact immutable map backed by flat arrays.
 *
 * This type is intentionally a tiny immutable map.
 *
 * Implementation notes:
 * - iteration order is insertion order
 * - caller-owned input is copied on construction, so instances never alias external mutable arrays
 * - `get`, `contains`, and key replacement use linear scans over the flat arrays
 * - `put` copies the backing arrays and is therefore O(n)
 * - `remove`, `keys`, and `values` also copy and are therefore O(n)
 * - `equals` is order-independent map equality and therefore O(n^2) in the worst case
 *
 * Exception contracts:
 * - [first] and [last] throw [NoSuchElementException] on an empty map
 * - key lookup methods never throw for missing keys; [get] returns `null` and [contains] returns
 *   `false`
 *
 * That tradeoff is intentional: for very small maps the flat representation is compact and
 * cache-friendly, and it avoids the object overhead of a general-purpose hash map.
 *
 * Prefer a regular hash map once instances routinely grow past [RECOMMENDED_MAX_SIZE] entries.
 */
@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
@Serializable(with = ArrMap.TypeSerializer::class)
class ArrMap<K : Any, V> private constructor(
    unsafe: Array<Any?>,
    hashes: IntArray,
    @Suppress("UNUSED_PARAMETER") owned: UnsafeOwnership,
) : Iterable<Pair<K, V>> {
    private val unsafe: Array<Any?> = unsafe
    private val hashes: IntArray = hashes

    init {
        require(unsafe.size % 2 == 0) { "Expected even number of elements, got ${unsafe.size}" }
        require(unsafe.size / 2 == hashes.size) { "Expected hashes size to be half of unsafe size" }
    }

    /**
     * Number of key/value entries.
     */
    val size: Int
        get() = hashes.size

    private fun requireEntry(): Unit =
        if (unsafe.isEmpty()) {
            throw NoSuchElementException("ArrMap is empty")
        } else {
            Unit
        }

    /**
     * Return whether this map has no entries.
     */
    fun isEmpty(): Boolean = unsafe.isEmpty()

    /**
     * Return whether this map has at least one entry.
     */
    fun isNotEmpty(): Boolean = !unsafe.isEmpty()

    /**
     * Return the first entry in insertion order.
     */
    fun first(): Pair<K, V> {
        requireEntry()
        return Pair(unsafe[0] as K, unsafe[1] as V)
    }

    /**
     * Return the last entry in insertion order.
     */
    fun last(): Pair<K, V> {
        requireEntry()
        return Pair(unsafe[unsafe.size - 2] as K, unsafe[unsafe.size - 1] as V)
    }

    /**
     * Return the value for [key], or null when absent.
     */
    operator fun get(key: K): V? {
        val unsafe = unsafe
        val hashes = hashes
        val size = hashes.size

        if (size == 0) return null

        val keyHash = key.hashCode()
        var i = 0
        while (i < size) {
            if (hashes[i] != keyHash) {
                i += 1
                continue
            }
            if (unsafe[2 * i] === key) {
                return unsafe[2 * i + 1] as V
            }
            if (key.equals(unsafe[2 * i])) {
                return unsafe[2 * i + 1] as V
            }
            i += 1
        }
        return null
    }

    /**
     * Return whether [key] is present.
     */
    operator fun contains(key: K): Boolean {
        val unsafe = unsafe
        val size = unsafe.size / 2
        val keyHash = key.hashCode()
        var i = 0
        while (i < size) {
            if (hashes[i] == keyHash && unsafe[2 * i] == key) {
                return true
            }
            i += 1
        }
        return false
    }

    /**
     * Return a map with [key] associated with [value].
     */
    fun put(key: K, value: V): ArrMap<K, V> {
        val unsafe = unsafe
        val size = hashes.size
        val keyHash = key.hashCode()
        if (size == 0) {
            return unsafeWrapOwned(arrayOf(key, value), intArrayOf(keyHash))
        }

        var i = 0
        while (i < size) {
            val itemKey = unsafe[2 * i]
            if (hashes[i] == keyHash && itemKey == key) {
                val newArr = unsafe.copyOf()
                newArr[2 * i + 1] = value
                return unsafeWrapOwned(newArr, hashes)
            }
            i += 1
        }

        val newArr = arrayOfNulls<Any?>(2 * size + 2)
        unsafe.copyInto(newArr, endIndex = 2 * size)
        newArr[2 * size] = key
        newArr[2 * size + 1] = value
        val newHashes = IntArray(size + 1)
        hashes.copyInto(newHashes, endIndex = size)
        newHashes[size] = keyHash
        return unsafeWrapOwned(newArr, newHashes)
    }

    /**
     * Materialize this map as a mutable Kotlin map.
     */
    fun toMutableMap(): MutableMap<K, V> {
        val unsafe = unsafe
        val size = unsafe.size
        val result = mutableMapOf<K, V>()
        var i = 0
        while (i < size) {
            result[unsafe[i] as K] = unsafe[i + 1] as V
            i += 2
        }
        return result
    }

    /**
     * Materialize entries in insertion order.
     */
    fun toList(): List<Pair<K, V>> {
        val result = ArrayList<Pair<K, V>>(size)
        for (index in hashes.indices) {
            result += (unsafe[2 * index] as K) to (unsafe[2 * index + 1] as V)
        }
        return result
    }

    /**
     * Materialize this map as a read-only Kotlin map.
     */
    fun toMap(): Map<K, V> = toMutableMap()

    /**
     * Return a map without [key].
     */
    fun remove(key: K): ArrMap<K, V> {
        val unsafe = unsafe
        val hashes = hashes
        val size = hashes.size
        if (size == 0) {
            return this
        }

        val keyHash = key.hashCode()
        var i = 0
        while (i < size) {
            if (hashes[i] == keyHash && unsafe[2 * i] == key) {
                if (size == 1) {
                    return empty()
                }
                val newUnsafe = arrayOfNulls<Any?>(unsafe.size - 2)
                unsafe.copyInto(newUnsafe, endIndex = 2 * i)
                unsafe.copyInto(newUnsafe, destinationOffset = 2 * i, startIndex = 2 * i + 2)
                val newHashes = IntArray(size - 1)
                hashes.copyInto(newHashes, endIndex = i)
                hashes.copyInto(newHashes, destinationOffset = i, startIndex = i + 1)
                return unsafeWrapOwned(newUnsafe, newHashes)
            }
            i += 1
        }
        return this
    }

    /**
     * Return an empty map.
     */
    fun clear(): ArrMap<K, V> = empty()

    /**
     * Return keys in insertion order.
     */
    fun keys(): Arr<K> {
        val result = arrayOfNulls<Any?>(size)
        for (index in hashes.indices) {
            result[index] = unsafe[2 * index]
        }
        return Arr.unsafeWrapOwned(result)
    }

    /**
     * Return values in insertion order.
     */
    fun values(): Arr<V> {
        val result = arrayOfNulls<Any?>(size)
        for (index in hashes.indices) {
            result[index] = unsafe[2 * index + 1]
        }
        return Arr.unsafeWrapOwned(result)
    }

    /**
     * Return entries in insertion order.
     */
    fun entries(): Arr<Pair<K, V>> {
        val result = arrayOfNulls<Any?>(size)
        for (index in hashes.indices) {
            result[index] = (unsafe[2 * index] as K) to (unsafe[2 * index + 1] as V)
        }
        return Arr.unsafeWrapOwned(result)
    }

    override operator fun iterator(): Iterator<Pair<K, V>> =
        object : Iterator<Pair<K, V>> {
            private var index = 0

            override fun hasNext(): Boolean = index < hashes.size

            override fun next(): Pair<K, V> {
                if (!hasNext()) throw NoSuchElementException()
                val current = index++
                return (unsafe[2 * current] as K) to (unsafe[2 * current + 1] as V)
            }
        }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("ArrMap(")
        val unsafe = unsafe
        val size = unsafe.size
        var i = 0
        while (i < size) {
            sb.append(unsafe[i])
            sb.append(" -> ")
            sb.append(unsafe[i + 1])
            if (i + 2 < size) {
                sb.append(", ")
            }
            i += 2
        }
        sb.append(")")
        return sb.toString()
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
        val size = hashes.size
        var i = 0
        while (i < size) {
            result += hashes[i] xor (unsafe[2 * i + 1]?.hashCode() ?: 0)
            i += 1
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ArrMap<*, *>) {
            val thisHashes = hashes
            val thatHashes = other.hashes
            val thisSize = hashes.size
            val thatSize = other.hashes.size
            if (thisSize != thatSize) return false
            val thisHash = _hashCode
            val otherHash = other._hashCode
            if (thisHash != UNCACHED_HASH && otherHash != UNCACHED_HASH && thisHash != otherHash) return false
            // Intentionally scan: ArrMap is a tiny-map structure optimized for very small sizes.
            val thisUnsafe = unsafe
            val thatUnsafe = other.unsafe
            var i = 0
            while (i < thisSize) {
                var j = 0
                while (j < thatSize) {
                    if (thisHashes[i] != thatHashes[j]) {
                        j += 1
                        continue
                    }
                    if (thisUnsafe[2 * i] != thatUnsafe[2 * j]) {
                        j += 1
                        continue
                    }
                    if (thisUnsafe[2 * i + 1] != thatUnsafe[2 * j + 1]) {
                        j += 1
                        continue
                    }
                    break
                }
                if (j == thatSize) {
                    return false
                }
                i += 1
            }
            return true
        }
        return false
    }

    /**
     * Serializer that encodes [ArrMap] as a Kotlin map.
     */
    class TypeSerializer<K : Any, V>(
        val keySerializer: KSerializer<K>,
        val valueSerializer: KSerializer<V>,
    ) : KSerializer<ArrMap<K, V>> {
        private val mapSerializer = MapSerializer(keySerializer, valueSerializer)
        override val descriptor = mapSerializer.descriptor

        override fun serialize(encoder: Encoder, value: ArrMap<K, V>) {
            mapSerializer.serialize(encoder, value.toMap())
        }

        override fun deserialize(decoder: Decoder): ArrMap<K, V> =
            ArrMap.from<K, V>(mapSerializer.deserialize(decoder))
    }

    /**
     * Factories for [ArrMap].
     */
    companion object {
        private object UnsafeOwnership
        private const val UNCACHED_HASH: Long = 0x100000000L

        /**
         * Suggested maximum size before a general-purpose hash map is usually a better fit.
         */
        const val RECOMMENDED_MAX_SIZE: Int = 16

        @InternalDataApi
        internal fun <K : Any, V> unsafeWrapOwned(unsafe: Array<Any?>, hashes: IntArray): ArrMap<K, V> =
            ArrMap(unsafe, hashes, UnsafeOwnership)

        private val EMPTY = unsafeWrapOwned<Nothing, Nothing>(emptyArray(), intArrayOf())

        /**
         * Return an empty map.
         */
        fun <K : Any, V> empty(): ArrMap<K, V> = EMPTY as ArrMap<K, V>

        /**
         * Build an [ArrMap] by copying [map] entries in iteration order.
         */
        fun <K : Any, V> from(map: Map<K, V>): ArrMap<K, V> {
            val size = map.size
            if (size == 0) {
                return empty<K, V>()
            }
            val unsafe = arrayOfNulls<Any?>(2 * size)
            val hashes = IntArray(size)
            var i = 0
            for ((key, value) in map) {
                unsafe[2 * i] = key
                unsafe[2 * i + 1] = value
                hashes[i] = key.hashCode()
                i += 1
            }
            return unsafeWrapOwned(unsafe, hashes)
        }
    }
}

/**
 * Return an empty [ArrMap].
 */
fun <K : Any, V> arrMapOf(): ArrMap<K, V> = ArrMap.empty()
