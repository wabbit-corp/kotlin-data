package one.wabbit.data

import kotlin.math.max
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persistent concatenation-friendly character sequence.
 *
 * Concatenation and append/prepend create new cords without mutating existing values. Indexing and
 * subsequences materialize through [toString], so this type is optimized for building strings before
 * final materialization rather than random access.
 */
@Serializable(with = Cord.TypeSerializer::class)
class Cord private constructor(private val value: Any, override val length: Int, private val depth: Int) : CharSequence {
    private class Concat(val left: Any, val right: Any)

    // Cord = (String | Concat, Int)
    // Concat = (String | Concat, String | Concat)

    /**
     * Concatenate this cord with [that].
     */
    operator fun plus(that: Cord) =
        Cord(
            Concat(this.value, that.value),
            this.length + that.length,
            max(this.depth + 1, that.depth),
        )

    /**
     * Concatenate this cord with [that].
     */
    operator fun plus(that: String) = append(that)

    /**
     * Return a cord with [s] before this cord.
     */
    fun prepend(s: String): Cord = Cord(Concat(s, this.value), s.length + this.length, this.depth)

    /**
     * Return a cord with [s] after this cord.
     */
    fun append(s: String): Cord =
        Cord(Concat(this.value, s), this.length + s.length, this.depth + 1)

    override fun get(index: Int): Char {
        if (index !in 0 until length) {
            throw IndexOutOfBoundsException("index: $index, length: $length")
        }
        return toString()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex < startIndex || endIndex > length) {
            throw IndexOutOfBoundsException("startIndex: $startIndex, endIndex: $endIndex, length: $length")
        }
        if (startIndex == endIndex) {
            return empty
        }
        return of(toString().substring(startIndex, endIndex))
    }

    override fun equals(other: Any?): Boolean = other is Cord && toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String {
        val rights = arrayOfNulls<Any>(this.depth)
        val out = CharArray(this.length)
        unsafeAppendToH(rights, out, this.value)
        return out.concatToString()
    }

    /**
     * Serializer that encodes cords as strings.
     */
    internal class TypeSerializer : KSerializer<Cord> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("Cord", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Cord) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Cord = Cord.of(decoder.decodeString())
    }

    /**
     * Factories for [Cord].
     */
    companion object {
        /**
         * Empty cord singleton.
         */
        val empty: Cord = Cord("", 0, 1)

        /**
         * Create a cord containing one [value].
         */
        fun of(value: Char): Cord = Cord(value, 1, 1)

        /**
         * Create a cord from [value].
         */
        fun of(value: String): Cord = Cord(value, value.length, 1)

        /**
         * Join [args] with [sep].
         */
        fun join(sep: String, args: List<Cord>) =
            args.fold(empty) { acc, arg ->
                if (acc.length == 0) {
                    arg
                } else {
                    acc + sep + arg
                }
            }

        private fun unsafeAppendToH(rights: Array<Any?>, out: CharArray, cord: Any) {
            var current: Any? = cord
            var stackPtr = 0
            var outputPtr = 0

            while (current != null) {
                if (current is Char) {
                    out[outputPtr++] = current

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                } else if (current is String) {
                    val s = current
                    for (i in s.indices) {
                        out[outputPtr + i] = s[i]
                    }
                    outputPtr += s.length
                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                } else {
                    val c = current as Concat
                    current = c.left
                    rights[stackPtr] = c.right
                    stackPtr += 1
                }
            }
        }
    }
}
