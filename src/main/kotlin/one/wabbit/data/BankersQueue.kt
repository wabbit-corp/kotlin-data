package one.wabbit.data

data class BankersQueue<A>(
    val ls: Int, val left: LazyConsList<A>,
    val rs: Int, val right: ConsList<A>
) {
    fun isEmpty(): Boolean = ls == 0

    fun snoc(x: @UnsafeVariance A): BankersQueue<A> =
        check(ls, left, rs + 1, right.cons(x))

    fun snocReversed(xs: ConsList<@UnsafeVariance A>): BankersQueue<A> =
        check(ls, left, rs + 1, xs + right)

    fun uncons(): Need<Pair<A, BankersQueue<A>>?> =
        left.thunk.map {
            when (it) {
                is LazyConsList.Nil -> null
                is LazyConsList.Cons -> it.head to check(ls - 1, it.tail, rs, right)
            }
        }

    companion object {
        fun <A> fromConsList(list: ConsList<A>): BankersQueue<A> =
            BankersQueue(list.size, list.toLazy(), 0, ConsList.Nil)

        fun <A> empty() = BankersQueue(0, LazyConsList.Nil, 0, ConsList.Nil)

        private fun <A> check(ls: Int, left: LazyConsList<A>, rs: Int, right: ConsList<A>): BankersQueue<A> =
            if (rs <= ls) BankersQueue(ls, left, rs, right)
            else BankersQueue(ls + rs, left + right.reverseLazy(), 0, ConsList.Nil)
    }
}
