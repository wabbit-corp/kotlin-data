# User Guide

## Immutable Collections

Use `Arr` when you need a compact immutable sequence with predictable array-backed access:

```kotlin
import one.wabbit.data.arrOf

val names = arrOf("ada", "grace", "katherine")
val displayNames = names.map { it.replaceFirstChar(Char::uppercase) }

check(displayNames.toList() == listOf("Ada", "Grace", "Katherine"))
```

Use `ArrMap` for small immutable maps where a compact array-backed representation is preferable to
hash-table overhead:

```kotlin
import one.wabbit.data.ArrMap

val ports = ArrMap.empty<String, Int>()
    .put("http", 80)
    .put("https", 443)

check(ports["https"] == 443)
```

## Mutable Primitive Buffers

Primitive buffers avoid boxing and mutate in place:

```kotlin
import one.wabbit.data.DoubleBuffer

val samples = DoubleBuffer.of(1.0, 2.0, 3.0)
samples.add(4.0)
samples.sortDescending()

check(samples.first() == 4.0)
```

Use `copy()`, `toList()`, or the primitive array conversion when handing values to code that should
not observe later mutations.

## Primitive Deques

Primitive deques are ring buffers optimized for both-end insertion and removal:

```kotlin
import one.wabbit.data.IntDeque

val deque = IntDeque.of(2, 3)
deque.pushFirst(1)
deque.pushLast(4)

check(deque.popFirst() == 1)
check(deque.popLast() == 4)
```

## Functional Result Types

`Option` represents presence or absence without nullable API ambiguity, `Either` represents one of
two typed outcomes, and `Validated` accumulates issues while still allowing successful values to
carry warnings.

```kotlin
import one.wabbit.data.Validated

fun positive(value: Int): Validated<String, Int> =
    if (value > 0) Validated.succeed(value) else Validated.fail(listOf("must be positive"))

val rounded = Validated.succeed(10, listOf("rounded to nearest whole number"))

check(positive(1).issues.isEmpty())
check(rounded.issues == listOf("rounded to nearest whole number"))
```

## Persistent Structures

`BankersQueue` and `LeftistHeap` return new values from updates and keep earlier values usable:

```kotlin
import one.wabbit.data.BankersQueue

val empty = BankersQueue.empty<Int>()
val queued = empty.enqueue(1).enqueue(2)

check(empty.isEmpty())
check(queued.peek() == 1)
check(queued.dequeueOrNull()?.second?.peekOrNull() == 2)
```

## Serialization

Several public structures provide `kotlinx.serialization` serializers. Add
`kotlinx-serialization-json` or another serialization format in application code when encoded values
need to cross process or network boundaries.
