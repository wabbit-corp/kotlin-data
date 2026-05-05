// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(ExperimentalContracts::class)

package one.wabbit.data

import kotlin.contracts.ExperimentalContracts
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Left side of [Either], conventionally used for errors or alternate outcomes. */
@Serializable @SerialName("Left") data class Left<out E>(val value: E) : Either<E, Nothing>()

/** Right side of [Either], conventionally used for successful values. */
@Serializable @SerialName("Right") data class Right<out A>(val value: A) : Either<Nothing, A>()

/**
 * Value that is either [Left] with an `E` value or [Right] with an `A` value.
 *
 * The right side is the success-biased side for [map] and [flatMap].
 */
@Serializable
sealed class Either<out E, out A> {
    /** Returns `true` when this value is [Left]. */
    val isLeft: Boolean
        inline get() = this is Left

    /** Returns `true` when this value is [Right]. */
    val isRight: Boolean
        inline get() = this is Right

    /** Right value, or null when this is [Left]. */
    val rightOrNull: A?
        inline get() =
            when (this) {
                is Left -> null
                is Right -> value
            }

    /** Left value, or null when this is [Right]. */
    val leftOrNull: E?
        inline get() =
            when (this) {
                is Left -> value
                is Right -> null
            }

    /** Return the right value or evaluate [block] when this is [Left]. */
    inline fun getOrElse(block: () -> @UnsafeVariance A): A =
        when (this) {
            is Left -> block()
            is Right -> value
        }

    /** Return the left value or evaluate [block] when this is [Right]. */
    inline fun getLeftOrElse(block: () -> @UnsafeVariance E): E =
        when (this) {
            is Left -> value
            is Right -> block()
        }

    /** Alias for [getOrElse]. */
    inline fun rightOr(block: () -> @UnsafeVariance A): A = getOrElse(block)

    /** Alias for [getLeftOrElse]. */
    inline fun leftOr(block: () -> @UnsafeVariance E): E = getLeftOrElse(block)

    /** Return the right value or throw [IllegalStateException] when this is [Left]. */
    @Throws(IllegalStateException::class)
    fun rightOrThrow(): A =
        when (this) {
            is Left -> throw IllegalStateException("Called getRightOrThrow on a Left value: $value")
            is Right -> value
        }

    /** Return the left value or throw [IllegalStateException] when this is [Right]. */
    @Throws(IllegalStateException::class)
    fun leftOrThrow(): E =
        when (this) {
            is Left -> value
            is Right ->
                throw IllegalStateException("Called getLeftOrThrow on a Right value: $value")
        }

    /** Transform the right value with [f], preserving [Left]. */
    fun <B> map(f: (A) -> B): Either<E, B> =
        when (this) {
            is Left -> this
            is Right -> Right(f(value))
        }

    /** Transform the left value with [f], preserving [Right]. */
    fun <E1> mapLeft(f: (E) -> E1): Either<E1, A> =
        when (this) {
            is Left -> Left(f(value))
            is Right -> this
        }

    /** Transform the right value with [f] and flatten the returned [Either]. */
    fun <B> flatMap(f: (A) -> Either<@UnsafeVariance E, B>): Either<E, B> =
        when (this) {
            is Left -> this
            is Right -> f(value)
        }

    /** Swap [Left] and [Right] sides. */
    fun swap(): Either<A, E> =
        when (this) {
            is Left -> Right(value)
            is Right -> Left(value)
        }

    /** Fold both either cases into one value. */
    fun <R> fold(onLeft: (E) -> R, onRight: (A) -> R): R =
        when (this) {
            is Left -> onLeft(value)
            is Right -> onRight(value)
        }

    /** Factories for [Either] values. */
    companion object {
        /** Create a [Left] value. */
        fun <E> left(value: E): Either<E, Nothing> = Left(value)

        /** Create a [Right] value. */
        fun <A> right(value: A): Either<Nothing, A> = Right(value)
    }
}

// suspend fun <T> catchAll(block: suspend () -> T): Either<Throwable, T> {
//    try {
//        val r = block()
//        return Right(r)
//    } catch (t: Exception) {
//        return Left(t)
//    }
// }
