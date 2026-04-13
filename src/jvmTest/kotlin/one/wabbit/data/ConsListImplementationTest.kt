package one.wabbit.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse

class ConsListImplementationTest {
    @Test
    fun `cons list interop methods do not delegate through toList`() {
        val source =
            Path.of(
                System.getProperty("user.dir"),
                "src",
                "commonMain",
                "kotlin",
                "one",
                "wabbit",
                "data",
                "ConsList.kt",
            ).readText()

        assertFalse(methodBody(source, "override fun listIterator(): ListIterator<V>").contains("toList()"))
        assertFalse(methodBody(source, "override fun listIterator(index: Int): ListIterator<V>").contains("toList()"))
        assertFalse(methodBody(source, "override fun subList(fromIndex: Int, toIndex: Int): List<V>").contains("toList()"))
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
