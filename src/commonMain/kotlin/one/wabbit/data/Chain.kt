// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import kotlin.math.max
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent concatenation-friendly sequence.
 *
 * [Chain] is immutable and persistent. Concatenation, [prepend], and [append] create new chain
 * values without mutating earlier ones. Equality and hashing are logical and based on the flattened
 * element sequence.
 *
 * Complexity notes:
 * - concatenation is O(1)
 * - [toList] and [toArray] are O(n)
 * - equality, hashing, and serialization are O(n)
 *
 * Aliasing guarantees:
 * - [of] copies vararg input into owned storage
 * - [wrapList] and [wrapArray] are explicit view-style adapters and alias caller-provided storage
 *
 * Negative indexing is not applicable because this type does not expose indexed access.
 */
@Serializable(with = Chain.TypeSerializer::class)
class Chain<out A>
private constructor(private val value: Any?, val length: Int, private val depth: Int) {
    private object Empty

    private object Finished

    private class Concat(val left: Any?, val right: Any?)

    private class WrapList<A>(val values: List<A>)

    private class WrapArray<A>(val values: Array<A>)

    // Chain = (Empty | A | Wrap<A> | Concat, Int)
    // Concat = (Empty | A | Wrap<A> | Concat, A | Wrap<A> | Concat)

    /** Concatenate this chain with [that] in O(1). */
    operator fun plus(that: Chain<@UnsafeVariance A>) =
        Chain<A>(
            Concat(this.value, that.value),
            this.length + that.length,
            max(this.depth + 1, that.depth),
        )

    /** Return a chain with [s] before this chain. */
    fun prepend(s: Chain<@UnsafeVariance A>): Chain<A> =
        Chain<A>(Concat(s.value, this.value), s.length + this.length, max(s.depth + 1, this.depth))

    /** Return a chain with [s] after this chain. */
    fun append(s: Chain<@UnsafeVariance A>): Chain<A> =
        Chain<A>(Concat(this.value, s.value), this.length + s.length, max(this.depth + 1, s.depth))

    /** Materialize this chain into a new array. */
    fun toArray(): Array<@UnsafeVariance A> {
        val rights = arrayOfNulls<Any?>(this.depth)
        val out = arrayOfNulls<Any?>(this.length)
        unsafeAppendToH(this.value, rights) { index, value -> out[index] = value }
        return out as Array<@UnsafeVariance A>
    }

    /** Materialize this chain into a new list. */
    fun toList(): List<A> {
        val rights = arrayOfNulls<Any?>(this.depth)
        val out = ArrayList<A>(length)
        unsafeAppendToH(this.value, rights) { _, value -> out.add(value as A) }
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is Chain<*> && length == other.length && toList() == other.toList()

    override fun hashCode(): Int = toList().hashCode()

    override fun toString(): String = toList().joinToString(prefix = "Chain(", postfix = ")")

    /** Serializer that encodes chains as flattened lists. */
    class TypeSerializer<E>(val elementSerializer: KSerializer<E>) : KSerializer<Chain<E>> {
        private val listSerializer = ListSerializer(elementSerializer)
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(encoder: Encoder, value: Chain<E>) {
            listSerializer.serialize(encoder, value.toList())
        }

        override fun deserialize(decoder: Decoder): Chain<E> =
            Chain.wrapList(listSerializer.deserialize(decoder))
    }

    /** Factories for [Chain]. */
    companion object {
        /** Empty chain singleton. */
        val empty: Chain<Nothing> = Chain<Nothing>(Empty, 0, 1)

        /** Create a chain containing one [value]. */
        fun <A> of(value: A): Chain<A> = Chain(value, 1, 1)

        /** Create a chain from [value]. */
        fun <A> of(vararg value: A): Chain<A> = Chain(WrapArray(value), value.size, 1)

        /**
         * Wraps [value] without copying it.
         *
         * This aliases the caller-provided array, so later mutations to [value] are reflected by
         * the returned chain.
         */
        fun <A> wrapArray(value: Array<A>): Chain<A> = Chain(WrapArray(value), value.size, 1)

        /**
         * Wraps [value] without copying it.
         *
         * This aliases the caller-provided list, so later mutations to the underlying list are
         * reflected by the returned chain.
         */
        fun <A> wrapList(value: List<A>): Chain<A> = Chain(WrapList(value), value.size, 1)

        private fun unsafeAppendToH(cord: Any?, rights: Array<Any?>, out: (Int, Any?) -> Unit) {
            var current: Any? = cord
            var stackPtr = 0
            var outputPtr = 0

            while (current !== Finished) {
                if (current === Empty) {
                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = Finished
                    }
                } else if (current is WrapList<*>) {
                    val s = current as WrapList<Any>
                    for (value in s.values) {
                        out(outputPtr++, value)
                    }

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = Finished
                    }
                } else if (current is WrapArray<*>) {
                    val s = current as WrapArray<Any>
                    for (value in s.values) {
                        out(outputPtr++, value)
                    }

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = Finished
                    }
                } else if (current is Concat) {
                    val c = current as Concat
                    current = c.left
                    rights[stackPtr] = c.right
                    stackPtr += 1
                } else {
                    out(outputPtr++, current)

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = Finished
                    }
                }
            }
        }
    }
}
