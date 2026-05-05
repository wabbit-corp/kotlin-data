package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent amortized O(1) FIFO queue implemented as a banker's queue.
 *
 * The queue is immutable and persistent: enqueue/dequeue operations return new queue values and do
 * not mutate earlier instances. Equality, hashing, and serialization are defined in terms of the
 * logical dequeue order rather than the internal left/right representation.
 *
 * Complexity notes:
 * - [enqueue], [snoc], [dequeue], and [uncons] are amortized O(1)
 * - [enqueueAll], [toList], iteration, equality, hashing, and serialization are O(n)
 * - [size], [frontSize], and [backSize] are O(1)
 * - [peek] and [peekOrNull] are amortized O(1)
 *
 * Exception contracts:
 * - this type does not throw on empty dequeue; [dequeue] and [uncons] return `null` inside [Need]
 *   and [dequeueOrNull] returns `null`
 * - [peek] throws [NoSuchElementException] on an empty queue, while [peekOrNull] returns `null`
 *
 * Naming:
 * - [enqueue] and [snoc] are aliases
 * - [dequeue] and [uncons] are aliases
 */
@Serializable(with = BankersQueue.TypeSerializer::class)
class BankersQueue<out A> private constructor(
    val ls: Int,
    val left: LazyList<A>,
    val rs: Int,
    val right: ConsList<A>,
) : Iterable<A> {
    /**
     * Number of elements currently represented by the lazy front list.
     */
    val frontSize: Int
        get() = ls

    /**
     * Number of elements currently buffered at the back of the queue.
     */
    val backSize: Int
        get() = rs

    /**
     * Total logical queue size.
     */
    val size: Int
        get() = ls + rs

    /**
     * Return whether this queue contains no elements.
     */
    fun isEmpty(): Boolean = ls == 0

    /**
     * Return the next value to dequeue, or throw [NoSuchElementException] when empty.
     */
    fun peek(): A = peekOrNull() ?: throw NoSuchElementException("BankersQueue is empty")

    /**
     * Return the next value to dequeue, or null when empty.
     */
    fun peekOrNull(): A? =
        when (val value = left.thunk.value) {
            is LazyList.Nil -> null
            is LazyList.Cons -> value.head
        }

    /**
     * Return a queue with [x] appended to the back.
     */
    fun enqueue(x: @UnsafeVariance A): BankersQueue<A> = check(ls, left, rs + 1, right.cons(x))

    /**
     * Alias for [enqueue].
     */
    fun snoc(x: @UnsafeVariance A): BankersQueue<A> = check(ls, left, rs + 1, right.cons(x))

    /**
     * Return a queue with all [xs] appended in iteration order.
     */
    fun enqueueAll(xs: Iterable<@UnsafeVariance A>): BankersQueue<A> {
        var result: BankersQueue<A> = this
        for (value in xs) {
            result = result.enqueue(value)
        }
        return result
    }

    /**
     * Return a queue with [xs] appended when [xs] is already in reverse enqueue order.
     */
    fun enqueueAllReversed(xs: ConsList<@UnsafeVariance A>): BankersQueue<A> =
        check(ls, left, rs + xs.size, xs + right)

    /**
     * Alias for [enqueueAllReversed].
     */
    fun snocReversed(xs: ConsList<@UnsafeVariance A>): BankersQueue<A> =
        check(ls, left, rs + xs.size, xs + right)

    /**
     * Lazily dequeue the next value and resulting queue, or null when empty.
     */
    fun dequeue(): Need<Pair<A, BankersQueue<A>>?> =
        left.thunk.map {
            when (it) {
                is LazyList.Nil -> null
                is LazyList.Cons -> it.head to check(ls - 1, it.tail, rs, right)
            }
        }

    /**
     * Alias for [dequeue].
     */
    fun uncons(): Need<Pair<A, BankersQueue<A>>?> =
        left.thunk.map {
            when (it) {
                is LazyList.Nil -> null
                is LazyList.Cons -> it.head to check(ls - 1, it.tail, rs, right)
            }
        }

    /**
     * Strictly dequeue the next value and resulting queue, or null when empty.
     */
    fun dequeueOrNull(): Pair<A, BankersQueue<A>>? = dequeue().value

    /**
     * Materialize this queue in dequeue order.
     */
    fun toList(): List<A> = toLogicalList()

    override operator fun iterator(): Iterator<A> = toLogicalList().iterator()

    override fun equals(other: Any?): Boolean =
        other is BankersQueue<*> && toLogicalList() == other.toLogicalList()

    override fun hashCode(): Int = toLogicalList().hashCode()

    private fun toLogicalList(): List<A> {
        val result = mutableListOf<A>()
        var current: BankersQueue<A> = this
        while (true) {
            val next = current.uncons().value ?: return result
            result += next.first
            current = next.second
        }
    }

    /**
     * Serializer that encodes queues as lists in dequeue order.
     */
    class TypeSerializer<A>(private val valueSerializer: KSerializer<A>) : KSerializer<BankersQueue<A>> {
        private val listSerializer = ListSerializer(valueSerializer)

        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: BankersQueue<A>) {
            encoder.encodeSerializableValue(listSerializer, value.toLogicalList())
        }

        override fun deserialize(decoder: Decoder): BankersQueue<A> =
            fromConsList(consListFrom(decoder.decodeSerializableValue(listSerializer)))
    }

    /**
     * Factory helpers for [BankersQueue].
     */
    companion object {
        /**
         * Build a queue from [list] in list iteration order.
         */
        fun <A> fromConsList(list: ConsList<A>): BankersQueue<A> =
            BankersQueue(list.size, list.toLazy(), 0, ConsList.Nil)

        /**
         * Return an empty queue.
         */
        fun <A> empty(): BankersQueue<A> = BankersQueue(0, LazyList.Nil, 0, ConsList.Nil)

        private fun <A> check(
            ls: Int,
            left: LazyList<A>,
            rs: Int,
            right: ConsList<A>,
        ): BankersQueue<A> =
            if (rs <= ls) {
                BankersQueue(ls, left, rs, right)
            } else {
                BankersQueue(ls + rs, left + right.reverseLazy(), 0, ConsList.Nil)
            }
    }
}
