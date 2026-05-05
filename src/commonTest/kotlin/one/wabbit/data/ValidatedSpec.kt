// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidatedSpec {
    @Test
    fun `validated fold and defaulting helpers align with the functional family`() {
        val success = Validated.succeed(1, listOf("warn"))
        val failure: Validated<String, Int> = Validated.fail(listOf("boom"))

        assertEquals(1, success.getOrElse { 2 })
        assertEquals(2, failure.getOrElse { 2 })

        assertEquals(
            "ok:1/warn",
            success.fold({ "fail:${it.joinToString()}" }) { value, issues ->
                "ok:$value/${issues.joinToString()}"
            },
        )
        assertEquals(
            "fail:boom",
            failure.fold({ "fail:${it.joinToString()}" }) { value, issues ->
                "ok:$value/${issues.joinToString()}"
            },
        )
    }

    @Test
    fun `validated zipWith accumulates issues applicatively`() {
        val left = Validated.succeed(1, listOf("warn-left"))
        val right = Validated.succeed(2, listOf("warn-right"))
        val failed: Validated<String, Int> = Validated.fail(listOf("boom"))

        assertEquals(
            Validated.Success(3, listOf("warn-left", "warn-right")),
            left.zipWith(right) { a, b -> a + b },
        )
        assertEquals(
            Validated.Fail(listOf("warn-left", "boom")),
            left.zipWith(failed) { a, b: Int -> a + b },
        )
        assertEquals(
            Validated.Fail(listOf("boom", "boom-2")),
            (Validated.fail<String>(listOf("boom")) as Validated<String, Int>).zipWith(
                Validated.fail(listOf("boom-2")) as Validated<String, Int>
            ) { a: Int, b: Int ->
                a + b
            },
        )
    }

    @Test
    fun `validated flatMap and lift preserve accumulated success issues`() {
        val flatMapped =
            Validated.succeed(1, listOf("warn-left")).flatMap {
                Validated.fail<String>(listOf("boom")) as Validated<String, Int>
            }

        assertEquals(Validated.Fail(listOf("warn-left", "boom")), flatMapped)

        val lifted =
            Validated.run<String, Int> {
                val value = lift(Validated.succeed(41, listOf("warn")))
                value + 1
            }

        assertEquals(Validated.Success(42, listOf("warn")), lifted)
    }

    @Test
    fun `fail runs finally cleanup before returning failure`() {
        val events = mutableListOf<String>()

        val result =
            Validated.run<String, Int> {
                try {
                    events += "start"
                    fail("boom")
                } finally {
                    events += "cleanup"
                }
            }

        assertIs<Validated.Fail<String>>(result)
        assertEquals(listOf("start", "cleanup"), events)
        assertEquals(listOf("boom"), result.issues)
    }

    @Test
    fun `failIfRaised runs finally cleanup before aborting`() {
        val events = mutableListOf<String>()

        val result =
            Validated.run<String, Int> {
                raise("warn")
                try {
                    events += "before-check"
                    failIfRaised()
                } finally {
                    events += "cleanup"
                }
                1
            }

        assertIs<Validated.Fail<String>>(result)
        assertEquals(listOf("before-check", "cleanup"), events)
        assertEquals(listOf("warn"), result.issues)
    }

    @Test
    fun `lift failure runs finally cleanup before aborting`() {
        val events = mutableListOf<String>()

        val result =
            Validated.run<String, Int> {
                try {
                    events += "before-lift"
                    lift(Validated.fail(listOf("boom")))
                } finally {
                    events += "cleanup"
                }
            }

        assertIs<Validated.Fail<String>>(result)
        assertEquals(listOf("before-lift", "cleanup"), events)
        assertEquals(listOf("boom"), result.issues)
    }

    @Test
    fun `successful run returns value and accumulated issues`() {
        val result =
            Validated.run<String, Int> {
                raise("warn")
                42
            }

        assertEquals(Validated.Success(42, listOf("warn")), result)
    }
}
