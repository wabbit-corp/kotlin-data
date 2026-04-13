package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OptionSpec {
    @Test
    fun `option exposes fold and defaulting helpers`() {
        val some: Option<Int> = Some(1)
        val none: Option<Int> = None

        assertEquals(1, some.getOrElse { 2 })
        assertEquals(2, none.getOrElse { 2 })

        assertEquals(Some(1), some.orElse { Some(2) })
        assertEquals(Some(2), none.orElse { Some(2) })

        assertEquals("value=1", some.fold({ "empty" }) { "value=$it" })
        assertEquals("empty", none.fold({ "empty" }) { "value=$it" })
    }
}
