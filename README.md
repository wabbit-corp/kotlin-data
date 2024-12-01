# kotlin-data

The Kotlin Data project, "kotlin-data", provides a collection of data structures and utilities for Kotlin. It focuses on performance and functional programming.

## Overview

Kotlin Data includes various data structures like enhanced arrays, maps, lazy lists, and persistent queues. It also offers functional types like `Either` and `Option`, and utility functions for string manipulation, randomization, and set operations. The project uses code generation for consistency and to reduce redundancy.

## Installation

Add the following dependency to your project:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.wabbit-corp:kotlin-data:1.0.0")
}
```

## Usage

### 1. `Arr.kt`
**Purpose**:
- A generic data class `Arr<T>` providing functionalities similar to a list with additional methods like `map`, `mapOrNull`, `all`, `any`, and more.
- Includes serialization support.

**Usage Example**:
```kotlin
val arr = Arr.of(1, 2, 3)
val newArr = arr.map { it * 2 }
println(newArr.toList())  // Output: [2, 4, 6]

if (arr.any { it > 2 }) {
    println("Contains elements greater than 2")
}
```

### 2. `ArrMap.kt`
**Purpose**:
- A map-like structure implemented with an array, supporting basic operations like `get`, `put`, and `contains`.

**Usage Example**:
```kotlin
val map = ArrMap.empty<String, Int>()
val updatedMap = map.put("key1", 10)
println(updatedMap["key1"])  // Output: 10

if ("key1" in updatedMap) {
    println("Contains key1")
}
```

### 3. `BankersQueue.kt`
**Purpose**:
- An implementation of a persistent queue based on the Banker's queue algorithm.

**Usage Example**:
```kotlin
val queue = BankersQueue.fromConsList(consListOf(1, 2, 3))
val (element, newQueue) = queue.uncons().value!!
println(element)  // Output: 1
```

### 4. `Buf.kt` and Its Variants
**Purpose**:
- Buffers like `BooleanBuf`, `ByteBuf`, etc., provide a resizable array implementation with functionalities to push, pop, and manage capacity.

**Usage Example**:
```kotlin
val intBuf = IntBuf()
intBuf.pushLast(5)
intBuf.pushLast(10)
println(intBuf.toList())  // Output: [5, 10]
```

### 5. `Chain.kt`
**Purpose**:
- Represents a lazy concatenation of elements which can be efficiently concatenated and converted to arrays or lists.

**Usage Example**:
```kotlin
val chain = Chain.of(1, 2) + Chain.of(3, 4)
println(chain.toList())  // Output: [1, 2, 3, 4]
```

### 6. `ConsList.kt`
**Purpose**:
- A persistent linked list implementation with various utility methods.

**Usage Example**:
```kotlin
val list = consListOf(1, 2, 3)
val newList = list.cons(0)
println(newList.toList())  // Output: [0, 1, 2, 3]
```

### 7. `Either.kt`
**Purpose**:
- A representation of a value that can be one of two possible types, `Left` or `Right`, often used for error handling.

**Usage Example**:
```kotlin
val result: Either<String, Int> = Right(42)
val mapped = result.map { it + 1 }
println(mapped)  // Output: Right(value=43)
```

### 8. `Option.kt`
**Purpose**:
- Represents an optional value, encapsulating presence or absence of a value.

**Usage Example**:
```kotlin
val someValue: Option<Int> = Some(10)
val noneValue: Option<Int> = None
println(someValue.map { it * 2 })  // Output: Some(value=20)
```

### 9. `LeftistHeap.kt`
**Purpose**:
- Implements a leftist heap, a type of priority queue.

**Usage Example**:
```kotlin
val heap = LeftistHeap.of(3, 1, 4, 1, 5, 9)
println(heap.findMin())  // Output: 1
```

### 10. `Validated.kt`
**Purpose**:
- Represents a value that may be valid or invalid, accumulating issues.

**Usage Example**:
```kotlin
val success = Validated.succeed("Valid")
val failure = Validated.fail(listOf("Error1", "Error2"))
println(success.map { it.uppercase() })  // Output: Success(value=VALID, issues=[])
```

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, please contact Wabbit Consulting Corporation (at wabbit@wabbit.one) for licensing terms.

## Contributing

Before we can accept your contributions, we kindly ask you to agree to our Contributor License Agreement (CLA).
