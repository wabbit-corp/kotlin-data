package one.wabbit.data

fun iteratorOf(): Iterator<Nothing> {
    return object : Iterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun next(): Nothing = throw NoSuchElementException()
    }
}

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

fun <T, U> Iterator<T>.map(f: (T) -> U): Iterator<U> {
    val self = this
    return object: Iterator<U> {
        var haveValue: Boolean = false
        var value: T? = null

        override fun hasNext(): Boolean {
            return haveValue || self.hasNext()
        }
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
                    if (e is VirtualMachineError) throw e
                    haveValue = true
                    this.value = value
                    throw e
                }
            }
        }
    }
}

fun <T, U> Iterator<T>.flatMap(f: (T) -> Iterator<U>): Iterator<U> {
    val self = this
    return object: Iterator<U> {
        var haveValue: Boolean = false
        var value: T? = null
        var it: Iterator<U>? = null

        override fun hasNext(): Boolean {
            val it = this.it
            if (it != null) return it.hasNext()
            else {
                if (haveValue) {
                    val value = this.value
                    val it = f(value as T)
                    this.it = it
                    this.value = null
                    this.haveValue = false
                    return it.hasNext()
                } else {
                    val value = self.next()
                    val it = try {
                        f(value)
                    } catch (e: Throwable) {
                        if (e is VirtualMachineError) throw e
                        this.haveValue = true
                        this.value = value
                        throw e
                    }
                    this.it = it
                    this.value = null
                    return it.hasNext()
                }
            }
        }
        override fun next(): U {
            val it = this.it
            if (it != null) return it.next()
            else {
                if (haveValue) {
                    val value = this.value
                    val it = f(value as T)
                    this.it = it
                    this.value = null
                    this.haveValue = false
                    return it.next()
                } else {
                    val value = self.next()
                    val it = try {
                        f(value)
                    } catch (e: Throwable) {
                        if (e is VirtualMachineError) throw e
                        this.haveValue = true
                        this.value = value
                        throw e
                    }
                    this.it = it
                    this.value = null
                    return it.next()
                }
            }
        }
    }
}

fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> {
    val self = this
    return object : Iterator<Pair<A, B>> {
        override fun hasNext(): Boolean = self.hasNext() && other.hasNext()
        override fun next(): Pair<A, B> = Pair(self.next(), other.next())
    }
}
