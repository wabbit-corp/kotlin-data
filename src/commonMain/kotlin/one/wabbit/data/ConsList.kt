// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent singly linked list.
 *
 * This type implements [List] and is optimized for cheap [cons] prepend operations. Indexed access
 * is linear.
 */
@Serializable(with = ConsList.TypeSerializer::class)
sealed class ConsList<out V> : List<V> {
    /** Number of elements in this list. */
    abstract override val size: Int

    /** Catamorphism over the empty and non-empty cases. */
    abstract fun <Z> cata(nil: Z, cons: (V, Z) -> Z): Z

    private fun requirePosition(index: Int): Int {
        if (index !in 0..size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
        return index
    }

    private fun requireRange(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex > size) {
            throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
        }
        if (fromIndex > toIndex) {
            throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
        }
    }

    private fun dropElements(count: Int): ConsList<V> {
        var current: ConsList<V> = this
        repeat(count) { current = (current as Cons).tail }
        return current
    }

    /** Return a new list with [value] prepended. */
    fun cons(value: @UnsafeVariance V): Cons<V> = Cons(value, this)

    /** Return the first element, or throw [NoSuchElementException] when empty. */
    fun head(): V =
        when (this) {
            is Nil -> throw NoSuchElementException("head of empty list")
            is Cons -> head
        }

    /** Return the first element, or null when empty. */
    fun headOrNull(): V? =
        when (this) {
            is Nil -> null
            is Cons -> head
        }

    // /////////////////////////////////////////////////////////////////////////
    // Conversion
    // /////////////////////////////////////////////////////////////////////////

    /** Materialize this list into a typed array. */
    fun toTypedArray(): Array<@UnsafeVariance V> {
        val size = size

        @Suppress("UNCHECKED_CAST") val array = arrayOfNulls<Any?>(size) as Array<V>
        var i = 0
        var tail = this
        while (tail is Cons) {
            array[i++] = tail.head
            tail = tail.tail
        }
        return array
    }

    /** Materialize this list into a Kotlin [List]. */
    fun toList(): List<V> {
        val list = ArrayList<V>(size)
        var tail = this
        while (tail is Cons) {
            list.add(tail.head)
            tail = tail.tail
        }
        return list
    }

    /** Convert this strict list to a [LazyList]. */
    fun toLazy(): LazyList<V> =
        when (this) {
            is Nil -> LazyList.Nil
            is Cons -> LazyList.Delay(Need.apply { LazyList.Cons(head, tail.toLazy()) })
        }

    /** Return this list in reverse order. */
    fun reverse(): ConsList<V> {
        var tail = this
        var result: ConsList<V> = Nil
        while (tail is Cons) {
            result = Cons(tail.head, result)
            tail = tail.tail
        }
        return result
    }

    /** Return this list in reverse order as a [LazyList]. */
    fun reverseLazy(): LazyList<V> {
        // rev' :: StrictList a -> [a]
        // rev' = go []
        //   where
        //     go :: [a] -> StrictList a -> [a]
        //     go acc SNil         = acc
        //     go acc (SCons x xs) = go (x:acc) xs

        var result: LazyList<V> = LazyList.Nil
        var tail = this
        while (tail is Cons) {
            result = LazyList.Cons(tail.head, result)
            tail = tail.tail
        }
        return result
    }

    // /////////////////////////////////////////////////////////////////////////
    // Folds
    // /////////////////////////////////////////////////////////////////////////

    /** Fold elements from left to right. */
    fun <Z> foldLeft(z: Z, s: (Z, V) -> Z): Z {
        var tail = this
        var result = z
        while (tail is Cons) {
            result = s(result, tail.head)
            tail = tail.tail
        }
        return result
    }

    /** Fold elements from right to left. */
    fun <Z> foldRight(z: Z, s: (V, Z) -> Z): Z {
        val arr = this.toTypedArray()
        var result = z
        for (i in arr.size - 1 downTo 0) {
            result = s(arr[i], result)
        }
        return result
    }

    /** Lazily fold elements from right to left. */
    fun <Z> foldRightLazy(z: Need<Z>, s: (V, Need<Z>) -> Need<Z>): Need<Z> =
        when (this) {
            is Nil -> z
            is Cons -> s(head, tail.foldRightLazy(z, s))
        }

    // /////////////////////////////////////////////////////////////////////////
    // Operators
    // /////////////////////////////////////////////////////////////////////////

    /** Return elements accepted by [f]. */
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

    /** Return whether at least one element satisfies [f]. */
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

    /** Concatenate this list with [other]. */
    operator fun plus(other: ConsList<@UnsafeVariance V>): ConsList<V> =
        this.foldRight(other) { v, acc -> acc.cons(v) }

    override operator fun get(index: Int): V {
        if (index < 0) throw IndexOutOfBoundsException("index: $index")
        if (index >= size) throw IndexOutOfBoundsException("index: $index, size: $size")

        var i = 0
        var current = this
        while (true) {
            if (current !is Cons<V>) {
                throw IndexOutOfBoundsException("index: $index, size: $size")
            }
            if (i == index) return current.head
            current = current.tail
            i += 1
        }
    }

    /** Return a copy with [element] stored at [index]. */
    fun update(index: Int, element: @UnsafeVariance V): ConsList<V> {
        if (index < 0) throw IndexOutOfBoundsException("index: $index")
        if (index >= size) throw IndexOutOfBoundsException("index: $index, size: $size")

        fun go(list: ConsList<V>, i: Int): ConsList<V> =
            when (list) {
                is Nil -> throw IndexOutOfBoundsException("index: $index, size: $size")
                is Cons ->
                    if (i == index) Cons(element, list.tail)
                    else Cons(list.head, go(list.tail, i + 1))
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

    override operator fun iterator(): Iterator<V> {
        val list = this
        return object : Iterator<V> {
            var current: ConsList<@UnsafeVariance V> = list

            override fun hasNext(): Boolean = current !is Nil

            override fun next(): V {
                val c = current
                if (c !is Cons) {
                    throw NoSuchElementException()
                }
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

    override fun listIterator(): ListIterator<V> {
        return listIterator(0)
    }

    override fun listIterator(index: Int): ListIterator<V> {
        requirePosition(index)

        var previous: ConsList<V> = Nil
        var remaining: ConsList<V> = this
        repeat(index) {
            val current = remaining as Cons
            previous = Cons(current.head, previous)
            remaining = current.tail
        }

        return object : ListIterator<V> {
            private var before: ConsList<V> = previous
            private var after: ConsList<V> = remaining
            private var position = index

            override fun hasNext(): Boolean = position < size

            override fun next(): V {
                if (!hasNext()) throw NoSuchElementException()
                val current = after as Cons
                before = Cons(current.head, before)
                after = current.tail
                position += 1
                return current.head
            }

            override fun hasPrevious(): Boolean = position > 0

            override fun previous(): V {
                if (!hasPrevious()) throw NoSuchElementException()
                val current = before as Cons
                after = Cons(current.head, after)
                before = current.tail
                position -= 1
                return current.head
            }

            override fun nextIndex(): Int = position

            override fun previousIndex(): Int = position - 1
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<V> {
        requireRange(fromIndex, toIndex)
        val sliceSize = toIndex - fromIndex
        if (sliceSize == 0) return emptyList()

        var current = dropElements(fromIndex)
        var result: ConsList<V> = Nil
        repeat(sliceSize) {
            val cons = current as Cons
            result = Cons(cons.head, result)
            current = cons.tail
        }
        return result.reverse()
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is List<*>) return false
        if (size != other.size) return false

        val otherIterator = other.iterator()
        var current = this
        while (current is Cons) {
            if (!otherIterator.hasNext()) return false
            if (current.head != otherIterator.next()) return false
            current = current.tail
        }
        return !otherIterator.hasNext()
    }

    final override fun hashCode(): Int {
        var result = 1
        var current = this
        while (current is Cons) {
            result = 31 * result + (current.head?.hashCode() ?: 0)
            current = current.tail
        }
        return result
    }

    /** Empty strict list. */
    object Nil : ConsList<Nothing>() {
        override val size = 0

        override fun <Z> cata(nil: Z, cons: (Nothing, Z) -> Z): Z = nil

        override fun toString(): String = "ConsList()"
    }

    /** Non-empty strict list node with [head] and [tail]. */
    data class Cons<out A>(val head: A, val tail: ConsList<A>) : ConsList<A>() {
        override val size: Int = tail.size + 1

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
                if (current.tail !is Nil) {
                    sb.append(", ")
                }

                current = current.tail
            }
            return sb.toString()
        }
    }

    /** Serializer that encodes strict lists as Kotlin lists. */
    class TypeSerializer<A>(val valueSerializer: KSerializer<A>) : KSerializer<ConsList<A>> {
        override val descriptor = ListSerializer(valueSerializer).descriptor

        override fun serialize(encoder: Encoder, value: ConsList<A>) {
            encoder.encodeSerializableValue(ListSerializer(valueSerializer), value.toList())
        }

        override fun deserialize(decoder: Decoder): ConsList<A> =
            consListFrom(decoder.decodeSerializableValue(ListSerializer(valueSerializer)))
    }
}

/** Return an empty [ConsList]. */
fun <V> emptyConsList(): ConsList<V> = ConsList.Nil

/** Return an empty [ConsList]. */
fun <V> consListOf(): ConsList<V> = ConsList.Nil

/** Build a [ConsList] containing [list] in argument order. */
fun <V> consListOf(vararg list: V): ConsList<V> {
    var result: ConsList<V> = ConsList.Nil
    for (i in list.size - 1 downTo 0) {
        result = ConsList.Cons(list[i], result)
    }
    return result
}

/** Build a [ConsList] by copying [list] in iteration order. */
fun <V> consListFrom(list: List<V>): ConsList<V> {
    var result: ConsList<V> = ConsList.Nil
    for (i in list.size - 1 downTo 0) {
        result = ConsList.Cons(list[i], result)
    }
    return result
}
