package one.wabbit.data

@JvmInline
value class NonEmptyList<out A> private constructor(val value: List<A>) : List<A> {
    override val size: Int
        get() = value.size

    override fun get(index: Int): A = value[index]

    override fun isEmpty(): Boolean = false

    override fun iterator(): Iterator<A> = value.iterator()

    override fun listIterator(): ListIterator<A> = value.listIterator()

    override fun listIterator(index: Int): ListIterator<A> = value.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<A> = value.subList(fromIndex, toIndex)

    override fun lastIndexOf(element: @UnsafeVariance A): Int = value.lastIndexOf(element)

    override fun indexOf(element: @UnsafeVariance A): Int = value.indexOf(element)

    override fun containsAll(elements: Collection<@UnsafeVariance A>): Boolean =
        value.containsAll(elements)

    override fun contains(element: @UnsafeVariance A): Boolean = value.contains(element)

    fun <B> map(transform: (A) -> B): NonEmptyList<B> = NonEmptyList(value.map(transform))

    companion object {
        fun <A> of(first: A, vararg rest: A): NonEmptyList<A> =
            NonEmptyList(listOf(first) + rest.toList())

        @JvmStatic
        fun <A> fromListOrThrow(value: List<A>): NonEmptyList<A> {
            if (value.isEmpty()) throw IllegalArgumentException("List must not be empty")
            return NonEmptyList(value)
        }

        @JvmStatic
        fun <A> fromListOrNull(value: List<A>): NonEmptyList<A>? =
            if (value.isEmpty()) null else NonEmptyList(value)
    }
}
