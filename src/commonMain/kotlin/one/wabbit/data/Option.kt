// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Optional value that is either [Some] with a value or [None] with no value.
 *
 * Use [of] to convert nullable values into [Option] and [orNull] to convert back to nullable Kotlin
 * values at API boundaries.
 */
@Serializable
sealed class Option<out A> {
    /** Returns `true` when this option is [None]. */
    fun isEmpty(): Boolean = this is None

    /** Returns `true` when this option is [Some]. */
    fun isNotEmpty(): Boolean = this is Some<A>

    /** Transform the present value with [f], or preserve [None]. */
    fun <B> map(f: (A) -> B): Option<B> =
        when (this) {
            is None -> None
            is Some -> Some(f(this.value))
        }

    /** Transform the present value with [f] and flatten the returned option. */
    fun <B> flatMap(f: (A) -> Option<B>): Option<B> =
        when (this) {
            is None -> None
            is Some -> f(this.value)
        }

    /** Return the present value, or evaluate [block] when this option is [None]. */
    fun getOrElse(block: () -> @UnsafeVariance A): A =
        when (this) {
            is None -> block()
            is Some -> value
        }

    /** Return this option when present, or evaluate [block] when this option is [None]. */
    fun orElse(block: () -> Option<@UnsafeVariance A>): Option<A> =
        when (this) {
            is None -> block()
            is Some -> this
        }

    /** Fold both option cases into one value. */
    fun <B> fold(ifEmpty: () -> B, ifSome: (A) -> B): B =
        when (this) {
            is None -> ifEmpty()
            is Some -> ifSome(value)
        }

    /** Convert this option to a nullable Kotlin value. */
    fun orNull(): A? =
        when (this) {
            is None -> null
            is Some -> this.value
        }

    /** Return an empty list for [None] or a singleton list for [Some]. */
    fun toList(): List<A> =
        when (this) {
            is None -> emptyList()
            is Some -> listOf(this.value)
        }

    /** Return the present value or throw [NoSuchElementException] when this option is [None]. */
    fun unsafeGet(): A =
        when (this) {
            is None -> throw NoSuchElementException("None.unsafeGet")
            is Some -> this.value
        }

    /** Factories for optional values. */
    companion object {
        /** Singleton empty option value. */
        val none: Option<Nothing> = None

        /** Return [None] when [value] is null, otherwise wrap it in [Some]. */
        fun <A : Any> of(value: A?): Option<A> = if (value == null) None else Some(value)
    }
}

/** Present [Option] value. */
@Serializable @SerialName("Some") data class Some<A>(val value: A) : Option<A>()

/** Empty [Option] value. */
@Serializable @SerialName("None") data object None : Option<Nothing>()
