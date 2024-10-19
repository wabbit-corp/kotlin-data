package one.wabbit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed interface Option<out A> {
    fun <B> map(f: (A) -> B): Option<B> = when (this) {
        is None -> None
        is Some -> Some(f(this.value))
    }

    fun unsafeGet(): A = when (this) {
        is None -> throw NoSuchElementException("None.unsafeGet")
        is Some -> this.value
    }

    companion object {
        fun <A : Any> of(value: A?): Option<A> = if (value == null) None else Some(value)

        val none: Option<Nothing> = None
    }
}

@Serializable @SerialName("Some")
data class Some<A>(val value: A) : Option<A>
@Serializable @SerialName("None")
data object None : Option<Nothing>
