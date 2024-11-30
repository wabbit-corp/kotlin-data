package one.wabbit.data

import kotlinx.serialization.Serializable

@Serializable sealed interface Union2<out A, out B> {
    @Serializable data class U1<out A>(val value: A) : Union2<A, Nothing>
    @Serializable data class U2<out B>(val value: B) : Union2<Nothing, B>

    fun <C> map1(f: (A) -> C): Union2<C, B> = when (this) {
        is U1 -> U1(f(value))
        is U2 -> U2(value)
    }
    fun <C> map2(f: (B) -> C): Union2<A, C> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(f(value))
    }

    fun firstOrNone(): Option<A> = when (this) {
        is U1 -> Some(value)
        is U2 -> None
    }

    fun firstOrNull(): A? = when (this) {
        is U1 -> value
        is U2 -> null
    }

    fun secondOrNone(): Option<B> = when (this) {
        is U1 -> None
        is U2 -> Some(value)
    }

    fun secondOrNull(): B? = when (this) {
        is U1 -> null
        is U2 -> value
    }
}

@Serializable sealed interface Union3<out A, out B, out C> {
    @Serializable data class U1<out A>(val value: A) : Union3<A, Nothing, Nothing>
    @Serializable data class U2<out B>(val value: B) : Union3<Nothing, B, Nothing>
    @Serializable data class U3<out C>(val value: C) : Union3<Nothing, Nothing, C>

    fun <D> map1(f: (A) -> D): Union3<D, B, C> = when (this) {
        is U1 -> U1(f(value))
        is U2 -> U2(value)
        is U3 -> U3(value)
    }

    fun <D> map2(f: (B) -> D): Union3<A, D, C> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(f(value))
        is U3 -> U3(value)
    }

    fun <D> map3(f: (C) -> D): Union3<A, B, D> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(value)
        is U3 -> U3(f(value))
    }
}

@Serializable sealed interface Union4<out A, out B, out C, out D> {
    @Serializable data class U1<out A>(val value: A) : Union4<A, Nothing, Nothing, Nothing>
    @Serializable data class U2<out B>(val value: B) : Union4<Nothing, B, Nothing, Nothing>
    @Serializable data class U3<out C>(val value: C) : Union4<Nothing, Nothing, C, Nothing>
    @Serializable data class U4<out D>(val value: D) : Union4<Nothing, Nothing, Nothing, D>

    fun <E> map1(f: (A) -> E): Union4<E, B, C, D> = when (this) {
        is U1 -> U1(f(value))
        is U2 -> U2(value)
        is U3 -> U3(value)
        is U4 -> U4(value)
    }

    fun <E> map2(f: (B) -> E): Union4<A, E, C, D> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(f(value))
        is U3 -> U3(value)
        is U4 -> U4(value)
    }

    fun <E> map3(f: (C) -> E): Union4<A, B, E, D> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(value)
        is U3 -> U3(f(value))
        is U4 -> U4(value)
    }

    fun <E> map4(f: (D) -> E): Union4<A, B, C, E> = when (this) {
        is U1 -> U1(value)
        is U2 -> U2(value)
        is U3 -> U3(value)
        is U4 -> U4(f(value))
    }
}
