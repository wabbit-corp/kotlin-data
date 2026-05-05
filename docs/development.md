# Development

## Build

From the workspace root:

```bash
./dev build kotlin-data
```

From the repository root:

```bash
./gradlew build
```

## Documentation

```bash
./gradlew dokkaGeneratePublicationHtml
./dev verify docs kotlin-data
```

Keep README examples, docs pages, and KDoc aligned. Public functions, properties, constants, and
factory helpers should describe mutation behavior, ownership/copying behavior, nullability, and
exception contracts where those details are not obvious from the signature.

## Generated Primitive Types

Primitive buffer and deque files are generated from the `FloatBuffer.kt` and `FloatDeque.kt`
templates by `generate.py`. When changing shared behavior or KDoc in generated primitive types,
update the source template and regenerate the other primitive variants instead of hand-editing only
one generated output.

## Publication Check

```bash
./dev publish --dry-run kotlin-data
```

The dry-run should list `kotlin-data` and its local dependency publications without mutating remote
repositories.
