@file:OptIn(ExperimentalContracts::class)

package one.wabbit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Serializable @SerialName("Left") data class Left<out E>(val value: E) : Either<E, Nothing>()

@Serializable @SerialName("Right") data class Right<out A>(val value: A) : Either<Nothing, A>()

@Serializable
sealed class Either<out E, out A> {
    val isLeft: Boolean inline get() = this is Left

    val isRight: Boolean inline get() = this is Right

    val rightOrNull: A? inline get() = when (this) {
        is Left -> null
        is Right -> value
    }

    val leftOrNull: E? inline get() = when (this) {
        is Left -> value
        is Right -> null
    }

    inline fun rightOr(block: () -> @UnsafeVariance A): A? =
        when (this) {
            is Left -> block()
            is Right -> value
        }

    inline fun leftOr(block: () -> @UnsafeVariance E): E? =
        when (this) {
            is Left -> value
            is Right -> block()
        }

    @Throws(IllegalStateException::class)
    fun rightOrThrow(): A =
        when (this) {
            is Left -> throw IllegalStateException("Called getRightOrThrow on a Left value: $value")
            is Right -> value
        }

    @Throws(IllegalStateException::class)
    fun leftOrThrow(): E =
        when (this) {
            is Left -> value
            is Right -> throw IllegalStateException("Called getLeftOrThrow on a Right value: $value")
        }

    fun <B> map(f: (A) -> B): Either<E, B> =
        when (this) {
            is Left -> this
            is Right -> Right(f(value))
        }

    fun <E1> mapLeft(f: (E) -> E1): Either<E1, A> =
        when (this) {
            is Left -> Left(f(value))
            is Right -> this
        }

    fun <B> flatMap(f: (A) -> Either<@UnsafeVariance E, B>): Either<E, B> =
        when (this) {
            is Left -> this
            is Right -> f(value)
        }

    fun swap(): Either<A, E> =
        when (this) {
            is Left -> Right(value)
            is Right -> Left(value)
        }

    fun <R> fold(onLeft: (E) -> R, onRight: (A) -> R): R =
        when (this) {
            is Left -> onLeft(value)
            is Right -> onRight(value)
        }

    companion object {
        fun <E> left(value: E): Either<E, Nothing> = Left(value)
        fun <A> right(value: A): Either<Nothing, A> = Right(value)
    }
}

//suspend fun <T> catchAll(block: suspend () -> T): Either<Throwable, T> {
//    try {
//        val r = block()
//        return Right(r)
//    } catch (t: Exception) {
//        return Left(t)
//    }
//}
