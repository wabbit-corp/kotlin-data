package one.wabbit.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable

@Serializable
sealed class Validated<out E, out A> {
    abstract val issues: List<E>

    @Serializable data class Fail<out E>(override val issues: List<E>) : Validated<E, Nothing>()

    @Serializable
    data class Success<out E, out A>(val value: A, override val issues: List<E>) : Validated<E, A>()

    fun <E1> mapError(f: (E) -> E1): Validated<E1, A> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(value, issues.map(f))
        }

    fun <A1> map(f: (A) -> A1): Validated<E, A1> =
        when (this) {
            is Fail -> Fail(issues)
            is Success -> Success(f(value), issues)
        }

    fun <B> flatMap(f: (A) -> Validated<@UnsafeVariance E, B>): Validated<E, B> =
        when (this) {
            is Fail -> Fail(issues)
            is Success ->
                when (val fv = f(value)) {
                    is Fail -> Fail(issues + fv.issues)
                    is Success<E, B> -> Success(fv.value, issues + fv.issues)
                }
        }

    fun <E1, A1> bimap(f: (E) -> E1, g: (A) -> A1): Validated<E1, A1> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(g(value), issues.map(f))
        }

    inline fun getOrElse(block: () -> @UnsafeVariance A): A =
        when (this) {
            is Fail -> block()
            is Success -> value
        }

    inline fun <R> fold(onFail: (List<E>) -> R, onSuccess: (A, List<E>) -> R): R =
        when (this) {
            is Fail -> onFail(issues)
            is Success -> onSuccess(value, issues)
        }

    fun <B> zip(that: Validated<@UnsafeVariance E, B>): Validated<E, Pair<A, B>> =
        zipWith(that) { left, right -> left to right }

    fun <B, C> zipWith(
        that: Validated<@UnsafeVariance E, B>,
        combine: (A, B) -> C,
    ): Validated<E, C> =
        when (this) {
            is Fail ->
                when (that) {
                    is Fail -> Fail(issues + that.issues)
                    is Success -> Fail(issues + that.issues)
                }
            is Success ->
                when (that) {
                    is Fail -> Fail(issues + that.issues)
                    is Success -> Success(combine(value, that.value), issues + that.issues)
                }
        }

    companion object {
        fun <E> fail(issues: List<E>): Validated<E, Nothing> = Fail(issues)

        fun <E, A> succeed(value: A, issues: List<E>): Validated<E, A> = Success(value, issues)

        fun <A> succeed(value: A): Validated<Nothing, A> = Success(value, emptyList())

        fun <E, A> fromNullable(value: A?, ifNull: () -> E): Validated<E, A> =
            when (value) {
                null -> fail(listOf(ifNull()))
                else -> succeed(value)
            }

        fun <E, A, B, C> map2(
            left: Validated<E, A>,
            right: Validated<E, B>,
            combine: (A, B) -> C,
        ): Validated<E, C> = left.zipWith(right, combine)

        interface Builder<Issue> {
            fun raise(issue: Issue)

            fun failIfRaised()

            fun fail(): Nothing

            fun fail(issue: Issue): Nothing {
                raise(issue)
                fail()
            }

            fun fail(vararg issues: Issue): Nothing {
                issues.forEach { raise(it) }
                fail()
            }

            fun <A> lift(value: Validated<Issue, A>): A
        }

        fun <Issue, Result> run(f: Builder<Issue>.() -> Result): Validated<Issue, Result> {
            val issues = mutableListOf<Issue>()

            val builder =
                object : Builder<Issue> {
                    override fun raise(issue: Issue) {
                        issues += issue
                    }

                    override fun failIfRaised() {
                        if (issues.isNotEmpty()) {
                            throw AbortValidation()
                        }
                    }

                    override fun fail(): Nothing = throw AbortValidation()

                    override fun <A> lift(value: Validated<Issue, A>): A =
                        when (value) {
                            is Success -> {
                                issues += value.issues
                                value.value
                            }
                            is Fail -> {
                                issues += value.issues
                                throw AbortValidation()
                            }
                        }
                }

            return try {
                Success(f(builder), issues)
            } catch (_: AbortValidation) {
                Fail(issues)
            }
        }
    }
}

private class AbortValidation : CancellationException("Validated.run aborted")
