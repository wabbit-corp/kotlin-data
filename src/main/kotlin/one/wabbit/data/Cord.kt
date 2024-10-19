package one.wabbit.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.max

class Cord(private val value: Any, val length: Int, private val depth: Int) {
    private class Concat(val left: Any, val right: Any)
    // Cord = (String | Concat, Int)
    // Concat = (String | Concat, String | Concat)

    operator fun plus(that: Cord) =
        Cord(
            Concat(this.value, that.value),
            this.length + that.length,
            max(this.depth + 1, that.depth)
        )

    operator fun plus(that: String) =
        append(that)

    fun prepend(s: String): Cord =
        Cord(
            Concat(s, this.value),
            s.length + this.length,
            this.depth
        )

    fun append(s: String): Cord =
        Cord(
            Concat(this.value, s),
            this.length + s.length,
            this.depth + 1
        )

    override fun toString(): String {
        val rights = arrayOfNulls<Any>(this.depth)
        val out = CharArray(this.length)
        unsafeAppendToH(rights, out, this.value)
        return String(out)
    }

    companion object {
        val empty: Cord = Cord("", 0, 1)
        fun of(value: Char): Cord = Cord(value, 1, 1)
        fun of(value: String): Cord = Cord(value, value.length, 1)

        fun join(sep: String, args: List<Cord>) =
            args.fold(empty) { acc, arg ->
                if (acc.length == 0) arg
                else acc + sep + arg
            }

        private fun unsafeAppendToH(rights: Array<Any?>, out: CharArray, cord: Any) {
            var current: Any? = cord
            var stackPtr  = 0
            var outputPtr = 0

            while (current != null) {
                if (current.javaClass === java.lang.Character::class.java) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    val s = current as Char
                    out[outputPtr++] = s

                    if (stackPtr > 0) {
                        stackPtr -= 1
                        current = rights[stackPtr]
                    } else {
                        current = null
                    }
                }
                else if (current.javaClass === String::class.java) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    val s = current as java.lang.String
                    s.getChars(0, s.length, out, outputPtr)
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
