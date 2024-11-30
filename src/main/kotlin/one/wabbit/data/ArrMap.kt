package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with=ArrMap.TypeSerializer::class)
class ArrMap<K : Any, V>(
    @JvmField val unsafe: Array<Any?>,
    @JvmField val hashes: IntArray
) {
    init {
        require(unsafe.size % 2 == 0) { "Expected even number of elements, got ${unsafe.size}" }
        require(unsafe.size / 2 == hashes.size) { "Expected hashes size to be half of unsafe size" }
    }

    val size: Int
        inline get() = hashes.size

    inline fun isEmpty(): Boolean = unsafe.isEmpty()
    inline fun isNotEmpty(): Boolean = !unsafe.isEmpty()

    inline fun first(): Pair<K, V> =
        Pair(unsafe[0] as K, unsafe[1] as V)
    inline fun last(): Pair<K, V> =
        Pair(unsafe[unsafe.size - 2] as K, unsafe[unsafe.size - 1] as V)

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

    fun put(key: K, value: V): ArrMap<K, V> {
        val unsafe = unsafe
        val size = hashes.size
        val keyHash = key.hashCode()
        if (size == 0) {
            return ArrMap(arrayOf(key, value), intArrayOf(keyHash))
        }

        var i = 0
        while (i < size) {
            val itemKey = unsafe[2 * i]
            if (hashes[i] == keyHash && itemKey == key) {
                val newArr = unsafe.copyOf()
                newArr[2 * i + 1] = value
                return ArrMap(newArr, hashes)
            }
            i += 1
        }

        val newArr = arrayOfNulls<Any?>(2 * size + 2)
        System.arraycopy(unsafe, 0, newArr, 0, 2 * size)
        newArr[2 * size] = key
        newArr[2 * size + 1] = value
        val newHashes = IntArray(size + 1)
        System.arraycopy(hashes, 0, newHashes, 0, size)
        newHashes[size] = keyHash
        return ArrMap(newArr, newHashes)
    }

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

    fun toMap(): Map<K, V> = toMutableMap()

//    fun remove(key: K): ArrMap<K, V> {
//        val unsafe = unsafe
//        val size = unsafe.size
//        if (size == 0) {
//            return this
//        }
//        var i = 0
//        while (i < size) {
//            if (unsafe[i] == key) {
//                val newArr = arrayOfNulls<Any?>(size - 2)
//                System.arraycopy(unsafe, 0, newArr, 0, i)
//                System.arraycopy(unsafe, i + 2, newArr, i, size - i - 2)
//                return ArrMap(newArr)
//            }
//            i += 2
//        }
//        return this
//    }
//
//    fun clear(): ArrMap<K, V> = ArrMap(emptyArray())
//
//    fun keys(): Arr<K> {
//        val unsafe = unsafe
//        val size = unsafe.size
//        val result = arrayOfNulls<Any?>(size / 2)
//        var i = 0
//        var j = 0
//        while (i < size) {
//            result[j] = unsafe[i]
//            i += 2
//            j += 1
//        }
//        return Arr(result)
//    }
//
//    fun values(): Arr<V> {
//        val unsafe = unsafe
//        val size = unsafe.size
//        val result = arrayOfNulls<Any?>(size / 2)
//        var i = 1
//        var j = 0
//        while (i < size) {
//            result[j] = unsafe[i]
//            i += 2
//            j += 1
//        }
//        return Arr(result)
//    }

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

    private var _hashCode: Long = 0x100000000L
    override fun hashCode(): Int {
        val h = _hashCode
        if (h != 0x100000000L) {
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
            result += hashes[i] xor unsafe[2 * i + 1].hashCode()
            i += 1
        }
        return result
    }

    override fun equals(that: Any?): Boolean {
        if (this === that) return true
        if (that is ArrMap<*, *>) {
            val thisHashes = hashes
            val thatHashes = that.hashes
            val thisSize = hashes.size
            val thatSize = that.hashes.size
            if (thisSize != thatSize) return false
            if (hashCode() != that.hashCode()) return false
            // TODO: Optimize this.
            val thisUnsafe = unsafe
            val thatUnsafe = that.unsafe
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

    class TypeSerializer<K : Any, V>(val keySerializer: KSerializer<K>, val valueSerializer: KSerializer<V>) : KSerializer<ArrMap<K, V>> {
        private val mapSerializer = MapSerializer(keySerializer, valueSerializer)
        override val descriptor = mapSerializer.descriptor
        override fun serialize(encoder: Encoder, value: ArrMap<K, V>) {
            mapSerializer.serialize(encoder, value.toMap())
        }

        override fun deserialize(decoder: Decoder): ArrMap<K, V> {
            return ArrMap.from<K, V>(mapSerializer.deserialize(decoder))
        }
    }

    companion object {
        private val EMPTY = ArrMap<Nothing, Nothing>(emptyArray(), intArrayOf())
        fun <K : Any, V> empty(): ArrMap<K, V> = EMPTY as ArrMap<K, V>

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
            return ArrMap(unsafe, hashes)
        }
    }
}

fun <K : Any, V> arrMapOf(): ArrMap<K, V> = ArrMap.empty()
