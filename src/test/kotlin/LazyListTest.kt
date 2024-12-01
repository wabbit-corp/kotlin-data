package one.wabbit.data

import kotlin.test.Test

class LazyListTest {
    @Test fun test() {
        var total = 0L
        for (i in 0 until 10000 step 500) {
            System.gc()
            Thread.yield()
            System.gc()

            val t0 = System.nanoTime() / 1_000_000.0
            for (j in 1..100) {
                val list = LazyList.recursive<Int> { list ->
                    LazyList.Cons(1) { list.map { it + 1 } }
                }
                val x = list[i]
                total += x
            }
            val t1 = System.nanoTime() / 1_000_000.0

            System.gc()
            Thread.yield()
            System.gc()

            println("$i, ${(t1 - t0) / 100.0}")
        }

        println(total)
    }
}
