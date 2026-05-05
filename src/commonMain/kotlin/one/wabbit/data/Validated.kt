// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable

/**
 * Validation result that can accumulate issues while still carrying a successful value.
 *
 * [Fail] stores one or more issues with no value. [Success] stores a value and may still carry
 * non-fatal issues such as warnings.
 */
@Serializable
sealed class Validated<out E, out A> {
    /** Issues accumulated during validation. */
    abstract val issues: List<E>

    /** Failed validation with [issues]. */
    @Serializable data class Fail<out E>(override val issues: List<E>) : Validated<E, Nothing>()

    /** Successful validation with [value] and accumulated [issues]. */
    @Serializable
    data class Success<out E, out A>(val value: A, override val issues: List<E>) : Validated<E, A>()

    /** Transform all issues with [f] while preserving validation state. */
    fun <E1> mapError(f: (E) -> E1): Validated<E1, A> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(value, issues.map(f))
        }

    /** Transform a successful value with [f], preserving accumulated issues. */
    fun <A1> map(f: (A) -> A1): Validated<E, A1> =
        when (this) {
            is Fail -> Fail(issues)
            is Success -> Success(f(value), issues)
        }

    /** Transform a successful value with [f] and append issues from both validation results. */
    fun <B> flatMap(f: (A) -> Validated<@UnsafeVariance E, B>): Validated<E, B> =
        when (this) {
            is Fail -> Fail(issues)
            is Success ->
                when (val fv = f(value)) {
                    is Fail -> Fail(issues + fv.issues)
                    is Success<E, B> -> Success(fv.value, issues + fv.issues)
                }
        }

    /** Transform issues and successful values at the same time. */
    fun <E1, A1> bimap(f: (E) -> E1, g: (A) -> A1): Validated<E1, A1> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(g(value), issues.map(f))
        }

    /** Return the successful value or evaluate [block] when this validation failed. */
    inline fun getOrElse(block: () -> @UnsafeVariance A): A =
        when (this) {
            is Fail -> block()
            is Success -> value
        }

    /** Fold failed and successful validation states into one value. */
    inline fun <R> fold(onFail: (List<E>) -> R, onSuccess: (A, List<E>) -> R): R =
        when (this) {
            is Fail -> onFail(issues)
            is Success -> onSuccess(value, issues)
        }

    /** Combine this validation with [that], accumulating issues from both sides. */
    fun <B> zip(that: Validated<@UnsafeVariance E, B>): Validated<E, Pair<A, B>> =
        zipWith(that) { left, right -> left to right }

    /** Combine this validation with [that] and map successful values with [combine]. */
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

    /** Factories, combinators, and builder-style validation support. */
    companion object {
        /** Create a failed validation from [issues]. */
        fun <E> fail(issues: List<E>): Validated<E, Nothing> = Fail(issues)

        /** Create a successful validation with non-fatal [issues]. */
        fun <E, A> succeed(value: A, issues: List<E>): Validated<E, A> = Success(value, issues)

        /** Create a successful validation without issues. */
        fun <A> succeed(value: A): Validated<Nothing, A> = Success(value, emptyList())

        /** Convert nullable [value] into validation, raising [ifNull] when it is null. */
        fun <E, A> fromNullable(value: A?, ifNull: () -> E): Validated<E, A> =
            when (value) {
                null -> fail(listOf(ifNull()))
                else -> succeed(value)
            }

        /** Combine two validations and map successful values with [combine]. */
        fun <E, A, B, C> map2(
            left: Validated<E, A>,
            right: Validated<E, B>,
            combine: (A, B) -> C,
        ): Validated<E, C> = left.zipWith(right, combine)

        /** Receiver used by [run] to accumulate validation issues before aborting. */
        interface Builder<Issue> {
            /** Record a non-fatal [issue]. */
            fun raise(issue: Issue)

            /** Abort validation if any issue has been raised. */
            fun failIfRaised()

            /** Abort validation immediately. */
            fun fail(): Nothing

            /** Raise [issue] and abort validation. */
            fun fail(issue: Issue): Nothing {
                raise(issue)
                fail()
            }

            /** Raise all [issues] and abort validation. */
            fun fail(vararg issues: Issue): Nothing {
                issues.forEach { raise(it) }
                fail()
            }

            /**
             * Extract a successful validation value, accumulating issues or aborting on failure.
             */
            fun <A> lift(value: Validated<Issue, A>): A
        }

        /** Run validation code that can accumulate issues and abort through [Builder]. */
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
