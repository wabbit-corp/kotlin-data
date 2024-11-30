package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.max

@Serializable(with = Chain.TypeSerializer::class)
class Chain<out A>(private val value: Any?, val length: Int, private val depth: Int) {
    private object Empty
    private class Concat(val left: Any?, val right: Any?)
    private class WrapList<A>(val values: List<A>)
    private class WrapArray<A>(val values: Array<A>)
    // Chain = (Empty | A | Wrap<A> | Concat, Int)
    // Concat = (Empty | A | Wrap<A> | Concat, A | Wrap<A> | Concat)

    operator fun plus(that: Chain<@UnsafeVariance A>) =
        Chain<A>(
            Concat(this.value, that.value),
            this.length + that.length,
            max(this.depth + 1, that.depth)
        )

    fun prepend(s: Chain<@UnsafeVariance A>): Chain<A> =
        Chain<A>(
            Concat(s, this.value),
            s.length + this.length,
            this.depth
        )

    fun append(s: Chain<@UnsafeVariance A>): Chain<A> =
        Chain<A>(
            Concat(this.value, s),
            this.length + s.length,
            this.depth + 1
        )

    fun toArray(): Array<@UnsafeVariance A> {
        val rights = arrayOfNulls<Any?>(this.depth)
        val out = arrayOfNulls<Any?>(this.length)
        unsafeAppendToH(this.value, rights) { index, value -> out[index] = value }
        return out as Array<@UnsafeVariance A>
    }

    fun toList(): List<A> {
        val rights = arrayOfNulls<Any?>(this.depth)
        val out = ArrayList<A>(length)
        unsafeAppendToH(this.value, rights) { _, value -> out.add(value as A) }
        return out
    }

    class TypeSerializer<E>(val elementSerializer: KSerializer<E>) : KSerializer<Chain<E>> {
        private val listSerializer = ListSerializer(elementSerializer)
        override val descriptor: SerialDescriptor = listSerializer.descriptor
        override fun serialize(encoder: Encoder, value: Chain<E>) {
            listSerializer.serialize(encoder, value.toList())
        }
        override fun deserialize(decoder: Decoder): Chain<E> {
            return Chain.fromList(listSerializer.deserialize(decoder))
        }
    }

    companion object {
        val empty: Chain<Nothing> = Chain<Nothing>(Empty, 0, 1)
        fun <A> of(value: A): Chain<A> = Chain(value, 1, 1)
        fun <A> of(vararg value: A): Chain<A> = Chain(WrapArray(value), value.size, 1)
        fun <A> fromArray(value: Array<A>): Chain<A> = Chain(WrapArray(value), value.size, 1)
        fun <A> fromList(value: List<A>): Chain<A> = Chain(WrapList(value), value.size, 1)

        private fun unsafeAppendToH(cord: Any?, rights: Array<Any?>, out: (Int, Any?) -> Unit) {
            var current: Any? = cord
            var stackPtr  = 0
            var outputPtr = 0

            while (current != null) {
                val javaClass = current.javaClass
                if (javaClass === Empty::class.java) {
                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                }
                else if (javaClass === WrapList::class.java) {
                    val s = current as WrapList<Any>
                    for (value in s.values) {
                        out(outputPtr++, value)
                    }

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                }
                else if (javaClass === WrapArray::class.java) {
                    val s = current as WrapList<Any>
                    for (value in s.values) {
                        out(outputPtr++, value)
                    }

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                }
                else if (javaClass === Concat::class.java) {
                    val c = current as Concat
                    current = c.left
                    rights[stackPtr] = c.right
                    stackPtr += 1
                }
                else {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    out(outputPtr++, current)

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                }
            }
        }
    }
}
