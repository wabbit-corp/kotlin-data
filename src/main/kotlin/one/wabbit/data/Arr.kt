@file:Suppress("ReplaceRangeToWithRangeUntil", "OVERRIDE_BY_INLINE")

package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// : List<T>, Cloneable, java.io.Serializable, RandomAccess
@Serializable(with=Arr.TypeSerializer::class)
data class Arr<T>(@JvmField val unsafe: Array<Any?>)  {
    inline val size: Int
        get() = unsafe.size

    inline fun isEmpty(): Boolean = unsafe.isEmpty()
    inline fun isNotEmpty(): Boolean = !unsafe.isEmpty()

    inline fun first(): T = unsafe[0] as T
    inline fun last(): T = unsafe[unsafe.size - 1] as T

    inline fun <U> map(f: (T) -> U): Arr<U> {
        val unsafe = unsafe
        val size = unsafe.size
        val newArr = arrayOfNulls<Any?>(size)
        for (i in 0..size-1) {
            newArr[i] = f(unsafe[i] as T)
        }
        return Arr(newArr)
    }

    inline fun <U : Any> mapOrNull(f: (T) -> U?): Arr<U>? {
        val unsafe = unsafe
        val size = unsafe.size
        val newArr = arrayOfNulls<Any?>(size)
        for (i in 0..size-1) {
            val r = f(unsafe[i] as T)
            if (r == null) return null
            newArr[i] = r
        }
        return Arr(newArr)
    }

    inline fun all(predicate: (T) -> Boolean): Boolean {
        val unsafe = unsafe
        val size = unsafe.size
        for (i in 0..size-1) {
            if (!predicate(unsafe[i] as T)) {
                return false
            }
        }
        return true
    }

    inline fun any(predicate: (T) -> Boolean): Boolean {
        val unsafe = unsafe
        val size = unsafe.size
        for (i in 0..size-1) {
            if (predicate(unsafe[i] as T)) {
                return true
            }
        }
        return false
    }

    inline fun count(predicate: (T) -> Boolean): Int {
        val unsafe = unsafe
        val size = unsafe.size
        var count = 0
        for (i in 0..size-1) {
            if (predicate(unsafe[i] as T)) {
                count++
            }
        }
        return count
    }

    inline operator fun get(index: Int): T = unsafe[index] as T

    val indices: IntRange
        get() = unsafe.indices

    class ArrIterator(private val unsafe: Array<Any?>) : Iterator<Any?> {
        private var index = 0
        override fun hasNext(): Boolean = index < unsafe.size
        override fun next(): Any? = unsafe[index++]
    }

    operator fun iterator(): Iterator<T> = ArrIterator(unsafe) as Iterator<T>

    fun toList(): List<T> = unsafe.toList() as List<T>

//    override fun containsAll(elements: Collection<T>): Boolean =
//        elements.all { contains(it) }
//
//    override fun contains(element: T): Boolean =
//        unsafe.contains(element)
//
//    @Suppress("UNCHECKED_CAST")
//    override operator fun get(index: Int): T =
//        unsafe[index] as T
//
//    override fun isEmpty(): Boolean =
//        unsafe.isEmpty()
//
//    @Suppress("UNCHECKED_CAST")
//    override fun iterator(): Iterator<T> =
//        unsafe.iterator() as Iterator<T>
//
//    override fun listIterator(): ListIterator<T> =
//        listIterator(0)
//
//    override fun listIterator(index: Int): ListIterator<T> {
//        val initialIndex = index
//        return object : ListIterator<T> {
//            private var index = initialIndex
//            override fun hasNext(): Boolean = this.index < size
//            override fun next(): T = get(this.index++)
//            override fun hasPrevious(): Boolean = this.index > 0
//            override fun previous(): T = get(--this.index)
//            override fun nextIndex(): Int = this.index
//            override fun previousIndex(): Int = this.index - 1
//        }
//    }
//
//    override fun subList(fromIndex: Int, toIndex: Int): List<T> =
//        Arr(unsafe.copyOfRange(fromIndex, toIndex))
//
//    override fun lastIndexOf(element: T): Int =
//        unsafe.lastIndexOf(element)
//
//    override fun indexOf(element: T): Int =
//        unsafe.indexOf(element)

    fun update(index: Int, value: Any?): Arr<T> =
        Arr(unsafe.clone().apply { this[index] = value })

    operator fun plus(other: Arr<T>): Arr<T> {
        val newArr = arrayOfNulls<Any?>(unsafe.size + other.unsafe.size)
        System.arraycopy(unsafe, 0, newArr, 0, unsafe.size)
        System.arraycopy(other.unsafe, 0, newArr, unsafe.size, other.unsafe.size)
        return Arr(newArr)
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
        val size = unsafe.size
        for (i in 0..size -1) {
            result = result * 31 + unsafe[i].hashCode()
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
            if (hashCode() != other.hashCode()) return false
            for (i in 0..size -1) {
                if (unsafe[i] === otherUnsafe[i]) continue
                if (unsafe[i] != otherUnsafe[i]) return false
            }
            return true
        } else if (other is List<*>) {
            if (size != other.size) return false
            for (i in 0..size -1) {
                if (unsafe[i] != other[i]) return false
            }
            return true
        } else {
            return false
        }
    }

    override fun toString(): String =
        "Arr(${unsafe.joinToString(", ")})"

//    override fun clone(): Arr<T> =
//        Arr(unsafe)

    class TypeSerializer<A>(val valueSerializer: KSerializer<A>) : KSerializer<Arr<A>> {
        override val descriptor = ListSerializer(valueSerializer).descriptor
        override fun serialize(encoder: Encoder, value: Arr<A>) {
            encoder.encodeSerializableValue(ListSerializer(valueSerializer), value.toList())
        }
        override fun deserialize(decoder: Decoder): Arr<A> {
            return Arr(decoder.decodeSerializableValue(ListSerializer(valueSerializer)).toTypedArray())
        }
    }

    companion object {
        private val EMPTY = Arr<Nothing>(emptyArray<Any?>())
        fun <T> empty(): Arr<T> = EMPTY as Arr<T>

        fun <T> of(t: T): Arr<T> = Arr(arrayOf(t))
        fun <T> of(t1: T, t2: T): Arr<T> = Arr(arrayOf(t1, t2))
        fun <T> of(t1: T, t2: T, t3: T): Arr<T> = Arr(arrayOf(t1, t2, t3))
        @Suppress("UNCHECKED_CAST")
        fun <T> of(vararg ts: T): Arr<T> = Arr(ts as Array<Any?>)

        fun <T> fromList(list: List<T>): Arr<T> =
            Arr(list.toTypedArray<Any?>())
    }
}


fun arrOf(): Arr<Nothing> = Arr.empty()
fun <T> arrOf(t: T): Arr<T> = Arr.of(t)
fun <T> arrOf(t1: T, t2: T): Arr<T> = Arr.of(t1, t2)
fun <T> arrOf(t1: T, t2: T, t3: T): Arr<T> = Arr.of(t1, t2, t3)
fun <T> arrOf(vararg ts: T): Arr<T> = Arr.of(*ts)
