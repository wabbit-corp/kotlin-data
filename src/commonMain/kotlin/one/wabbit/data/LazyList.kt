package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent lazy linked list backed by [Need].
 *
 * Tails are evaluated only as demanded and then cached by their underlying [Need].
 */
@Serializable(with = LazyList.TypeSerializer::class)
sealed interface LazyList<out E> : Iterable<E> {
    /**
     * Deferred strict representation of this lazy list.
     */
    val thunk: Need<Strict<E>>

    /**
     * Delayed lazy-list node.
     */
    data class Delay<out E>(override val thunk: Need<Strict<E>>) : LazyList<E> {
        /**
         * Factories for delayed nodes.
         */
        companion object {
            /**
             * Delay evaluation of [thunk].
             */
            operator fun <E> invoke(thunk: () -> LazyList<E>): Delay<E> =
                Delay(Need.apply { thunk() }.flatMap { it.thunk })
        }

        override fun equals(other: Any?): Boolean = lazyListEquals(this, other)

        override fun hashCode(): Int = lazyListHashCode(this)

        override fun toString(): String = toList().toString()
    }

    /**
     * Already evaluated lazy-list node.
     */
    sealed class Strict<out E> : LazyList<E> {
        override val thunk: Need<Strict<E>> = Need.now(this)
    }

    /**
     * Empty lazy list.
     */
    object Nil : Strict<Nothing>() {
        override fun equals(other: Any?): Boolean = lazyListEquals(this, other)

        override fun hashCode(): Int = lazyListHashCode(this)

        override fun toString(): String = toList().toString()
    }

    /**
     * Non-empty lazy-list node with [head] and [tail].
     */
    data class Cons<E>(val head: E, val tail: LazyList<E>) : Strict<E>() {
        /**
         * Factories for strict cons nodes.
         */
        companion object {
            /**
             * Create a cons node with a delayed strict [tail].
             */
            operator fun <E> invoke(head: E, tail: Need<LazyList.Strict<E>>): Cons<E> =
                Cons(head, Delay(tail))

            /**
             * Create a cons node with a delayed lazy [tail].
             */
            operator fun <E> invoke(head: E, tail: () -> LazyList<E>): Cons<E> =
                Cons(head, Delay(tail))
        }

        override fun equals(other: Any?): Boolean = lazyListEquals(this, other)

        override fun hashCode(): Int = lazyListHashCode(this)

        override fun toString(): String = toList().toString()
    }

    /**
     * Lazily concatenate this list with [other].
     */
    operator fun plus(other: LazyList<@UnsafeVariance E>): LazyList<E> =
        Delay(
            this.thunk.flatMap {
                when (it) {
                    is Nil -> other.thunk
                    is Cons -> Need.apply { Cons(it.head, it.tail + other) }
                }
            }
        )

    /**
     * Lazily transform each element with [f].
     */
    fun <G> map(f: (E) -> G): LazyList<G> =
        Delay(
            this.thunk.map {
                when (it) {
                    is Nil -> Nil
                    is Cons -> Cons(f(it.head), it.tail.map(f))
                }
            }
        )

    /**
     * Lazily transform each element with [f] and concatenate returned lists.
     */
    fun <G> flatMap(f: (E) -> LazyList<G>): LazyList<G> =
        Delay(
            this.thunk.flatMap {
                when (it) {
                    is Nil -> Need.now(Nil)
                    is Cons -> (f(it.head) + it.tail.flatMap(f)).thunk
                }
            }
        )

    /**
     * Lazily keep elements accepted by [f].
     */
    fun filter(f: (E) -> Boolean): LazyList<E> =
        Delay(
            this.thunk.flatMap {
                when (it) {
                    is Nil -> Need.now(Nil)
                    is Cons ->
                        if (f(it.head)) {
                            Need.now(Cons(it.head, it.tail.filter(f)))
                        } else {
                            it.tail.filter(f).thunk
                        }
                }
            }
        )

    /**
     * Return the element at zero-based index [n].
     */
    operator fun get(n: Int): E {
        if (n < 0) {
            throw IndexOutOfBoundsException("Index: $n")
        }
        var current = this
        repeat(n) {
            val strict = current.thunk.value
            when (strict) {
                is Nil -> throw IndexOutOfBoundsException("Index: $n")
                is Cons -> current = strict.tail
            }
        }
        val strict = current.thunk.value
        return when (strict) {
            is Nil -> throw IndexOutOfBoundsException("Index: $n")
            is Cons -> strict.head
        }
    }

    /**
     * Return at most the first [n] elements.
     */
    fun take(n: Int): LazyList<E> =
        if (n <= 0) {
            Nil
        } else {
            Delay(
                this.thunk.flatMap {
                    when (it) {
                        is Nil -> Need.now(Nil)
                        is Cons -> Need.apply { Cons(it.head, it.tail.take(n - 1)) }
                    }
                }
            )
        }

    /**
     * Return a lazy list with [e] before this list.
     */
    fun prepend(e: @UnsafeVariance E): LazyList<E> = Cons(e, this)

    /**
     * Strictly materialize this lazy list into a Kotlin [List].
     */
    fun toList(): List<E> {
        val list = mutableListOf<E>()
        var current = this
        while (true) {
            val strict = current.thunk.value
            when (strict) {
                is LazyList.Nil -> return list
                is LazyList.Cons -> {
                    list.add(strict.head)
                    current = strict.tail
                }
            }
        }
    }

    override operator fun iterator(): Iterator<E> =
        object : Iterator<E> {
            var current: LazyList<@UnsafeVariance E> = this@LazyList

            override fun hasNext(): Boolean {
                val strict = current.thunk.value
                return when (strict) {
                    is LazyList.Nil -> false
                    is LazyList.Cons -> true
                }
            }

            override fun next(): E {
                val strict = current.thunk.value
                return when (strict) {
                    is LazyList.Nil -> throw NoSuchElementException()
                    is LazyList.Cons -> {
                        current = strict.tail
                        strict.head
                    }
                }
            }
        }

    /**
     * Serializer that encodes lazy lists as strict Kotlin lists.
     */
    class TypeSerializer<E>(val elementSerializer: KSerializer<E>) : KSerializer<LazyList<E>> {
        private val listSerializer = ListSerializer(elementSerializer)
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: LazyList<E>) {
            listSerializer.serialize(encoder, value.toList())
        }

        override fun deserialize(decoder: Decoder): LazyList<E> =
            lazyConsListFrom(listSerializer.deserialize(decoder))
    }

    /**
     * Factories for [LazyList].
     */
    companion object {
        /**
         * Empty lazy list singleton.
         */
        val nil: LazyList<Nothing> = Nil

        /**
         * Build a lazy list from [list] in iteration order.
         */
        fun <A> from(list: List<A>): LazyList<A> =
            list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }

        /**
         * Build a lazy list from [list] in argument order.
         */
        fun <A> from(vararg list: A): LazyList<A> =
            list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }

        /**
         * Build a lazy list by consuming [iterator] as elements are demanded.
         */
        fun <A> from(iterator: Iterator<A>): LazyList<A> =
            if (iterator.hasNext()) {
                val head = iterator.next()
                Cons(head) { from(iterator) }
            } else {
                Nil
            }

        /**
         * Build a recursive lazy list.
         */
        fun <A> recursive(f: (LazyList<A>) -> LazyList<A>): LazyList<A> =
            Delay(Need.recursive<LazyList.Strict<A>> { f(Delay(it)).thunk })
    }
}

private fun lazyListEquals(left: LazyList<*>, other: Any?): Boolean {
    if (left === other) return true
    if (other !is LazyList<*>) return false

    var currentLeft: LazyList<*> = left
    var currentRight: LazyList<*> = other
    while (true) {
        val leftStrict = currentLeft.thunk.value
        val rightStrict = currentRight.thunk.value
        when {
            leftStrict is LazyList.Nil && rightStrict is LazyList.Nil -> return true
            leftStrict is LazyList.Cons && rightStrict is LazyList.Cons -> {
                if (leftStrict.head != rightStrict.head) return false
                currentLeft = leftStrict.tail
                currentRight = rightStrict.tail
            }
            else -> return false
        }
    }
}

private fun lazyListHashCode(list: LazyList<*>): Int {
    var result = 1
    var current: LazyList<*> = list
    while (true) {
        when (val strict = current.thunk.value) {
            is LazyList.Nil -> return result
            is LazyList.Cons -> {
                result = 31 * result + (strict.head?.hashCode() ?: 0)
                current = strict.tail
            }
        }
    }
}

/**
 * Return an empty [LazyList].
 */
fun <A> emptyLazyConsList(): LazyList<A> = LazyList.Nil

/**
 * Prepend this value to [list].
 */
fun <A> A.cons(list: LazyList<A>): LazyList<A> = LazyList.Cons(this, list)

/**
 * Build a one-element [LazyList].
 */
fun <A> lazyConsListOf(a: A): LazyList<A> = LazyList.Cons(a, LazyList.Nil)

/**
 * Build a two-element [LazyList].
 */
fun <A> lazyConsListOf(a1: A, a2: A): LazyList<A> =
    LazyList.Cons(a1, LazyList.Cons(a2, LazyList.Nil))

/**
 * Build a three-element [LazyList].
 */
fun <A> lazyConsListOf(a1: A, a2: A, a3: A): LazyList<A> =
    LazyList.Cons(a1, LazyList.Cons(a2, LazyList.Cons(a3, LazyList.Nil)))

/**
 * Build a [LazyList] from [list] in argument order.
 */
fun <A> lazyConsListOf(vararg list: A): LazyList<A> =
    list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }

/**
 * Build a [LazyList] by copying [list] in iteration order.
 */
fun <A> lazyConsListFrom(list: List<A>): LazyList<A> =
    list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }
