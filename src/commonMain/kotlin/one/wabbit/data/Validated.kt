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

    fun <E1, A1> bimap(f: (E) -> E1, g: (A) -> A1): Validated<E1, A1> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(g(value), issues.map(f))
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

        //        fun <A, E, B> Validated<E, A>.map(f: (A) -> B): Validated<E, B> =
        //            when (this) {
        //                is Fail -> this
        //                is Success -> Success(f(value))
        //            }

        fun <A, B, E> Validated<E, A>.flatMap(f: (A) -> Validated<E, B>): Validated<E, B> =
            when (this) {
                is Fail -> Fail(issues)
                is Success ->
                    when (val fv = f(value)) {
                        is Fail -> fv
                        is Success<E, B> -> Success(fv.value, issues + fv.issues)
                    }
            }

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
                            is Success -> value.value
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

private class AbortValidation : CancellationException("Validated.run aborted") {
    override fun fillInStackTrace(): Throwable = this
}
