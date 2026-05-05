// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

/** Return an empty iterator. */
fun iteratorOf(): Iterator<Nothing> =
    object : Iterator<Nothing> {
        override fun hasNext(): Boolean = false

        override fun next(): Nothing = throw NoSuchElementException()
    }

/** Return an iterator over one value. */
fun <V> iteratorOf(v: V): Iterator<V> {
    return object : Iterator<V> {
        var done = false

        override fun hasNext(): Boolean = !done

        override fun next(): V {
            if (done) throw NoSuchElementException()
            done = true
            return v
        }
    }
}

/** Return an iterator over two values in argument order. */
fun <V> iteratorOf(v1: V, v2: V): Iterator<V> {
    return object : Iterator<V> {
        var index = 0

        override fun hasNext(): Boolean = index < 2

        override fun next(): V {
            if (index >= 2) throw NoSuchElementException()
            return when (index++) {
                0 -> v1
                1 -> v2
                else -> throw IllegalStateException()
            }
        }
    }
}

/** Return an iterator over three values in argument order. */
fun <V> iteratorOf(v1: V, v2: V, v3: V): Iterator<V> {
    return object : Iterator<V> {
        var index = 0

        override fun hasNext(): Boolean = index < 3

        override fun next(): V {
            if (index >= 3) throw NoSuchElementException()
            return when (index++) {
                0 -> v1
                1 -> v2
                2 -> v3
                else -> throw IllegalStateException()
            }
        }
    }
}

/** Return an iterator over four values in argument order. */
fun <V> iteratorOf(v1: V, v2: V, v3: V, v4: V): Iterator<V> {
    return object : Iterator<V> {
        var index = 0

        override fun hasNext(): Boolean = index < 4

        override fun next(): V {
            if (index >= 4) throw NoSuchElementException()
            return when (index++) {
                0 -> v1
                1 -> v2
                2 -> v3
                3 -> v4
                else -> throw IllegalStateException()
            }
        }
    }
}

/** Return a lazy iterator that yields only values accepted by [f]. */
fun <A> Iterator<A>.filter(f: (A) -> Boolean): Iterator<A> {
    val self = this
    return object : Iterator<A> {
        var haveValue: Boolean = false
        var value: A? = null

        override fun hasNext(): Boolean {
            if (haveValue) return true
            while (self.hasNext()) {
                val value = self.next()
                if (f(value)) {
                    this.value = value
                    this.haveValue = true
                    return true
                }
            }
            return false
        }

        override fun next(): A {
            if (haveValue) {
                val value = this.value
                this.value = null
                this.haveValue = false
                // Why not assert that value != null?
                // Well... it COULD be legitimately null since A is potentially nullable
                @Suppress("UNCHECKED_CAST")
                return value as A
            } else {
                while (true) {
                    val value = self.next()
                    if (f(value)) return value
                }
            }
        }
    }
}

/**
 * Return a lazy iterator that transforms values with [f].
 *
 * If [f] throws a non-fatal exception, the source value is retried on the next [Iterator.next]
 * call.
 */
fun <T, U> Iterator<T>.map(f: (T) -> U): Iterator<U> {
    val self = this
    return object : Iterator<U> {
        var haveValue: Boolean = false
        var value: T? = null

        override fun hasNext(): Boolean = haveValue || self.hasNext()

        override fun next(): U {
            if (haveValue) {
                val value = this.value
                // Why not assert that value != null?
                // Well... it COULD be legitimately null since A is potentially nullable
                val result = @Suppress("UNCHECKED_CAST") f(value as T)
                this.value = null
                this.haveValue = false
                return result
            } else {
                val value = self.next()
                return try {
                    f(value)
                } catch (e: Throwable) {
                    if (e is Error) throw e
                    haveValue = true
                    this.value = value
                    throw e
                }
            }
        }
    }
}

/**
 * Return a lazy iterator that expands each source value into an iterator from [f].
 *
 * If [f] throws a non-fatal exception, the source value is retried on the next [Iterator.next]
 * call.
 */
fun <T, U> Iterator<T>.flatMap(f: (T) -> Iterator<U>): Iterator<U> {
    val upstream = this
    return object : Iterator<U> {
        private var current: Iterator<U>? = null
        private var retry = false
        private var pending: T? = null

        // Ensure `current` points to a non-empty inner iterator, if one exists.
        private fun advance(): Boolean {
            while (true) {
                // If current inner has data, we're good.
                current?.let { if (it.hasNext()) return true }

                // Drop exhausted inner.
                current = null

                // Choose the next T to expand.
                val t: T =
                    if (retry) {
                        @Suppress("UNCHECKED_CAST")
                        pending as T
                    } else {
                        if (!upstream.hasNext()) return false
                        upstream.next()
                    }

                // Try to create the new inner iterator.
                val inner =
                    try {
                        f(t)
                    } catch (e: Throwable) {
                        // Don’t mutate iterator state on fatal errors.
                        if (e is Error) throw e
                        retry = true
                        pending = t
                        throw e
                    }

                // Mapping succeeded; clear retry state.
                retry = false
                pending = null

                // If inner is empty, loop again; else set it and report readiness.
                if (inner.hasNext()) {
                    current = inner
                    return true
                }
                // else loop to fetch next upstream T
            }
        }

        override fun hasNext(): Boolean = advance()

        override fun next(): U {
            if (!advance()) throw NoSuchElementException()
            return current!!.next()
        }
    }
}

/** Zip this iterator with [other] until either iterator is exhausted. */
fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> {
    val self = this
    return object : Iterator<Pair<A, B>> {
        override fun hasNext(): Boolean = self.hasNext() && other.hasNext()

        override fun next(): Pair<A, B> = Pair(self.next(), other.next())
    }
}
