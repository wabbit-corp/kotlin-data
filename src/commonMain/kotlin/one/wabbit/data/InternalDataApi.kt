package one.wabbit.data

@Retention(AnnotationRetention.RUNTIME)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API exposes internal representation details and invariants.",
)
annotation class InternalDataApi
