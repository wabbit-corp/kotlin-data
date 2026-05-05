// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PrimitiveDequeBulkImplementationTest {
    @Test
    fun `generated primitive deque bulk methods use bulk copy helpers`() {
        listOf("Boolean", "Byte", "Char", "Double", "Float", "Int", "Long", "Short").forEach { type
            ->
            val source = dequeSource(type)
            assertContains(source, "private fun copyLinearIntoRing(", false)
            assertContains(source, "private fun copyRingIntoLinear(", false)
            assertContains(source, "private fun copyRingIntoRing(", false)

            assertUsesBulkCopy(source, "fun pushLast(values: ${type}Array)", "copyLinearIntoRing(")
            assertUsesBulkCopy(source, "fun pushLast(values: ${type}Deque)", "copyRingIntoRing(")
            assertUsesBulkCopy(source, "fun pushFirst(values: ${type}Array)", "copyLinearIntoRing(")
            assertUsesBulkCopy(source, "fun pushFirst(values: ${type}Deque)", "copyRingIntoRing(")
            assertUsesBulkCopy(
                source,
                "fun popLast(count: Int): ${type}Array",
                "copyRingIntoLinear(",
            )
            assertUsesBulkCopy(
                source,
                "fun popFirst(count: Int): ${type}Array",
                "copyRingIntoLinear(",
            )
        }
    }

    private fun dequeSource(type: String): String =
        Path.of(
                System.getProperty("user.dir"),
                "src",
                "commonMain",
                "kotlin",
                "one",
                "wabbit",
                "data",
                "${type}Deque.kt",
            )
            .readText()

    private fun assertUsesBulkCopy(source: String, signature: String, helperCall: String) {
        val body = methodBody(source, signature)
        assertContains(body, helperCall, false, "Expected `$signature` to call `$helperCall`.")
        assertFalse(
            body.contains("for ("),
            "Expected `$signature` to avoid element-by-element loops.",
        )
    }

    private fun methodBody(source: String, signature: String): String {
        val signatureIndex = source.indexOf(signature)
        check(signatureIndex >= 0) { "Missing method signature: $signature" }

        val bodyStart = source.indexOf('{', signatureIndex)
        check(bodyStart >= 0) { "Missing body start for: $signature" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(bodyStart + 1, index)
                    }
                }
            }
        }

        error("Unterminated method body for: $signature")
    }
}
