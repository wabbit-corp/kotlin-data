// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

@file:OptIn(InternalDataApi::class)

package one.wabbit.data

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class InternalDataApiSpec {
    @Test
    fun `core collection constructors are not public`() {
        assertFalse(
            BankersQueue::class.java.sourceVisibleConstructors().any { it.isPubliclyAccessible() }
        )
        assertFalse(Chain::class.java.sourceVisibleConstructors().any { it.isPubliclyAccessible() })
        assertFalse(Cord::class.java.sourceVisibleConstructors().any { it.isPubliclyAccessible() })
        assertFalse(
            ArrMap::class.java.sourceVisibleConstructors().any { it.isPubliclyAccessible() }
        )
    }

    @Test
    fun `raw representation nodes require internal opt in`() {
        assertInternalDataApi(LeftistHeap.Node::class.java)
        assertInternalDataApi(Chunk.ArrayChunk::class.java)
        assertInternalDataApi(Chunk.BooleanArrayChunk::class.java)
        assertInternalDataApi(Chunk.ByteArrayChunk::class.java)
        assertInternalDataApi(Chunk.Concat::class.java)
        assertInternalDataApi(Chunk.DoubleArrayChunk::class.java)
        assertInternalDataApi(Chunk.FloatArrayChunk::class.java)
        assertInternalDataApi(Chunk.IntArrayChunk::class.java)
        assertInternalDataApi(Chunk.LongArrayChunk::class.java)
        assertInternalDataApi(Chunk.ShortArrayChunk::class.java)
        assertInternalDataApi(Chunk.Single::class.java)
        assertInternalDataApi(Chunk.Slice::class.java)
        assertInternalDataApi(Chunk.StringChunk::class.java)
    }

    @Test
    fun `representation carriers do not expose data class copy`() {
        assertFalse(BankersQueue::class.java.declaredMethods.any { it.name == "copy" })
        assertFalse(LeftistHeap.Node::class.java.declaredMethods.any { it.name == "copy" })
    }

    private fun assertInternalDataApi(type: Class<*>) {
        val onType = type.getAnnotation(InternalDataApi::class.java)
        val onConstructor =
            type.declaredConstructors
                .flatMap { it.annotations.toList() }
                .firstOrNull { it.annotationClass == InternalDataApi::class }
        assertNotNull(onType ?: onConstructor)
    }

    private fun java.lang.reflect.Constructor<*>.isPubliclyAccessible(): Boolean =
        Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)

    private fun Class<*>.sourceVisibleConstructors(): List<java.lang.reflect.Constructor<*>> =
        declaredConstructors.filterNot { constructor ->
            constructor.isSynthetic ||
                constructor.parameterTypes.lastOrNull()?.name ==
                    "kotlin.jvm.internal.DefaultConstructorMarker"
        }
}
