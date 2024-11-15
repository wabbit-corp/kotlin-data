package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.StringBuilder

@Serializable(with = ConsList.TypeSerializer::class)
sealed class ConsList<out V> : List<V> {
    abstract fun <Z> cata(nil: Z, cons: (V, Z) -> Z): Z

    fun cons(value: @UnsafeVariance V): Cons<V> =
        Cons(value, this)

    fun head(): V = when (this) {
        is Nil -> throw NoSuchElementException("head of empty list")
        is Cons -> head
    }

    fun headOrNull(): V? = when (this) {
        is Nil -> null
        is Cons -> head
    }

    ///////////////////////////////////////////////////////////////////////////
    // Conversion
    ///////////////////////////////////////////////////////////////////////////

    fun toTypedArray(): Array<@UnsafeVariance V> {
        val size = size
        val array = arrayOfNulls<Any?>(size) as Array<V>
        var i = 0
        var tail = this
        while (tail is Cons) {
            array[i++] = tail.head
            tail = tail.tail
        }
        return array
    }

    fun toList(): List<V> {
        val list = ArrayList<V>(size)
        var tail = this
        while (tail is Cons) {
            list.add(tail.head)
            tail = tail.tail
        }
        return list
    }

    fun toLazy(): LazyConsList<V> = when (this) {
        is Nil -> LazyConsList.Nil
        is Cons -> LazyConsList.Delay(Need.apply { LazyConsList.Cons(head, tail.toLazy()) })
    }

    fun reverse(): ConsList<V> {
        var tail = this
        var result: ConsList<V> = Nil
        while (tail is Cons) {
            result = Cons(tail.head, result)
            tail = tail.tail
        }
        return result
    }

    fun reverseLazy(): LazyConsList<V> {
        // rev' :: StrictList a -> [a]
        // rev' = go []
        //   where
        //     go :: [a] -> StrictList a -> [a]
        //     go acc SNil         = acc
        //     go acc (SCons x xs) = go (x:acc) xs

        var result: LazyConsList<V> = LazyConsList.Nil
        var tail = this
        while (tail is Cons) {
            result = LazyConsList.Cons(tail.head, result)
            tail = tail.tail
        }
        return result
    }

    ///////////////////////////////////////////////////////////////////////////
    // Folds
    ///////////////////////////////////////////////////////////////////////////

    fun <Z> foldLeft(z: Z, s: (Z, V) -> Z): Z {
        var tail = this
        var result = z
        while (tail is Cons) {
            result = s(result, tail.head)
            tail = tail.tail
        }
        return result
    }

    fun <Z> foldRight(z: Z, s: (V, Z) -> Z): Z {
        val arr = this.toTypedArray()
        var result = z
        for (i in arr.size - 1 downTo 0) {
            result = s(arr[i], result)
        }
        return result
    }

    fun <Z> foldRightLazy(z: Need<Z>, s: (V, Need<Z>) -> Need<Z>): Need<Z> =
        when (this) {
            is Nil -> z
            is Cons -> s(head, tail.foldRightLazy(z, s))
        }

    ///////////////////////////////////////////////////////////////////////////
    // Operators
    ///////////////////////////////////////////////////////////////////////////

    fun filter(f: (V) -> Boolean): ConsList<V> {
        var tail = this
        var result: ConsList<V> = Nil
        while (tail is Cons) {
            if (f(tail.head)) {
                result = Cons(tail.head, result)
            }
            tail = tail.tail
        }
        return result.reverse()
    }

    inline fun any(f: (V) -> Boolean): Boolean {
        var tail = this
        while (tail is Cons) {
            if (f(tail.head)) {
                return true
            }
            tail = tail.tail
        }
        return false
    }

    operator fun plus(other: ConsList<@UnsafeVariance V>): ConsList<V> =
        this.foldRight(other) { v, acc -> acc.cons(v) }

    override fun get(index: Int): V {
        if (index < 0) throw IndexOutOfBoundsException("index: $index")
        if (index >= size) throw IndexOutOfBoundsException("index: $index, size: $size")

        var i = 0
        var current = this
        while (true) {
            if (current !is Cons<V>)
                throw IndexOutOfBoundsException("index: $index, size: $size")
            if (i == index) return current.head
            current = current.tail
            i += 1
        }
    }

    fun update(index: Int, element: @UnsafeVariance V): ConsList<V> {
        if (index < 0) throw IndexOutOfBoundsException("index: $index")
        if (index >= size) throw IndexOutOfBoundsException("index: $index, size: $size")

        fun go(list: ConsList<V>, i: Int): ConsList<V> = when (list) {
            is Nil -> throw IndexOutOfBoundsException("index: $index, size: $size")
            is Cons -> if (i == index) Cons(element, list.tail) else Cons(list.head, go(list.tail, i + 1))
        }

        return go(this, 0)
    }

    override fun contains(element: @UnsafeVariance V): Boolean {
        var current = this
        while (true) {
            if (current !is Cons<V>) return false
            if (element == current.head) return true
            current = current.tail
        }
    }

    override fun containsAll(elements: Collection<@UnsafeVariance V>): Boolean {
        val set = elements.toMutableSet()
        var current = this
        while (true) {
            if (current !is Cons<V>) return set.isEmpty()
            set.remove(current.head)
            current = current.tail
        }
    }

    override fun indexOf(element: @UnsafeVariance V): Int {
        var i = 0
        var current = this
        while (true) {
            if (current !is Cons<V>) return -1
            if (element == current.head) return i
            current = current.tail
            i += 1
        }
    }

    override fun isEmpty(): Boolean = this is Nil

    override fun iterator(): Iterator<V> {
        val list = this
        return object : Iterator<V> {
            var current: ConsList<@UnsafeVariance V> = list

            override fun hasNext(): Boolean = current !is Nil

            override fun next(): V {
                val c = current
                if (c !is Cons)
                    throw IndexOutOfBoundsException()
                val result = c.head
                current = c.tail
                return result
            }
        }
    }

    override fun lastIndexOf(element: @UnsafeVariance V): Int {
        var i = 0
        var last = -1
        var current = this
        while (true) {
            if (current !is Cons<V>) return last
            if (element == current.head) {
                last = i
            }
            current = current.tail
            i += 1
        }
    }

    override fun listIterator(): ListIterator<V> =
        toList().listIterator()
    override fun listIterator(index: Int): ListIterator<V> =
        toList().listIterator(index)

    // FIXME: This can be implemented more efficiently.
    override fun subList(fromIndex: Int, toIndex: Int): List<V> =
        toList().subList(fromIndex, toIndex)

    object Nil : ConsList<Nothing>() {
        override val size = 0

        override fun <Z> cata(nil: Z, cons: (Nothing, Z) -> Z): Z = nil

        override fun toString(): String = "ConsList()"
    }

    data class Cons<out A>(val head: A, val tail: ConsList<A>) : ConsList<A>() {
        override val size = tail.size + 1

        override fun <Z> cata(nil: Z, cons: (A, Z) -> Z): Z = cons(head, tail.cata(nil, cons))

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("ConsList(")
            var current: ConsList<A> = this
            while (true) {
                if (current !is Cons<A>) {
                    sb.append(")")
                    break
                }

                sb.append(current.head)
                if (current.tail !is Nil)
                    sb.append(", ")

                current = current.tail
            }
            return sb.toString()
        }
    }

    class TypeSerializer<A>(val valueSerializer: KSerializer<A>) : KSerializer<ConsList<A>> {
        override val descriptor = ListSerializer(valueSerializer).descriptor

        override fun serialize(encoder: Encoder, value: ConsList<A>) {
            encoder.encodeSerializableValue(ListSerializer(valueSerializer), value.toList())
        }

        override fun deserialize(decoder: Decoder): ConsList<A> {
            return consListFrom(decoder.decodeSerializableValue(ListSerializer(valueSerializer)))
        }
    }
}

fun <V> emptyConsList(): ConsList<V> = ConsList.Nil
fun <V> consListOf(): ConsList<V> = ConsList.Nil
fun <V> consListOf(vararg list: V): ConsList<V> {
    var result: ConsList<V> = ConsList.Nil
    for (i in list.size-1 downTo 0) {
        result = ConsList.Cons(list[i], result)
    }
    return result
}
fun <V> consListFrom(list: List<V>): ConsList<V> {
    var result: ConsList<V> = ConsList.Nil
    for (i in list.size-1 downTo 0) {
        result = ConsList.Cons(list[i], result)
    }
    return result
}
