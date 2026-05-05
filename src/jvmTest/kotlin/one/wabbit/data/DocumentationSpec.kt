// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class DocumentationSpec {
    @Test
    fun `arr complexity docs describe first and last access as constant time`() {
        val source = sourceOf("Arr.kt")

        assertContains(
            source,
            "* - indexed reads, [first], [last], [firstOrNull], and [lastOrNull] are O(1)",
            false,
        )
        assertContains(source, "* - [contains], [indexOf], and [lastIndexOf] are O(n)", false)
    }

    @Test
    fun `primitive buffer binarySearch docs require sorted contents`() {
        listOf("Byte", "Char", "Double", "Float", "Int", "Long", "Short").forEach { type ->
            val source = sourceOf("${type}Buffer.kt")
            assertContains(
                source,
                "Requires the current contents to already be sorted in ascending order according to compareTo.",
                false,
                "Expected ${type}Buffer.kt to document the binarySearch sortedness precondition.",
            )
        }
    }

    private fun sourceOf(fileName: String): String =
        Path.of(
                System.getProperty("user.dir"),
                "src",
                "commonMain",
                "kotlin",
                "one",
                "wabbit",
                "data",
                fileName,
            )
            .readText()
}
