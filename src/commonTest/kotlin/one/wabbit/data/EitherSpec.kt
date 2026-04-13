package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals

class EitherSpec {
    @Test
    fun `either provides Kotlin style defaulting aliases without unnecessary nullable types`() {
        val right: Either<String, Int> = Right(1)
        val left: Either<String, Int> = Left("boom")
        val rightValue: Int = right.rightOr { 2 }
        val leftValue: String = left.leftOr { "fallback" }

        assertEquals(1, rightValue)
        assertEquals("boom", leftValue)

        assertEquals(1, right.getOrElse { 2 })
        assertEquals(2, left.getOrElse { 2 })
        assertEquals("boom", left.getLeftOrElse { "fallback" })
        assertEquals("fallback", right.getLeftOrElse { "fallback" })
    }
}
