// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationSpec {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `bankers queue serialization is based on logical contents`() {
        val left = BankersQueue.fromConsList(consListOf(1, 2)).enqueue(3)
        val right = BankersQueue.empty<Int>().enqueue(1).enqueue(2).enqueue(3)

        val leftJson = json.encodeToString<BankersQueue<Int>>(left)
        val rightJson = json.encodeToString<BankersQueue<Int>>(right)

        assertEquals("[1,2,3]", leftJson)
        assertEquals(leftJson, rightJson)
        assertEquals(left, json.decodeFromString<BankersQueue<Int>>(leftJson))
    }

    @Test
    fun `leftist heap serialization is based on logical contents`() {
        val left = LeftistHeap.of(3, 1, 5, 2)
        val right = LeftistHeap.of(2, 5, 1, 3)

        val leftJson = json.encodeToString<LeftistHeap<Int>>(left)
        val rightJson = json.encodeToString<LeftistHeap<Int>>(right)

        assertEquals("[1,2,3,5]", leftJson)
        assertEquals(leftJson, rightJson)
        assertEquals(left, json.decodeFromString<LeftistHeap<Int>>(leftJson))
    }
}
