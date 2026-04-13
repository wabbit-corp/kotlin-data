@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent leftist min-heap.
 *
 * This heap is immutable and persistent: [insert], [merge], and [deleteMin] return new heap values
 * without mutating earlier ones. Equality, hashing, and serialization are based on logical sorted
 * contents rather than tree shape.
 *
 * Complexity notes:
 * - [findMin] is O(1)
 * - [insert], [merge], and [deleteMin] are O(log n)
 * - [size] is O(1)
 * - equality, hashing, and serialization are O(n log n) because they normalize via sorted contents
 *
 * Exception contracts:
 * - [findMin] throws [NoSuchElementException] on an empty heap
 * - [deleteMin] throws [NoSuchElementException] on an empty heap
 */
@Serializable(with = LeftistHeap.TypeSerializer::class)
sealed class LeftistHeap<out E : Comparable<@UnsafeVariance E>> {
    abstract val size: Int

    data object Empty : LeftistHeap<Nothing>() {
        override val size: Int = 0
    }

    @InternalDataApi
    class Node<E : Comparable<E>> @InternalDataApi constructor(
        val rank: Int,
        val value: E,
        val left: LeftistHeap<E>,
        val right: LeftistHeap<E>,
    ) : LeftistHeap<E>() {
        override val size: Int = 1 + left.size + right.size
    }

    fun findMin(): E =
        when (this) {
            is Empty -> throw NoSuchElementException()
            is Node -> value
        }

    fun deleteMin(): LeftistHeap<E> =
        when (this) {
            is Empty -> throw NoSuchElementException("Cannot deleteMin from an empty leftist heap")
            is Node -> merge(left, right)
        }

    fun merge(that: LeftistHeap<@UnsafeVariance E>): LeftistHeap<E> = Companion.merge(this, that)

    fun insert(value: @UnsafeVariance E): LeftistHeap<E> = merge(Node(1, value, Empty, Empty), this)

    final override fun equals(other: Any?): Boolean =
        other is LeftistHeap<*> && sortedElements() == other.sortedElements()

    final override fun hashCode(): Int = sortedElements().hashCode()

    private fun sortedElements(): List<E> {
        val result = mutableListOf<E>()
        var current: LeftistHeap<E> = this
        while (current is Node) {
            result += current.value
            current = current.deleteMin()
        }
        return result
    }

    class TypeSerializer<E : Comparable<E>>(private val valueSerializer: KSerializer<E>) :
        KSerializer<LeftistHeap<E>> {
        private val listSerializer = ListSerializer(valueSerializer)

        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: LeftistHeap<E>) {
            encoder.encodeSerializableValue(listSerializer, value.sortedElements())
        }

        override fun deserialize(decoder: Decoder): LeftistHeap<E> {
            val values = decoder.decodeSerializableValue(listSerializer)
            var heap: LeftistHeap<E> = empty()
            for (value in values) {
                heap = heap.insert(value)
            }
            return heap
        }
    }

    companion object {
        val empty: LeftistHeap<Nothing> = Empty

        @Suppress("UNCHECKED_CAST")
        fun <E : Comparable<E>> empty(): LeftistHeap<E> = empty as LeftistHeap<E>

        fun <E : Comparable<E>> of(vararg values: E): LeftistHeap<E> {
            var heap: LeftistHeap<E> = empty()
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

        private fun <E : Comparable<E>> makeT(
            value: E,
            left: LeftistHeap<E>,
            right: LeftistHeap<E>,
        ): LeftistHeap<E> =
            if (rank(left) >= rank(right)) {
                Node(rank(right) + 1, value, left, right)
            } else {
                Node(rank(left) + 1, value, right, left)
            }

        internal fun <E : Comparable<E>> merge(
            left: LeftistHeap<E>,
            right: LeftistHeap<E>,
        ): LeftistHeap<E> {
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
