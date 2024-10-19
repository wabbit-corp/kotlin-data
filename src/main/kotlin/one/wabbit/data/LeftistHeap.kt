package one.wabbit.data

import kotlinx.serialization.Serializable

@Serializable sealed class LeftistHeap<out E : Comparable<@UnsafeVariance E>> {
    @Serializable data object Empty : LeftistHeap<Nothing>()
    @Serializable data class Node<E : Comparable<E>>(
        val rank: Int, val value: E,
        val left: LeftistHeap<E>,
        val right: LeftistHeap<E>) : LeftistHeap<E>()

    val size: Int
        get() = when (this) {
            is Empty -> 0
            is Node -> 1 + left.size + right.size
        }

    fun findMin(): E = when (this) {
        is Empty -> throw NoSuchElementException()
        is Node -> value
    }

    fun deleteMin(): LeftistHeap<E> = when (this) {
        is Empty -> throw IllegalStateException("Cannot deleteMin from an empty leftist heap")
        is Node -> merge(left, right)
    }

    fun insert(value: @UnsafeVariance E): LeftistHeap<E> =
        merge(Node(1, value, Empty, Empty), this)

    companion object {
        val empty: LeftistHeap<Nothing> = Empty

        fun <E : Comparable<E>> of(vararg values: E): LeftistHeap<E> {
            var heap: LeftistHeap<E> = empty
            for (value in values) {
                heap = heap.insert(value)
            }
            return heap
        }

        private fun <E : Comparable<E>> rank(heap: LeftistHeap<E>): Int =
            when (heap) {
                is Empty -> 0
                is Node -> heap.rank
            }

        private fun <E : Comparable<E>> makeT(value: E, left: LeftistHeap<E>, right: LeftistHeap<E>): LeftistHeap<E> =
            if (rank(left) >= rank(right))
                Node(rank(right) + 1, value, left, right)
            else
                Node(rank(left) + 1, value, right, left)

        private fun <E : Comparable<E>> merge(left: LeftistHeap<E>, right: LeftistHeap<E>): LeftistHeap<E> {
            if (left !is Node) return right
            if (right !is Node) return left

            val cmp = left.value.compareTo(right.value)

            if (cmp <= 0) {
                return makeT(left.value, left.left, merge(left.right, right))
            } else {
                return makeT(right.value, right.left, merge(left, right.right))
            }
        }
    }
}

//sealed class Accum<out V> {
//    object Empty : Accum<Nothing>()
//    data class C1<V>(val b: V) : Accum<V>()
//    data class C2<V>(val b: V, val c: V, val bc: Need<V>, val rest: Accum<V>) : Accum<V>()
//    data class C3<V>(val b: V, val c: V, val d: V, val bc: Need<V>, val rest: Accum<V>) : Accum<V>()
//
//    fun cons(a: @UnsafeVariance V, app: (V, V) -> @UnsafeVariance V): Accum<V> {
//        return when (this) {
//            Empty -> C1(a)
//            is C1 -> C2(a, this.b, Need.apply { app(a, this.b) }, Empty)
//            is C2 -> C3(a, this.b, this.c, this.bc, this.rest)
//            is C3 -> C2(a, this.bc.value, Need.apply { app(a, this.bc.value) }, this.rest.cons(this.d, app))
//        }
//    }
//    fun <R> query(inj: (V) -> R, combine: (R, Need<R>) -> Need<R>, empty: R): Need<R> {
//        return when (this) {
//            is Empty -> Need.now(empty)
//            is C1 -> Need.apply { inj(this.b) }
//            is C2 -> combine(inj(this.b), Need.apply { inj(this.c) }.flatMap { rc -> combine(rc, this.rest.query(inj, combine, empty)) })
//            is C3 -> combine(inj(this.b), Need.apply { inj(this.c) }.flatMap { rc -> combine(rc, Need.apply { inj(this.d) }.flatMap { rd -> combine(rd, this.rest.query(inj, combine, empty)) } )})
//        }
//    }
//}
//
//data class Set<V : Any>(val underlying: Accum<Entry<V>>) {
//    data class Entry<V : Any>(val values: Array<V>, val bitSet: BitSet) {
//        fun merge(that: Entry<V>): Entry<V> {
//            val N = this.values.size + that.values.size
//            val array = arrayOfNulls(N)
//            for (i in 0 until this.values.size) {
//                for (j in 0 until that.values.size) {
//
//                }
//            }
//        }
//    }
//
//    fun cons(v: V): Set<V> = Set(underlying.cons(v))
//}
