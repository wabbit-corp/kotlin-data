package one.wabbit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed interface Option<out A> {
    fun isEmpty(): Boolean = this is None
    fun isNotEmpty(): Boolean = this is Some<A>

    fun <B> map(f: (A) -> B): Option<B> = when (this) {
        is None -> None
        is Some -> Some(f(this.value))
    }

    fun <B> flatMap(f: (A) -> Option<B>): Option<B> = when (this) {
        is None -> None
        is Some -> f(this.value)
    }

    fun orNull(): A? = when (this) {
        is None -> null
        is Some -> this.value
    }

    fun toList(): List<A> = when (this) {
        is None -> emptyList()
        is Some -> listOf(this.value)
    }

    fun unsafeGet(): A = when (this) {
        is None -> throw NoSuchElementException("None.unsafeGet")
        is Some -> this.value
    }

    companion object {
        val none: Option<Nothing> = None

        fun <A : Any> of(value: A?): Option<A> =
            if (value == null) None else Some(value)

    }
}

@Serializable @SerialName("Some") data class Some<A>(val value: A) : Option<A>
@Serializable @SerialName("None") data object None : Option<Nothing>
