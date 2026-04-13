package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidatedSpec {
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
