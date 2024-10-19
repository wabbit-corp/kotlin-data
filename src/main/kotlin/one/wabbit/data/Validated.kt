package one.wabbit.data

import kotlinx.serialization.Serializable
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Serializable sealed class Validated<out E, out A> {
    abstract val issues: List<E>

    @Serializable data class Fail<out E>(override val issues: List<E>) : Validated<E, Nothing>()
    @Serializable data class Success<out E, out A>(val value: A, override val issues: List<E>) : Validated<E, A>()

    fun <E1> mapError(f: (E) -> E1): Validated<E1, A> =
        when (this) {
            is Fail -> Fail(issues.map(f))
            is Success -> Success(value, issues.map(f))
        }

    fun <A1> map(f: (A) -> A1): Validated<E, A1> =
        when (this) {
            is Fail -> this
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
                is Fail -> this
                is Success -> when (val fv = f(value)) {
                    is Fail -> fv
                    is Success<E, B> -> Success(fv.value, issues + fv.issues)
                }
            }

        interface Builder<Issue> {
            suspend fun raise(issue: Issue): Unit
            suspend fun failIfRaised(): Unit
            suspend fun fail(): Nothing
            suspend fun fail(issue: Issue): Nothing {
                raise(issue)
                fail()
            }
            suspend fun fail(vararg issues: Issue): Nothing {
                issues.forEach { raise(it) }
                fail()
            }
            suspend fun <A> lift(value: Validated<Issue, A>): A
        }

        fun <Issue, Result> run(f: suspend Builder<Issue>.() -> Result): Validated<Issue, Result> {
            val issues = mutableListOf<Issue>()
            var failed: Boolean = false
            var resultVar: kotlin.Result<Result> = kotlin.Result.failure(Exception("Result not set"))

            val builder = object : Builder<Issue> {
                override suspend fun raise(issue: Issue) {
                    issues += issue
                }
                override suspend fun failIfRaised(): Unit = suspendCoroutine { cont ->
                    if (issues.isNotEmpty()) {
                        failed = true
                        return@suspendCoroutine Unit
                    } else cont.resume(Unit)
                }
                override suspend fun fail(): Nothing = suspendCoroutine { cont ->
                    failed = true
                    return@suspendCoroutine Unit
                }
                override suspend fun <A> lift(value: Validated<Issue, A>): A = suspendCoroutine {
                    when (value) {
                        is Success -> it.resume(value.value)
                        is Fail -> {
                            issues += value.issues
                            failed = true
                            return@suspendCoroutine Unit
                        }
                    }
                }
            }

            val store = object : Continuation<Result> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: kotlin.Result<Result>) {
                    resultVar = result
                }
            }

            f.createCoroutineUnintercepted(builder, store).resume(Unit)

            return when {
                failed -> Fail(issues)
                else -> Success(resultVar.getOrThrow(), issues)
            }
        }
    }
}
