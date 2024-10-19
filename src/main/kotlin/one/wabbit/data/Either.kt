package one.wabbit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("Left")
data class Left<out E>(val value: E) : Either<E, Nothing>
@Serializable @SerialName("Right")
data class Right<out A>(val value: A) : Either<Nothing, A>

@Serializable
sealed interface Either<out E, out A> {
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
}
