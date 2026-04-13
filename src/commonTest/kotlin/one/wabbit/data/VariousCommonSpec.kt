// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlin.test.Test
import kotlin.test.assertEquals

class VariousCommonSpec {
    @Test
    fun `base16 formats bytes as unsigned two digit hex`() {
        assertEquals("00", 0.toByte().base16())
        assertEquals("0f", 15.toByte().base16())
        assertEquals("10", 16.toByte().base16())
        assertEquals("ff", (-1).toByte().base16())
        assertEquals("80", (-128).toByte().base16())
    }

    @Test
    fun `byte array base16 uses unsigned byte formatting`() {
        assertEquals("00ff80", byteArrayOf(0, (-1).toByte(), (-128).toByte()).base16())
        assertEquals("ff80", byteArrayOf(0, (-1).toByte(), (-128).toByte()).base16(from = 1))
    }
}
