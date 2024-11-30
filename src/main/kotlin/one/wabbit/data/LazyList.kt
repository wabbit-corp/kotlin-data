package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = LazyList.TypeSerializer::class)
sealed interface LazyList<out E> {
    val thunk: Need<Strict<E>>

    data class Delay<out E>(override val thunk: Need<Strict<E>>) : LazyList<E> {
        companion object {
            operator fun <E> invoke(thunk: () -> LazyList<E>): Delay<E> =
                Delay(Need.apply { thunk() }.flatMap { it.thunk })
        }
    }
    sealed class Strict<out E> : LazyList<E> {
        override val thunk: Need<Strict<E>> = Need.now(this)
    }

    data object Nil : Strict<Nothing>()

    data class Cons<E>(val head: E, val tail: LazyList<E>) : Strict<E>() {
        companion object {
            operator fun <E> invoke(head: E, tail: Need<LazyList.Strict<E>>): Cons<E> =
                Cons(head, Delay(tail))
            operator fun <E> invoke(head: E, tail: () -> LazyList<E>): Cons<E> =
                Cons(head, Delay(tail))
        }
    }

    operator fun plus(other: LazyList<@UnsafeVariance E>): LazyList<E> =
        Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> other.thunk
                is Cons -> Need.apply { Cons(it.head, it.tail + other) }
            }
        })

    fun <G> map(f: (E) -> G): LazyList<G> {
        return Delay(this.thunk.map {
            when (it) {
                is Nil -> Nil
                is Cons -> Cons(f(it.head), it.tail.map(f))
            }
        })
    }

    fun <G> flatMap(f: (E) -> LazyList<G>): LazyList<G> {
        return Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> Need.now(Nil)
                is Cons -> (f(it.head) + it.tail.flatMap(f)).thunk
            }
        })
    }

    fun filter(f: (E) -> Boolean): LazyList<E> {
        return Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> Need.now(Nil)
                is Cons ->
                    if (f(it.head)) Need.now(Cons(it.head, it.tail.filter(f)))
                    else it.tail.filter(f).thunk
            }
        })
    }

    fun prepend(e: @UnsafeVariance E): LazyList<E> {
        return Cons(e, this)
    }

    fun toList(): List<E> {
        val list = mutableListOf<E>()
        var current = this
        while (true) {
            val strict = current.thunk.value
            when (strict) {
                is LazyList.Nil ->
                    return list
                is LazyList.Cons -> {
                    list.add(strict.head)
                    current = strict.tail
                }
            }
        }
    }

    fun iterator(): Iterator<E> = object : Iterator<E> {
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

    class TypeSerializer<E>(val elementSerializer: KSerializer<E>) : KSerializer<LazyList<E>> {
        private val listSerializer = ListSerializer(elementSerializer)
        override val descriptor: SerialDescriptor = listSerializer.descriptor
        override fun serialize(encoder: Encoder, value: LazyList<E>) {
            listSerializer.serialize(encoder, value.toList())
        }
        override fun deserialize(decoder: Decoder): LazyList<E> {
            return lazyConsListFrom(listSerializer.deserialize(decoder))
        }
    }

    companion object {
        val nil: LazyList<Nothing> = Nil

        fun <A> from(list: List<A>): LazyList<A> {
            return list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }
        }

        fun <A> from(vararg list: A): LazyList<A> {
            return list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }
        }

        fun <A> from(iterator: Iterator<A>): LazyList<A> {
            return if (iterator.hasNext()) {
                val head = iterator.next()
                Cons(head) { from(iterator) }
            } else {
                Nil
            }
        }
    }
}

fun <A> emptyLazyConsList(): LazyList<A> = LazyList.Nil

fun <A> A.cons(list: LazyList<A>): LazyList<A> = LazyList.Cons(this, list)

fun <A> lazyConsListOf(a: A): LazyList<A> =
    LazyList.Cons(a, LazyList.Nil)
fun <A> lazyConsListOf(a1: A, a2: A): LazyList<A> =
    LazyList.Cons(a1, LazyList.Cons(a2, LazyList.Nil))
fun <A> lazyConsListOf(a1: A, a2: A, a3: A): LazyList<A> =
    LazyList.Cons(a1, LazyList.Cons(a2, LazyList.Cons(a3, LazyList.Nil)))

fun <A> lazyConsListOf(vararg list: A): LazyList<A> {
    return list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }
}

fun <A> lazyConsListFrom(list: List<A>): LazyList<A> {
    return list.foldRight(LazyList.Nil as LazyList<A>) { a, acc -> acc.prepend(a) }
}
