package one.wabbit.data

sealed interface LazyConsList<out E> {
    val thunk: Need<Strict<E>>

    data class Delay<out E>(override val thunk: Need<Strict<E>>) : LazyConsList<E>
    sealed class Strict<out E> : LazyConsList<E> {
        override val thunk: Need<Strict<E>> = Need.now(this)
    }

    data object Nil : Strict<Nothing>()
    data class Cons<E>(val head: E, val tail: LazyConsList<E>) : Strict<E>()

    operator fun plus(other: LazyConsList<@UnsafeVariance E>): LazyConsList<E> =
        Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> other.thunk
                is Cons -> Need.apply { Cons(it.head, it.tail + other) }
            }
        })

    fun <G> map(f: (E) -> G): LazyConsList<G> {
        return Delay(this.thunk.map {
            when (it) {
                is Nil -> Nil
                is Cons -> Cons(f(it.head), it.tail.map(f))
            }
        })
    }

    fun <G> flatMap(f: (E) -> LazyConsList<G>): LazyConsList<G> {
        return Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> Need.now(Nil)
                is Cons -> (f(it.head) + it.tail.flatMap(f)).thunk
            }
        })
    }

    fun filter(f: (E) -> Boolean): LazyConsList<E> {
        return Delay(this.thunk.flatMap {
            when (it) {
                is Nil -> Need.now(Nil)
                is Cons ->
                    if (f(it.head)) Need.now(Cons(it.head, it.tail.filter(f)))
                    else it.tail.filter(f).thunk
            }
        })
    }

    fun cons(e: @UnsafeVariance E): LazyConsList<E> {
        return Cons(e, this)
    }

    fun toList(): List<E> {
        val list = mutableListOf<E>()
        var current = this
        while (true) {
            val strict = current.thunk.value
            when (strict) {
                is LazyConsList.Nil ->
                    return list
                is LazyConsList.Cons -> {
                    list.add(strict.head)
                    current = strict.tail
                }
            }
        }
    }

    fun iterator(): Iterator<E> = object : Iterator<E> {
        var current: LazyConsList<@UnsafeVariance E> = this@LazyConsList

        override fun hasNext(): Boolean {
            val strict = current.thunk.value
            return when (strict) {
                is LazyConsList.Nil -> false
                is LazyConsList.Cons -> true
            }
        }

        override fun next(): E {
            val strict = current.thunk.value
            return when (strict) {
                is LazyConsList.Nil -> throw NoSuchElementException()
                is LazyConsList.Cons -> {
                    current = strict.tail
                    strict.head
                }
            }
        }

    }
}

fun <A> emptyLazyConsList(): LazyConsList<A> = LazyConsList.Nil

fun <A> lazyConsListOf(a: A): LazyConsList<A> =
    LazyConsList.Cons(a, LazyConsList.Nil)
fun <A> lazyConsListOf(a1: A, a2: A): LazyConsList<A> =
    LazyConsList.Cons(a1, LazyConsList.Cons(a2, LazyConsList.Nil))
fun <A> lazyConsListOf(a1: A, a2: A, a3: A): LazyConsList<A> =
    LazyConsList.Cons(a1, LazyConsList.Cons(a2, LazyConsList.Cons(a3, LazyConsList.Nil)))

fun <A> lazyConsListOf(vararg list: A): LazyConsList<A> {
    return list.foldRight(LazyConsList.Nil as LazyConsList<A>) { a, acc -> acc.cons(a) }
}

fun <A> lazyConsListFrom(list: List<A>): LazyConsList<A> {
    return list.foldRight(LazyConsList.Nil as LazyConsList<A>) { a, acc -> acc.cons(a) }
}
