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
 * - [size], [frontSize], and [backSize] are O(1)
 * - equality, hashing, and serialization are O(n)
 *
 * Exception contracts:
 * - this type does not throw on empty dequeue; [dequeue] and [uncons] return `null` inside [Need]
 *   and [dequeueOrNull] returns `null`
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
) {
    val frontSize: Int
        get() = ls

    val backSize: Int
        get() = rs

    val size: Int
        get() = ls + rs

    fun isEmpty(): Boolean = ls == 0

    fun enqueue(x: @UnsafeVariance A): BankersQueue<A> = check(ls, left, rs + 1, right.cons(x))

    fun snoc(x: @UnsafeVariance A): BankersQueue<A> = check(ls, left, rs + 1, right.cons(x))

    fun enqueueAllReversed(xs: ConsList<@UnsafeVariance A>): BankersQueue<A> =
        check(ls, left, rs + xs.size, xs + right)

    fun snocReversed(xs: ConsList<@UnsafeVariance A>): BankersQueue<A> =
        check(ls, left, rs + xs.size, xs + right)

    fun dequeue(): Need<Pair<A, BankersQueue<A>>?> =
        left.thunk.map {
            when (it) {
                is LazyList.Nil -> null
                is LazyList.Cons -> it.head to check(ls - 1, it.tail, rs, right)
            }
        }

    fun uncons(): Need<Pair<A, BankersQueue<A>>?> =
        left.thunk.map {
            when (it) {
                is LazyList.Nil -> null
                is LazyList.Cons -> it.head to check(ls - 1, it.tail, rs, right)
            }
        }

    fun dequeueOrNull(): Pair<A, BankersQueue<A>>? = dequeue().value

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

    class TypeSerializer<A>(private val valueSerializer: KSerializer<A>) : KSerializer<BankersQueue<A>> {
        private val listSerializer = ListSerializer(valueSerializer)

        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: BankersQueue<A>) {
            encoder.encodeSerializableValue(listSerializer, value.toLogicalList())
        }

        override fun deserialize(decoder: Decoder): BankersQueue<A> =
            fromConsList(consListFrom(decoder.decodeSerializableValue(listSerializer)))
    }

    companion object {
        fun <A> fromConsList(list: ConsList<A>): BankersQueue<A> =
            BankersQueue(list.size, list.toLazy(), 0, ConsList.Nil)

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
